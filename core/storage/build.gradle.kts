plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:objects"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Driver JDBC portable (Linux/Mac/Windows desktop) : voir la note dans
    // SqliteSignedObjectStore.kt sur ses limites côté Android.
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    testImplementation(project(":core:identity")) // pour Ed25519Keys dans les fixtures de test
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
