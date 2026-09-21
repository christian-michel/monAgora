package org.monagora.core.sync

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Implémentation SQLite de [SyncCursorStore] (docs/protocole-synchronisation.md,
 * section 5 : curseur par pair). Même note de portabilité que
 * [org.monagora.core.storage.SqliteSignedObjectStore] : driver JDBC desktop
 * (`org.xerial:sqlite-jdbc`), utilisable uniquement en JVM pur (pas sur un
 * appareil Android réel, cf. docs/architecture.md, section 4) — sert au
 * développement et aux tests de ce module (`core/sync`) avant qu'un appareil
 * Android soit disponible. [org.monagora.core.sync.android.AndroidSyncCursorStore]
 * (module `core/sync-android`) fournit l'équivalent réel via
 * `android.database.sqlite`, même schéma, même contrat — [SyncCursorStore]
 * est l'interface qui rend ce remplacement mécanique.
 */
class SqliteSyncCursorStore(path: String) : SyncCursorStore, AutoCloseable {
    private val logger = LoggerFactory.getLogger(SqliteSyncCursorStore::class.java)
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    init {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_cursors (
                    peer_device_pubkey TEXT PRIMARY KEY,
                    last_synced_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override fun getCursor(peerDevicePubkey: String): String? = try {
        connection.prepareStatement("SELECT last_synced_at FROM sync_cursors WHERE peer_device_pubkey = ?").use { statement ->
            statement.setString(1, peerDevicePubkey)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString("last_synced_at") else null }
        }
    } catch (e: SQLException) {
        logger.error("sync_cursor_read_failed peer={} reason=\"{}\"", peerDevicePubkey, e.message)
        throw e
    }

    override fun setCursor(peerDevicePubkey: String, lastSyncedAt: String) {
        try {
            // Upsert en une requête (clé primaire = peer_device_pubkey) : un
            // seul curseur par pair, la valeur la plus récente écrase toujours
            // la précédente — jamais d'historique à conserver ici.
            connection.prepareStatement(
                """
                INSERT INTO sync_cursors (peer_device_pubkey, last_synced_at) VALUES (?, ?)
                ON CONFLICT(peer_device_pubkey) DO UPDATE SET last_synced_at = excluded.last_synced_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, peerDevicePubkey)
                statement.setString(2, lastSyncedAt)
                statement.executeUpdate()
            }
            logger.info("sync_cursor_updated peer={} last_synced_at={}", peerDevicePubkey, lastSyncedAt)
        } catch (e: SQLException) {
            logger.error("sync_cursor_write_failed peer={} reason=\"{}\"", peerDevicePubkey, e.message)
            throw e
        }
    }

    override fun close() = connection.close()
}
