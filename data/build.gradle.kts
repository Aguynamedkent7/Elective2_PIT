plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.numlab.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":domain"))
    // Room + DataStore arrive in Phase 5 (history) / Phase 7 (settings).
}
