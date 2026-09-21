package org.monagora.core.logging.android

import org.slf4j.event.Level

/**
 * Point de configuration du binding SLF4J -> `android.util.Log`
 * ([AndroidServiceProvider]). Le « mode debug » de l'application consiste,
 * concrètement, à mettre [minimumLevel] à `Level.DEBUG` au démarrage
 * (typiquement piloté par `BuildConfig.DEBUG`, voir `MonAgoraApplication`
 * dans app/gps-citoyen) : tous les logs déjà écrits dans le projet — y
 * compris les DEBUG habituellement invisibles (dédoublonnage d'objet,
 * curseur mis à jour...) — deviennent alors visibles dans `adb logcat`.
 * `INFO` par défaut, pour ne pas noyer logcat en usage normal (build release).
 */
object AndroidLogging {
    @Volatile
    var minimumLevel: Level = Level.INFO
}
