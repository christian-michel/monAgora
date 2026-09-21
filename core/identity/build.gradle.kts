plugins {
    kotlin("jvm")
}

kotlin {
    // 21 : seul JDK disponible dans cet environnement de build ; à réévaluer
    // une fois l'Android Gradle Plugin introduit (généralement JDK 17).
    jvmToolchain(21)
}

dependencies {
    // Ed25519 via l'API "lightweight" de BouncyCastle (pas le provider JCA) :
    // c'est le choix portable sur Android, où le provider crypto système n'a
    // pas toujours supporté Ed25519 nativement (cf. constitution-technique.md, section 9).
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
