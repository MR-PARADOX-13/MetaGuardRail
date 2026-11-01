import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val devBasetenKey = localProps.getProperty("baseten.apiKey", "")
val backendBaseUrl = localProps.getProperty("backend.baseUrl", "https://meta-guardrail-backend.example.com")

android {
    namespace = "com.example.metaguardrail"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.metaguardrail"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "DEV_BASETEN_API_KEY", "\"$devBasetenKey\"")
        buildConfigField("boolean", "IS_DEV_BACKEND", if (devBasetenKey.isNotBlank()) "true" else "false")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "MUSE_SPARK_MODEL", "\"meta-models/muse-spark-open-1026\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "MUSE_SPARK_MODEL", "\"meta-models/muse-spark-open-1026\"")
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
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
    }
}

tasks.register("checkReleaseNoApiKey") {
    doLast {
        if (devBasetenKey.isNotBlank()) {
            throw GradleException("Release build must not contain DEV_BASETEN_API_KEY. Clear baseten.apiKey from local.properties or set backend.baseUrl for prod.")
        }
    }
}
afterEvaluate {
    tasks.findByName("assembleRelease")?.dependsOn(tasks.named("checkReleaseNoApiKey"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.security.crypto)
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
