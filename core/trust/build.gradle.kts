plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:objects"))
    // api, pas implementation : le constructeur public de TrustResolver prend un
    // SignedObjectStore (core:storage) — même motif que kotlinx-serialization-json
    // dans core/objects et core/storage (docs/architecture.md, section 3). Encore
    // latent (rien n'instancie TrustResolver depuis un autre module pour l'instant)
    // mais deviendrait une erreur de compilation dès qu'un module appelant (ex.
    // app/gps-citoyen) essaierait de construire un TrustResolver.
    api(project(":core:storage"))
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":core:identity")) // pour Ed25519Keys dans les fixtures de test
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
