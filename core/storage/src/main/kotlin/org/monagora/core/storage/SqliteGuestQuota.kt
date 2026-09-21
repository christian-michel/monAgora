package org.monagora.core.storage

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Implémentation SQLite de [GuestQuota], même note de portabilité que
 * [SqliteSignedObjectStore]/[org.monagora.core.sync.SqliteSyncCursorStore] :
 * driver JDBC desktop, à remplacer par `android.database.sqlite`/Room quand le
 * module `app` Android existera.
 *
 * @param clock horloge injectable pour les tests (date UTC par défaut) —
 *   la date du jour ne doit pas dépendre implicitement de l'horloge système
 *   dans une classe par ailleurs testée en isolation.
 */
class SqliteGuestQuota(
    path: String,
    private val clock: Clock = Clock.systemUTC(),
) : GuestQuota, AutoCloseable {
    private val logger = LoggerFactory.getLogger(SqliteGuestQuota::class.java)
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    init {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guest_quota (
                    day TEXT PRIMARY KEY,
                    count INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override fun countToday(): Int = try {
        connection.prepareStatement("SELECT count FROM guest_quota WHERE day = ?").use { statement ->
            statement.setString(1, today())
            statement.executeQuery().use { rs -> if (rs.next()) rs.getInt("count") else 0 }
        }
    } catch (e: SQLException) {
        logger.error("guest_quota_read_failed day={} reason=\"{}\"", today(), e.message)
        throw e
    }

    override fun tryConsume(dailyLimit: Int): Boolean {
        // Lecture puis écriture en deux requêtes séparées (pas de transaction
        // explicite) : suffisant pour l'usage prévu (un seul appareil, un seul
        // processus), mais voir la note de [GuestQuota.tryConsume] sur les
        // limites de ce schéma en cas d'appels concurrents.
        val current = countToday()
        if (current >= dailyLimit) {
            logger.warn("guest_quota_denied day={} count={} limit={}", today(), current, dailyLimit)
            return false
        }
        try {
            connection.prepareStatement(
                """
                INSERT INTO guest_quota (day, count) VALUES (?, 1)
                ON CONFLICT(day) DO UPDATE SET count = count + 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, today())
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            logger.error("guest_quota_write_failed day={} reason=\"{}\"", today(), e.message)
            throw e
        }
        logger.info("guest_quota_consumed day={} count={} limit={}", today(), current + 1, dailyLimit)
        return true
    }

    override fun close() = connection.close()

    private fun today(): String = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()
}
