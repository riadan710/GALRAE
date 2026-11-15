import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    useLibrary("android.car")
    namespace = "com.galrae.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.galrae.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val kakaoKey = localConfig.getProperty("kakao.restApiKey", "")
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // release 서명 키는 git 밖에서 공유한다 (local.properties의 release.* 값, local.properties.example 참고).
    // 키가 없는 PC에서는 서명 없이 빌드된다
    val releaseStoreFile = localConfig.getProperty("release.storeFile")?.let { rootProject.file(it) }
    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localConfig.getProperty("release.storePassword")
                keyAlias = localConfig.getProperty("release.keyAlias")
                keyPassword = localConfig.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.tts)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
