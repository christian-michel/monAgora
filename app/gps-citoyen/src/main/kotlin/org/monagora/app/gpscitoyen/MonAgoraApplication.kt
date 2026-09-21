package org.monagora.app.gpscitoyen

import android.app.Application
import org.monagora.core.logging.android.AndroidLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * Configure le « mode debug » du logging avant tout le reste : `DEBUG` en
 * build debug (tout devient visible dans `adb logcat`, y compris les
 * événements habituellement en DEBUG — dédoublonnage d'objet, curseur mis à
 * jour, etc., cf. CLAUDE.md section Journalisation), `INFO` en release pour
 * ne pas noyer logcat en usage normal.
 *
 * Installe aussi un gestionnaire d'exceptions non rattrapées : une erreur
 * vraiment inattendue (pas prévue par un `catch` existant) doit rester
 * visible dans les logs structurés, avec la pile complète, avant que le
 * plantage ne se produise — jamais un crash muet sans trace exploitable.
 * Le comportement de plantage lui-même n'est pas changé (le gestionnaire
 * précédent, celui d'Android, est toujours appelé ensuite) : on veut que
 * l'appli continue de planter fort sur une vraie erreur, juste avec une
 * trace utilisable dans les logs avant que ça arrive.
 */
class MonAgoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AndroidLogging.minimumLevel = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO

        val logger = LoggerFactory.getLogger(MonAgoraApplication::class.java)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error("uncaught_exception thread={} reason=\"{}\"", thread.name, throwable.message, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        logger.info("app_started debug_mode={}", BuildConfig.DEBUG)
    }
}
