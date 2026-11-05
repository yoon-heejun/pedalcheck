plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.pedalcheck.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.pedalcheck.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
        resValue("string", "app_name", "페달체크 시연")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            applicationIdSuffix = ".overlaydemo"
            resValue("string", "app_name", "페달체크 · 시연")
        }
        release { isMinifyEnabled = false }
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
