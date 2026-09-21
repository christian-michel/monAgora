package org.monagora.core.storage.android

import android.content.ContentValues
import android.content.Context
import org.monagora.core.storage.GuestQuota
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Implémentation [GuestQuota] pour Android — remplacement mécanique de
 * [org.monagora.core.storage.SqliteGuestQuota] annoncé dans sa propre
 * documentation, même schéma (`guest_quota`, une ligne par jour).
 *
 * L'incrémentation évite la syntaxe `ON CONFLICT ... DO UPDATE` (UPSERT) :
 * pas garantie disponible sur toutes les versions de SQLite embarquées selon
 * l'appareil/la ROM (minSdk 26) — une tentative d'insertion suivie, en cas de
 * conflit, d'une mise à jour explicite reste correcte sur toutes les versions.
 */
class AndroidGuestQuota(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : GuestQuota {
    private val logger = LoggerFactory.getLogger(AndroidGuestQuota::class.java)
    private val dbHelper = DbOpenHelper(context.applicationContext)

    override fun countToday(): Int = try {
        dbHelper.readableDatabase.query(
            "guest_quota",
            arrayOf("count"),
            "day = ?",
            arrayOf(today()),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    } catch (e: android.database.SQLException) {
        logger.error("guest_quota_read_failed day={} reason=\"{}\"", today(), e.message)
        throw e
    }

    override fun tryConsume(dailyLimit: Int): Boolean {
        // Lecture (countToday) puis écriture séparée, comme SqliteGuestQuota —
        // voir la note sur GuestQuota.tryConsume : suffisant pour un seul
        // appareil/processus, pas garanti sous appels concurrents.
        val current = countToday()
        if (current >= dailyLimit) {
            logger.warn("guest_quota_denied day={} count={} limit={}", today(), current, dailyLimit)
            return false
        }

        try {
            val db = dbHelper.writableDatabase
            val inserted = db.insertWithOnConflict(
                "guest_quota",
                null,
                ContentValues().apply { put("day", today()); put("count", 1) },
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (inserted == -1L) {
                db.execSQL("UPDATE guest_quota SET count = count + 1 WHERE day = ?", arrayOf(today()))
            }
        } catch (e: android.database.SQLException) {
            logger.error("guest_quota_write_failed day={} reason=\"{}\"", today(), e.message)
            throw e
        }

        logger.info("guest_quota_consumed day={} count={} limit={}", today(), current + 1, dailyLimit)
        return true
    }

    private fun today(): String = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()
}
