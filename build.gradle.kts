// Build racine du projet : ne fait qu'enregistrer, une seule fois pour tout
// le dépôt, les plugins et leurs versions utilisés par au moins un module.
// `apply false` : le plugin est résolu et sa version fixée ici, mais pas
// appliqué au projet racine lui-même — chaque module (core/*, app/*)
// l'applique explicitement dans son propre build.gradle.kts (cf.
// docs/architecture.md, carte des modules) selon ce dont il a besoin :
// - kotlin("jvm") : modules Kotlin/JVM purs (core/identity, core/objects,
//   core/storage, core/sync, core/trust) — aucune API android.*.
// - kotlin("android") + com.android.application / com.android.library :
//   modules Android réels (les variantes *-android, core/logging-android,
//   app/gps-citoyen) — nécessitent le SDK Android (cf. CLAUDE.md, section
//   "Environnement de build Android").
// - kotlin("plugin.serialization") : génère les (dé)sérialiseurs
//   kotlinx.serialization là où le format d'objet signé en a besoin
//   (JsonObject des payloads, cf. core/objects).
// - kotlin("plugin.compose") : compilateur Compose (remplace, depuis
//   Kotlin 2.0/K2, l'ancien mécanisme de version séparée de l'extension
//   Compose) — seul app/gps-citoyen en a besoin (seul module avec une UI).
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
}
