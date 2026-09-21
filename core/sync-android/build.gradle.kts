// Build du module core/sync-android : implémentation Android du transport de
// synchronisation local (Bluetooth RFCOMM) au-dessus de core:sync, plus le
// curseur SQLite via android.database.sqlite — cf. docs/architecture.md,
// section 2, ligne core/sync-android, et section 4 (motif JDBC ↔ Android :
// même contrat que core:sync, remplacement mécanique de son implémentation
// JVM desktop). Module Android (pas Kotlin/JVM pur) car il utilise
// android.bluetooth.* et android.database.sqlite.*.
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "org.monagora.core.sync.android"
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
    // api, pas implementation : même raisonnement que core:storage-android —
    // SyncSession et les types de core:sync apparaissent dans nos signatures publiques.
    api(project(":core:sync"))
    // api, pas implementation : SignedObjectStore (core:storage) apparaît dans
    // les constructeurs publics de BluetoothSyncClient/BluetoothSyncServer —
    // même règle que core/sync/build.gradle.kts.
    api(project(":core:storage"))
    // Nécessaire pour référencer SyncMessage/SyncResponse (sérialisables) via
    // core:sync ; ce module ne (dé)sérialise rien lui-même directement.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Façade de logging ; le binding réel (vers android.util.Log) est fourni
    // par core:logging-android côté application, jamais par ce module.
    implementation("org.slf4j:slf4j-api:2.0.13")
}
