package org.monagora.core.sync.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.monagora.core.sync.SyncCursorStore
import org.slf4j.LoggerFactory

/**
 * Implémentation Android (`android.database.sqlite`) de [SyncCursorStore] —
 * remplacement mécanique de
 * [org.monagora.core.sync.SqliteSyncCursorStore] (driver JDBC desktop,
 * inutilisable sur un appareil réel), même schéma, même logique, comme annoncé
 * dans sa documentation. Base dédiée, séparée de celle de
 * `core:storage-android` (`org.monagora.core.storage.android.DbOpenHelper`,
 * non exposée hors de ce module) : responsabilités distinctes, pas de
 * dépendance entre les deux modules Android pour autant.
 */
class AndroidSyncCursorStore(context: Context) : SyncCursorStore {
    private val logger = LoggerFactory.getLogger(AndroidSyncCursorStore::class.java)
    private val helper = OpenHelper(context)

    override fun getCursor(peerDevicePubkey: String): String? = try {
        helper.readableDatabase.query(
            "sync_cursors",
            arrayOf("last_synced_at"),
            "peer_device_pubkey = ?",
            arrayOf(peerDevicePubkey),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: android.database.SQLException) {
        logger.error("sync_cursor_read_failed peer={} reason=\"{}\"", peerDevicePubkey, e.message)
        throw e
    }

    override fun setCursor(peerDevicePubkey: String, lastSyncedAt: String) {
        try {
            val values = ContentValues().apply {
                put("peer_device_pubkey", peerDevicePubkey)
                put("last_synced_at", lastSyncedAt)
            }
            helper.writableDatabase.insertWithOnConflict(
                "sync_cursors",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: android.database.SQLException) {
            logger.error("sync_cursor_write_failed peer={} reason=\"{}\"", peerDevicePubkey, e.message)
            throw e
        }
        logger.info("sync_cursor_updated peer={} last_synced_at={}", peerDevicePubkey, lastSyncedAt)
    }

    private class OpenHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE sync_cursors (
                    peer_device_pubkey TEXT PRIMARY KEY,
                    last_synced_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Rien à migrer encore : premier schéma (DB_VERSION = 1). À remplir
            // explicitement dès qu'un changement de schéma apparaît — jamais un
            // DROP/CREATE silencieux qui perdrait les curseurs déjà connus.
        }

        companion object {
            const val DB_NAME = "monagora-sync-cursors.db"
            const val DB_VERSION = 1
        }
    }
}
