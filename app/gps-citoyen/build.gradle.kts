// Build de app/gps-citoyen : première application réelle du projet
// (docs/architecture.md, section 1). Module Android "application" (produit un
// APK), assemble tous les modules core/* (identité, objets, stockage, sync,
// logging) dont il a besoin ; lui-même ne contient aucune logique
// réutilisable par une autre application (cf. constitution-technique.md,
// section 3 : ce qui doit être partagé va en `core/*`, pas ici).
plugins {
    id("com.android.application")
    kotlin("android")
    // Compilateur Compose : nécessaire pour l'UI de l'écran
    // (GpsCitoyenScreen.kt).
    kotlin("plugin.compose")
    // Plugin de sérialisation kotlinx : ce module n'utilise directement
    // aucune classe @Serializable dans ses propres sources (les payloads
    // d'objets signés, eux @Serializable, vivent dans core/objects, déjà
    // compilé) — appliqué ici par cohérence avec les autres modules plutôt
    // que par nécessité stricte actuelle ; inoffensif, à retirer s'il reste
    // durablement inutile.
    kotlin("plugin.serialization")
}

android {
    namespace = "org.monagora.app.gpscitoyen"
    compileSdk = 34

    defaultConfig {
        // Identifiant unique de l'APK (Play Store / installation système) —
        // distinct du `namespace` ci-dessus par convention, ici identiques.
        applicationId = "org.monagora.app.gpscitoyen"
        // compileSdk/minSdk/targetSdk = 34/26/34 : choix par défaut du projet,
        // pas encore confronté à la version d'e/OS réellement installée sur
        // les appareils cibles (POCO F1) — cf. CLAUDE.md, section
        // "Environnement de build Android".
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
    // Toute la pile core/* dont app/gps-citoyen a besoin (docs/architecture.md,
    // section 2, ligne app/gps-citoyen : "tous les core/* ci-dessus") : identité,
    // format d'objet signé, stockage local (variante Android), synchronisation
    // (variante Android), logging. Les variantes JVM pures (core:storage,
    // core:sync) sont aussi déclarées directement, en plus des variantes
    // -android correspondantes, pour les interfaces qu'elles définissent
    // (SignedObjectStore, GuestQuota, SyncCursorStore — utilisées par leur type
    // d'interface dans MainActivity.kt).
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

    // Extensions Kotlin standard d'AndroidX (ex. accès simplifié aux
    // services système) — base habituelle de toute application AndroidX.
    implementation("androidx.core:core-ktx:1.13.1")
    // Intégration Compose <-> Activity : fournit ComponentActivity.setContent
    // utilisé dans MainActivity.onCreate.
    implementation("androidx.activity:activity-compose:1.9.2")
    // Platform BOM (Bill Of Materials) : fixe un jeu de versions cohérent
    // entre toutes les bibliothèques Compose ci-dessous (ui, material3,
    // ui-tooling*), sans avoir à répéter/accorder une version par artefact.
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    // Support des previews Compose dans l'IDE (annotation @Preview) ; le
    // moteur de preview lui-même (ui-tooling, plus lourd) n'est nécessaire
    // qu'en développement, d'où debugImplementation ci-dessous plutôt
    // qu'implementation — absent des builds release.
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
