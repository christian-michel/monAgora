package org.monagora.core.storage.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base SQLite unique de l'appareil (constitution-technique.md, section 7 :
 * "SQLite pour toutes les données locales"), partagée par
 * [AndroidSignedObjectStore] et [AndroidGuestQuota] — même rôle que les
 * connexions JDBC séparées de [org.monagora.core.storage.SqliteSignedObjectStore]
 * et [org.monagora.core.storage.SqliteGuestQuota], mais une seule base par
 * appareil plutôt qu'un fichier par store, ce que permet `SQLiteOpenHelper`
 * directement.
 *
 * Schéma volontairement identique à celui des implémentations JDBC desktop —
 * même noms de colonnes, même index — pour que le remplacement annoncé dans
 * `SqliteSignedObjectStore.kt` reste un remplacement mécanique côté appelant.
 */
internal class DbOpenHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE signed_objects (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                version INTEGER NOT NULL,
                author TEXT NOT NULL,
                created_at TEXT NOT NULL,
                payload TEXT NOT NULL,
                signature TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_signed_objects_created_at ON signed_objects(created_at)")
        db.execSQL("CREATE INDEX idx_signed_objects_type ON signed_objects(type)")
        db.execSQL("CREATE INDEX idx_signed_objects_author ON signed_objects(author)")

        db.execSQL(
            """
            CREATE TABLE guest_quota (
                day TEXT PRIMARY KEY,
                count INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Rien à migrer encore : c'est le premier schéma (DB_VERSION = 1).
        // À remplir explicitement dès qu'un changement de schéma apparaît —
        // jamais un DROP/CREATE silencieux qui perdrait les données locales.
    }

    companion object {
        private const val DB_NAME = "monagora.db"
        private const val DB_VERSION = 1
    }
}
