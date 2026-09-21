// Build du module core/sync : couche protocole de synchronisation (messages,
// codec JSON, curseur par pair, ingestion) — Kotlin/JVM pur, aucune API
// android.* (cf. docs/architecture.md, section 2, ligne core/sync). C'est
// délibérément un module JVM standard, pas Android, pour rester testable et
// développable sans SDK ni appareil (docs/architecture.md, section 4).
// L'implémentation liée à Android (Bluetooth, SQLite via android.database)
// vit dans le module frère core/sync-android.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api, pas implementation : SignedObject (core:objects) apparaît dans les
    // signatures publiques de ce module (SyncMessage.SyncResponse.objects,
    // etc.) — règle Gradle de docs/architecture.md, section 3.
    api(project(":core:objects"))
    // api, pas implementation : SignedObjectStore (core:storage) apparaît lui
    // aussi dans des signatures publiques (constructeur de SyncSession,
    // SyncIngest.ingest) — même règle de docs/architecture.md, section 3.
    api(project(":core:storage"))
    // Sérialisation JSON des SyncMessage (SyncMessageCodec) et des SignedObject
    // qu'ils transportent — cf. protocole-synchronisation.md, section 2.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Façade de logging (implémentation fournie par l'appelant : slf4j-simple
    // en test ici, core:logging-android sur un appareil réel) — cf. CLAUDE.md,
    // section Journalisation.
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":core:identity")) // pour Ed25519Keys dans les fixtures de test
    // Implémentation SLF4J minimale (sortie console) pour voir les logs pendant
    // les tests JVM ; jamais utilisée en dehors des tests (cf. core:logging-android
    // pour le binding réel sur Android).
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
