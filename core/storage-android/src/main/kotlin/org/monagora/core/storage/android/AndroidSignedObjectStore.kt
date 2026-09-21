package org.monagora.core.storage.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.monagora.core.objects.SignedObject
import org.monagora.core.storage.SignedObjectStore
import org.slf4j.LoggerFactory

/**
 * Implémentation [SignedObjectStore] pour Android, sur `android.database.sqlite` —
 * remplacement mécanique annoncé dans la note de portabilité de
 * [org.monagora.core.storage.SqliteSignedObjectStore] : même contrat, même
 * schéma, seul le driver change.
 */
class AndroidSignedObjectStore(context: Context) : SignedObjectStore {
    private val logger = LoggerFactory.getLogger(AndroidSignedObjectStore::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val dbHelper = DbOpenHelper(context.applicationContext)

    override fun save(obj: SignedObject): Boolean {
        val values = ContentValues().apply {
            put("id", obj.id)
            put("type", obj.type)
            put("version", obj.version)
            put("author", obj.author)
            put("created_at", obj.createdAt)
            put("payload", obj.payload.toString())
            put("signature", obj.signature)
        }
        val rowId = try {
            dbHelper.writableDatabase.insertWithOnConflict(
                "signed_objects",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        } catch (e: android.database.SQLException) {
            logger.error("object_store_write_failed id={} type={} reason=\"{}\"", obj.id, obj.type, e.message)
            throw e
        }
        return if (rowId == -1L) {
            logger.debug("object_store_deduplicated id={} type={}", obj.id, obj.type)
            false
        } else {
            logger.info("object_stored id={} type={}", obj.id, obj.type)
            true
        }
    }

    override fun findById(id: String): SignedObject? =
        dbHelper.readableDatabase.query(
            "signed_objects",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toSignedObject() else null }

    override fun query(since: String?, types: List<String>?, author: String?, limit: Int): List<SignedObject> {
        if (types != null && types.isEmpty()) return emptyList()

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (since != null) {
            conditions.add("created_at > ?")
            args.add(since)
        }
        if (types != null) {
            conditions.add("type IN (${types.joinToString(",") { "?" }})")
            args.addAll(types)
        }
        if (author != null) {
            conditions.add("author = ?")
            args.add(author)
        }
        val whereClause = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")

        return dbHelper.readableDatabase.query(
            "signed_objects",
            null,
            whereClause,
            if (args.isEmpty()) null else args.toTypedArray(),
            null,
            null,
            "created_at ASC",
            limit.toString(),
        ).use { cursor ->
            val results = mutableListOf<SignedObject>()
            while (cursor.moveToNext()) results.add(cursor.toSignedObject())
            results
        }
    }

    override fun count(): Long =
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM signed_objects", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun android.database.Cursor.toSignedObject(): SignedObject = SignedObject(
        type = getString(getColumnIndexOrThrow("type")),
        version = getInt(getColumnIndexOrThrow("version")),
        author = getString(getColumnIndexOrThrow("author")),
        createdAt = getString(getColumnIndexOrThrow("created_at")),
        payload = json.parseToJsonElement(getString(getColumnIndexOrThrow("payload"))).jsonObject,
        id = getString(getColumnIndexOrThrow("id")),
        signature = getString(getColumnIndexOrThrow("signature")),
    )
}
