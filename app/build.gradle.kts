plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ir.shipaa.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.shipaa.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1-account-entry"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
