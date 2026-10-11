plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
tasks.withType<Test>().configureEach { useJUnitPlatform(); testLogging { events("passed", "failed", "skipped") } }

dependencies {
testImplementation(project(":cart-api"))
testImplementation(project(":cart-impl"))
testImplementation(project(":feature-saved"))
testImplementation(libs.coroutinesTest)
testImplementation(libs.junitJupiter)
testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.withType<Test>().configureEach {
    systemProperty("cart.provider", providers.gradleProperty("cartProvider").orElse("fixed").get())
}
