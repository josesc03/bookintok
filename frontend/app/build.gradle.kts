import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Machine-specific values (server URLs, Firebase config) live in local.properties, which is not committed
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun configValue(name: String, default: String = ""): String =
    "\"${localProperties.getProperty(name) ?: System.getenv(name) ?: default}\""

android {
    namespace = "com.bookintok.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.bookintok.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        // 10.0.2.2 is the host machine as seen from the Android emulator
        buildConfigField("String", "API_BASE_URL", configValue("bookintok.apiBaseUrl", "http://10.0.2.2:8080"))
        buildConfigField("String", "CHAT_BASE_URL", configValue("bookintok.chatBaseUrl", "ws://10.0.2.2:8081"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", configValue("firebase.projectId"))
        buildConfigField("String", "FIREBASE_APP_ID", configValue("firebase.appId"))
        buildConfigField("String", "FIREBASE_API_KEY", configValue("firebase.apiKey"))
        buildConfigField("String", "FIREBASE_SENDER_ID", configValue("firebase.senderId"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configValue("firebase.webClientId"))
        // Shown in the privacy policy: who is responsible for the data and how to contact them
        buildConfigField("String", "LEGAL_OWNER", configValue("legal.owner"))
        buildConfigField("String", "LEGAL_EMAIL", configValue("legal.email"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    // Exported schemas are committed, so database migrations can be reviewed and tested
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.osmdroid)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}
