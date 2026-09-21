plugins {
    kotlin("jvm")
    // Génère le code de (dé)sérialisation pour les data class @Serializable
    // (SignedObject, les payloads identity/wastereport...) — requis en plus
    // de la bibliothèque kotlinx-serialization-json ci-dessous, qui ne fournit
    // que le runtime, pas le plugin de compilation.
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api, pas implementation : GuestSession (core:identity) apparaît dans les
    // signatures publiques de ce module (SignedObjects.createAsGuest,
    // WasteReports.createAsGuest) — même règle que kotlinx-serialization-json
    // ci-dessous (docs/architecture.md, section 3). Le socle cryptographique sur
    // lequel ce module construit le format d'objet signé (encodage b64/b64u,
    // signature/vérification Ed25519, session invité).
    api(project(":core:identity"))
    // api, pas implementation : SignedObject.payload expose JsonObject dans l'API
    // publique de ce module — les modules qui en dépendent (core:trust, core:sync)
    // ont besoin de ce type sur leur propre classpath de compilation.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Canonicalisation JSON RFC 8785 (JCS) : on délègue à une bibliothèque dédiée
    // plutôt que de réimplémenter les règles de tri de clés et de formatage des
    // nombres nous-mêmes (constitution-technique.md, principe 11 : réutilisation
    // avant réinvention — d'autant plus critique ici qu'une erreur de canonicalisation
    // casserait silencieusement toutes les signatures).
    implementation("io.github.erdtman:java-json-canonicalization:1.1")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
