plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
tasks.withType<Test>().configureEach { useJUnitPlatform(); testLogging { events("passed", "failed", "skipped") } }

dependencies {

testImplementation(libs.junitJupiter)
testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named<Test>("test") { exclude("**/UploadQueueExerciseTest.class") }
tasks.register<Test>("exerciseTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/UploadQueueExerciseTest.class")
    outputs.upToDateWhen { false }
}
