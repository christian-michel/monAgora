package org.monagora.core.logging.android

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

/**
 * Fournisseur SLF4J 2.x qui route tous les logs du projet vers
 * `android.util.Log` (donc visibles avec `adb logcat`), plutôt que vers
 * stdout comme `slf4j-simple` — peu fiable pour observer une vraie appli
 * Android en fonctionnement (cf. CLAUDE.md, section Journalisation : toute
 * application Android du projet doit dépendre de ce module, pas de
 * `slf4j-simple`, qui reste utilisé seulement en test JVM pur par les autres
 * modules `core` (identity, objects, storage, sync, trust), cf. leurs
 * `build.gradle.kts`, `testImplementation`).
 *
 * Découvert automatiquement par le `ServiceLoader` de slf4j-api via
 * `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` — aucun câblage
 * explicite nécessaire côté application, il suffit d'avoir ce module sur le
 * classpath (et de ne pas avoir d'autre fournisseur SLF4J en même temps,
 * sans quoi le choix entre plusieurs devient non déterministe).
 */
class AndroidServiceProvider : SLF4JServiceProvider {
    private val loggerFactoryInstance = AndroidLoggerFactory()
    private val markerFactoryInstance = BasicMarkerFactory()
    private val mdcAdapterInstance = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactoryInstance
    override fun getMarkerFactory(): IMarkerFactory = markerFactoryInstance
    override fun getMDCAdapter(): MDCAdapter = mdcAdapterInstance
    override fun getRequestedApiVersion(): String = "2.0.99"
    override fun initialize() {}
}
