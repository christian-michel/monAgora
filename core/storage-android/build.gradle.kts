// Module core/storage-android : implémentation Android (android.database.sqlite)
// de SignedObjectStore/GuestQuota — la variante "-android" du motif décrit dans
// docs/architecture.md, section 4 ("Deux variantes, une seule interface : le
// motif JDBC ↔ Android"). Remplacement mécanique de core:storage côté appelant
// (même interfaces, même schéma SQL) — jamais un second protocole. Bibliothèque
// Android (pas d'app), donc pas de tâche `test` classique : la logique est déjà
// couverte par les tests JVM purs de core:storage ; vérifier la compilation via
// `./gradlew :core:storage-android:compileDebugKotlin`.

plugins {
    id("com.android.library")
    kotlin("android")
    // Autorise l'usage du runtime kotlinx.serialization (Json.parseToJsonElement
    // sur la colonne `payload`) — même besoin que core:storage, appliqué ici en
    // plus par cohérence avec les autres modules Android du projet (sync-android,
    // logging-android suivent le même plugin set Android+serialization).
    kotlin("plugin.serialization")
}

android {
    // Identifiant du module dans le manifeste fusionné final (pas de package
    // Java à part — les classes vivent déjà sous org.monagora.core.storage.android).
    namespace = "org.monagora.core.storage.android"
    // Version de SDK utilisée pour compiler ce module — voir CLAUDE.md, section
    // "Environnement de build Android", pour le choix (pas encore confronté aux
    // appareils cibles réels).
    compileSdk = 34

    defaultConfig {
        // Version Android minimale supportée (Android 8) — même choix que le
        // reste du projet, cf. CLAUDE.md.
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
    // api, pas implementation : même raisonnement que core:storage (SignedObject
    // apparaît dans les signatures publiques de SignedObjectStore).
    api(project(":core:storage"))
    // Parsing/sérialisation du payload JSON stocké en base (même usage que
    // core:storage, driver différent).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // API SLF4J seule ; le binding réel vers android.util.Log vient de
    // core:logging-android, ajouté par l'application appelante (CLAUDE.md,
    // section Journalisation) — jamais slf4j-simple sur Android.
    implementation("org.slf4j:slf4j-api:2.0.13")
}
