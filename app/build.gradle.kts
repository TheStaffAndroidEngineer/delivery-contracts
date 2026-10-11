plugins { alias(libs.plugins.androidApplication) }
android {
    namespace = "staff.android.delivery"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "staff.android.delivery"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testProguardFile("test-proguard-rules.pro")
        val provider = providers.gradleProperty("cartProvider").orElse("fixed").get()
        require(provider in setOf("fixed", "broken"))
        buildConfigField("boolean", "BROKEN_PROVIDER", (provider == "broken").toString())
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testBuildType = providers.gradleProperty("testedBuildType").orElse("debug").get()
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
dependencies {
    implementation(project(":cart-api"))
    implementation(project(":cart-impl"))
    implementation(project(":feature-saved"))
    implementation(libs.coroutinesAndroid)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.espressoCore)
}
