// Module core/logging-android : binding SLF4J -> android.util.Log (cf.
// docs/architecture.md, section 2, ligne core/logging-android). Indépendant
// de tout autre module core/* (voir le graphe de dépendances, section 3) :
// il ne connaît ni objets signés, ni stockage, ni synchronisation — un
// binding de logging générique, utilisable par n'importe quelle future
// application Android du projet. Bibliothèque Android (pas une app) : pas
// d'APK produit, vérifier la compilation via
// `./gradlew :core:logging-android:compileDebugKotlin`.
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "org.monagora.core.logging.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
    // Seule dépendance : l'API SLF4J que ce module implémente (AndroidLogger,
    // AndroidLoggerFactory, AndroidServiceProvider). android.util.Log fait
    // partie du SDK Android fourni par le plugin com.android.library
    // ci-dessus, pas besoin de le déclarer séparément.
    implementation("org.slf4j:slf4j-api:2.0.13")
}
