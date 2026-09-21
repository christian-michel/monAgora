// Fichier d'entrée de la build Gradle multi-modules : dépôts binaires et
// liste des modules qui composent le projet (cf. docs/architecture.md,
// section 2, pour le rôle de chacun et leurs dépendances).

pluginManagement {
    // Dépôts où résoudre les *plugins* Gradle (avant même d'évaluer les
    // build.gradle.kts des modules). google() est nécessaire pour l'Android
    // Gradle Plugin et le plugin Kotlin Compose ; sans accès réseau à
    // dl.google.com (via google()), rien ne se résout — cf. CLAUDE.md,
    // section "Environnement de build Android".
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Dépôts où résoudre les *dépendances* (artefacts AndroidX, Compose,
    // SLF4J, etc.) déclarées dans les blocs `dependencies { ... }` de chaque
    // module.
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "monagora"

// Un module par ligne, chemin Gradle (`:core:xxx`, `:app:xxx`) reflétant le
// chemin sur le disque (`core/xxx`, `app/xxx`). Toute création/suppression de
// module doit être répercutée ici et dans docs/architecture.md, dans le même
// commit (cf. CLAUDE.md, section "Manière de travailler").
include(":core:identity")
include(":core:logging-android")
include(":core:objects")
include(":core:storage")
include(":core:storage-android")
include(":core:sync")
include(":core:sync-android")
include(":core:trust")
include(":app:gps-citoyen")
