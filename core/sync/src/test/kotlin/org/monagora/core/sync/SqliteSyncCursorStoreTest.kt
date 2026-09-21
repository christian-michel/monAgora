package org.monagora.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteSyncCursorStoreTest {

    private fun store() = SqliteSyncCursorStore(":memory:")

    @Test
    fun `getCursor renvoie null pour un pair jamais synchronise`() {
        store().use { s ->
            assertNull(s.getCursor("b64u:pair-inconnu"))
        }
    }

    @Test
    fun `setCursor puis getCursor redonne la valeur ecrite`() {
        store().use { s ->
            s.setCursor("b64u:pair-1", "2026-09-21T10:00:00Z")

            assertEquals("2026-09-21T10:00:00Z", s.getCursor("b64u:pair-1"))
        }
    }

    @Test
    fun `setCursor met a jour la valeur existante plutot que d en creer une seconde`() {
        store().use { s ->
            s.setCursor("b64u:pair-1", "2026-09-21T10:00:00Z")
            s.setCursor("b64u:pair-1", "2026-09-21T11:00:00Z")

            assertEquals("2026-09-21T11:00:00Z", s.getCursor("b64u:pair-1"))
        }
    }

    @Test
    fun `chaque pair a son propre curseur independant`() {
        store().use { s ->
            s.setCursor("b64u:pair-1", "2026-09-21T10:00:00Z")
            s.setCursor("b64u:pair-2", "2026-09-21T12:00:00Z")

            assertEquals("2026-09-21T10:00:00Z", s.getCursor("b64u:pair-1"))
            assertEquals("2026-09-21T12:00:00Z", s.getCursor("b64u:pair-2"))
        }
    }
}
