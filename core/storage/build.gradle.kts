// Module core/storage : persistance locale des objets signés (SignedObjectStore)
// et du quota mode invité (GuestQuota), variante Kotlin/JVM pur — voir
// docs/architecture.md, section 4 ("motif JDBC ↔ Android") pour la place de
// ce module par rapport à sa variante core:storage-android, et section 2
// (carte des modules) pour le graphe de dépendances complet.

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api, pas implementation : SignedObject (et donc JsonObject) apparaît dans
    // les signatures publiques de SignedObjectStore (save/findById/query) — même
    // raisonnement que dans core:objects/build.gradle.kts.
    api(project(":core:objects"))
    // Sérialisation du payload JSON stocké/relu en base (colonne `payload` en
    // texte) — même bibliothèque que core:objects, pas de plugin.serialization
    // ici car aucune classe @Serializable n'est déclarée dans ce module (on ne
    // fait que parser/sérialiser des JsonObject déjà typés en amont).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Driver JDBC portable (Linux/Mac/Windows desktop) : voir la note dans
    // SqliteSignedObjectStore.kt sur ses limites côté Android.
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    // API SLF4J seule (pas de binding ici) : la journalisation est obligatoire
    // partout dans ce module (CLAUDE.md, section Journalisation) mais le choix
    // du binding (slf4j-simple en test, core:logging-android en prod Android)
    // revient à l'appelant final, pas à cette bibliothèque.
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":core:identity")) // pour Ed25519Keys dans les fixtures de test
    // Binding SLF4J minimal pour voir les logs (WARN/ERROR de rejet ou d'échec
    // SQLite) sur la sortie standard pendant les tests — jamais utilisé en prod.
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
