import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Google's published AdMob sample IDs: they only ever serve test ads (docs/RELEASE.md).
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerId = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "app.numlab"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.numlab"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("numlab.versionCode").get().toInt()
        versionName = providers.gradleProperty("numlab.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] = admobTestAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBannerId\"")
    }

    // Upload key lives outside the repo; keystore.properties is git-ignored (docs/RELEASE.md).
    val ks = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    signingConfigs {
        if (ks != null) {
            create("upload") {
                storeFile = file(ks.getProperty("storeFile"))
                storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias")
                keyPassword = ks.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real IDs apply to release builds only; debug builds always stay on test ads.
            manifestPlaceholders["admobAppId"] = providers.gradleProperty("numlab.admob.appId").getOrElse(admobTestAppId)
            buildConfigField("String", "ADMOB_BANNER_ID",
                "\"${providers.gradleProperty("numlab.admob.bannerId").getOrElse(admobTestBannerId)}\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    ksp(libs.hilt.compiler)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
