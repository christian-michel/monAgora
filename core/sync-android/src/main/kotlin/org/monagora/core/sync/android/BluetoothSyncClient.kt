package org.monagora.core.sync.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import org.monagora.core.storage.SignedObjectStore
import org.monagora.core.sync.SyncCursorStore
import org.monagora.core.sync.SyncSession
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Rôle client du transport Bluetooth : se connecte à un appareil Bluetooth
 * déjà appairé (docs/protocole-synchronisation.md, section 3, étape 1) et
 * fait tourner une [SyncSession] sur la connexion obtenue.
 *
 * S'appuie sur l'appairage standard d'Android (réglages système) : ce module
 * ne fait pas de découverte active — voir la note dans AndroidManifest.xml.
 * `device` doit donc venir de `BluetoothAdapter.getBondedDevices()`, pas d'un
 * scan.
 */
class BluetoothSyncClient(
    private val adapter: BluetoothAdapter,
    private val localDevicePubkey: String,
    private val supportedTypes: List<String>,
    private val store: SignedObjectStore,
    private val cursors: SyncCursorStore,
) {
    private val logger = LoggerFactory.getLogger(BluetoothSyncClient::class.java)

    /**
     * Bloque jusqu'à la fin de la session (ou une erreur d'E/S, relancée —
     * CLAUDE.md, section Journalisation). À appeler depuis un thread dédié,
     * jamais le thread principal Android.
     */
    fun syncWith(device: BluetoothDevice) {
        adapter.cancelDiscovery() // recommandé par la documentation Android avant toute connexion RFCOMM
        val socket = device.createRfcommSocketToServiceRecord(BluetoothSyncTransport.SERVICE_UUID)
        logger.info("bluetooth_sync_connecting remote={}", device.address)
        try {
            socket.connect()
            logger.info("bluetooth_sync_connected remote={}", device.address)
            SyncSession(
                input = socket.inputStream,
                output = socket.outputStream,
                localDevicePubkey = localDevicePubkey,
                supportedTypes = supportedTypes,
                store = store,
                cursors = cursors,
                transportName = "bluetooth",
            ).run()
        } catch (e: IOException) {
            logger.error("bluetooth_sync_connection_failed remote={} reason=\"{}\"", device.address, e.message)
            throw e
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                logger.warn("bluetooth_sync_socket_close_failed reason=\"{}\"", e.message)
            }
        }
    }
}
