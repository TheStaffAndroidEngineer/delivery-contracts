import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile

buildscript { dependencies { classpath(libs.kotlinGradlePlugin) } }
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinJvm) apply false
}

abstract class VerifyDeliveryBoundaries : DefaultTask() {
    @get:Input abstract val edges: ListProperty<String>
    @get:Input abstract val modules: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty
    @TaskAction fun verify() {
        val known = setOf(":app", ":cart-api", ":cart-impl", ":feature-saved", ":contract-tests", ":test-lab")
        check(modules.get().toSet() == known) { "Module registry requires policy review: ${modules.get()}" }
        val actual = edges.get().sorted()
        report.get().asFile.apply { parentFile.mkdirs(); writeText(actual.joinToString("\n") + "\n") }
        val permitted = setOf(
            ":app|implementation|:cart-api",
            ":app|implementation|:cart-impl",
            ":app|implementation|:feature-saved",
            ":cart-impl|api|:cart-api",
            ":feature-saved|api|:cart-api",
        )
        check(actual.toSet() == permitted) {
            "Boundary violation: extra=${actual.toSet() - permitted}; " +
                "missing=${permitted - actual.toSet()}"
        }
        logger.lifecycle(
            "DELIVERY BOUNDARIES PASS: ${actual.size} " +
                "declared production edges"
        )
    }
}
val boundaryCheck = tasks.register<VerifyDeliveryBoundaries>("verifyBoundaries") {
    report.set(layout.buildDirectory.file("reports/module-edges.tsv"))
}
gradle.projectsEvaluated {
    val production = setOf(":app", ":cart-api", ":cart-impl", ":feature-saved")
    val actual = subprojects.filter { it.path in production }.flatMap { owner ->
        owner.configurations.filter { it.isCanBeDeclared }.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java)
                .filterNot {
                    it.path == owner.path &&
                        configuration.name.matches(Regex(
                            "(debug|release)(AndroidTest|UnitTest)" +
                                "(CompileClasspath|RuntimeClasspath)"
                        ))
                }.map {
                "${owner.path}|${configuration.name}|${it.path}"
            }
        }
    }
    boundaryCheck.configure { edges.set(actual); modules.set(subprojects.map { it.path }.sorted()) }
}
tasks.register("verifyDelivery") {
    dependsOn("verifyBoundaries", ":contract-tests:test", ":test-lab:test", ":app:lintDebug", ":app:assembleDebug")
}
