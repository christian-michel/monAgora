package org.monagora.core.sync.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import org.monagora.core.storage.SignedObjectStore
import org.monagora.core.sync.SyncCursorStore
import org.monagora.core.sync.SyncSession
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Rôle serveur du transport Bluetooth (docs/protocole-synchronisation.md,
 * section 3, étape 1) : écoute les connexions RFCOMM entrantes et fait
 * tourner une [SyncSession] par connexion acceptée, chacune sur son propre
 * thread pour ne pas bloquer l'acceptation de la suivante.
 *
 * [start] bloque l'appelant tant que le serveur écoute — à lancer depuis un
 * thread ou un service dédié, jamais depuis le thread principal Android.
 * Nécessite `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE` déjà accordées (API 31+)
 * — précondition de l'appelant, pas vérifiée ici (même logique que
 * [org.monagora.core.storage.SignedObjectStore] qui suppose l'objet déjà
 * vérifié avant `save()` : chaque module reste responsable d'une seule chose).
 */
class BluetoothSyncServer(
    private val adapter: BluetoothAdapter,
    private val localDevicePubkey: String,
    private val supportedTypes: List<String>,
    private val store: SignedObjectStore,
    private val cursors: SyncCursorStore,
) {
    private val logger = LoggerFactory.getLogger(BluetoothSyncServer::class.java)

    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running = false

    /**
     * Bloque jusqu'à [stop] ou une erreur d'E/S irrécupérable sur le socket
     * serveur lui-même (relancée, jamais avalée — CLAUDE.md, section
     * Journalisation).
     */
    fun start() {
        val socket = adapter.listenUsingRfcommWithServiceRecord(
            BluetoothSyncTransport.SERVICE_NAME,
            BluetoothSyncTransport.SERVICE_UUID,
        )
        serverSocket = socket
        running = true
        logger.info("bluetooth_sync_server_started")
        try {
            while (running) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (!running) {
                        logger.info("bluetooth_sync_server_accept_interrupted reason=\"arrêt demandé\"")
                        null
                    } else {
                        logger.error("bluetooth_sync_server_accept_failed reason=\"{}\"", e.message)
                        throw e
                    }
                }
                if (connection != null) {
                    Thread({ handleConnection(connection) }, "sync-bt-accept-${connection.remoteDevice.address}").start()
                }
            }
        } finally {
            logger.info("bluetooth_sync_server_stopped")
        }
    }

    /** Ferme le socket d'écoute ; les connexions déjà acceptées se terminent normalement. */
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            logger.warn("bluetooth_sync_server_close_failed reason=\"{}\"", e.message)
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        try {
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
            logger.error("bluetooth_sync_connection_failed remote={} reason=\"{}\"", socket.remoteDevice.address, e.message)
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                logger.warn("bluetooth_sync_socket_close_failed reason=\"{}\"", e.message)
            }
        }
    }
}
