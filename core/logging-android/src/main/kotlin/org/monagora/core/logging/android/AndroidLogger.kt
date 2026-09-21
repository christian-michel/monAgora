package org.monagora.core.logging.android

import android.util.Log
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

/**
 * Implémentation [org.slf4j.Logger] qui écrit vers `android.util.Log`.
 * `LegacyAbstractLogger` gère déjà toutes les surcharges publiques
 * (trace/debug/info/warn/error, avec ou sans `Marker`, avec ou sans
 * `Throwable` en dernier argument) et les fait toutes converger vers
 * [handleNormalizedLoggingCall] — un seul point à implémenter.
 */
internal class AndroidLogger(loggerName: String) : LegacyAbstractLogger() {
    init {
        name = loggerName
    }

    // Pas de calcul de la pile d'appel : coûteux, et aucun binding "simple"
    // ne s'en sert réellement (ni slf4j-simple, ni les bindings historiques).
    override fun getFullyQualifiedCallerName(): String? = null

    override fun isTraceEnabled(): Boolean = isLevelEnabled(Level.TRACE)
    override fun isDebugEnabled(): Boolean = isLevelEnabled(Level.DEBUG)
    override fun isInfoEnabled(): Boolean = isLevelEnabled(Level.INFO)
    override fun isWarnEnabled(): Boolean = isLevelEnabled(Level.WARN)
    override fun isErrorEnabled(): Boolean = isLevelEnabled(Level.ERROR)

    private fun isLevelEnabled(level: Level): Boolean = level.toInt() >= AndroidLogging.minimumLevel.toInt()

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String,
        arguments: Array<Any?>?,
        throwable: Throwable?,
    ) {
        if (!isLevelEnabled(level)) return
        val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        val tag = androidTag(name)
        when (level) {
            Level.TRACE, Level.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            Level.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            Level.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            Level.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}

/** Les tags logcat pré-Android 8 étaient limités à 23 caractères — inoffensif de continuer à tronquer. */
private fun androidTag(loggerName: String): String {
    val short = loggerName.substringAfterLast('.')
    return if (short.length > 23) short.substring(0, 23) else short
}
