plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "org.monagora.app.gpscitoyen"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.monagora.app.gpscitoyen"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
        buildConfig = true // pour BuildConfig.DEBUG, cf. MonAgoraApplication
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:identity"))
    implementation(project(":core:objects"))
    implementation(project(":core:storage"))
    implementation(project(":core:storage-android"))
    implementation(project(":core:sync"))
    implementation(project(":core:sync-android"))
    implementation(project(":core:logging-android"))
    // Déclaré directement : aucun module core:* ne l'expose en api (toujours
    // implementation), donc rien ne le rend transitivement visible ici sans ça —
    // ce module l'utilise directement (LoggerFactory.getLogger dans MainActivity.kt).
    implementation("org.slf4j:slf4j-api:2.0.13")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
