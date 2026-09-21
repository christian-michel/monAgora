package org.monagora.core.logging.android

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * [ILoggerFactory] du binding : crée et met en cache un [AndroidLogger] par
 * nom de logger (typiquement un nom de classe complet, `LoggerFactory
 * .getLogger(MaClasse::class.java)`), pour ne pas en réallouer un à chaque
 * appel. `ConcurrentHashMap` car des loggers peuvent être demandés depuis
 * plusieurs threads (ex. `MainActivity` crée des `Logger` depuis le thread
 * UI et depuis les threads de synchronisation Bluetooth, cf. `MainActivity
 * .kt`).
 */
internal class AndroidLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger = loggers.getOrPut(name) { AndroidLogger(name) }
}
