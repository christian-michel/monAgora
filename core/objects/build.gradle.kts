plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:identity"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
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
