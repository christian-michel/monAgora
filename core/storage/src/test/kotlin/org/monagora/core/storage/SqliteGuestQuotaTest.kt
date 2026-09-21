package org.monagora.core.storage

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqliteGuestQuotaTest {

    private val jourFixe = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC)

    private fun quota() = SqliteGuestQuota(":memory:", jourFixe)

    @Test
    fun `countToday vaut 0 avant toute consommation`() {
        quota().use { q ->
            assertEquals(0, q.countToday())
        }
    }

    @Test
    fun `tryConsume incremente le compteur tant que la limite n est pas atteinte`() {
        quota().use { q ->
            assertTrue(q.tryConsume(dailyLimit = 3))
            assertTrue(q.tryConsume(dailyLimit = 3))
            assertEquals(2, q.countToday())
        }
    }

    @Test
    fun `tryConsume refuse et n incremente pas une fois la limite atteinte`() {
        quota().use { q ->
            repeat(3) { q.tryConsume(dailyLimit = 3) }

            val refuse = q.tryConsume(dailyLimit = 3)

            assertFalse(refuse)
            assertEquals(3, q.countToday())
        }
    }

    @Test
    fun `une limite de 0 refuse tout de suite`() {
        quota().use { q ->
            assertFalse(q.tryConsume(dailyLimit = 0))
            assertEquals(0, q.countToday())
        }
    }

    @Test
    fun `chaque jour a son propre compteur, independant des jours precedents`() {
        val fichier = File.createTempFile("guest-quota-test", ".db")
        fichier.deleteOnExit()
        try {
            val jour1 = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC)
            SqliteGuestQuota(fichier.absolutePath, jour1).use { q ->
                repeat(3) { q.tryConsume(dailyLimit = 10) }
            }

            val jour2 = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC)
            SqliteGuestQuota(fichier.absolutePath, jour2).use { q ->
                assertEquals(0, q.countToday())
                assertTrue(q.tryConsume(dailyLimit = 10))
                assertEquals(1, q.countToday())
            }
        } finally {
            fichier.delete()
        }
    }
}
