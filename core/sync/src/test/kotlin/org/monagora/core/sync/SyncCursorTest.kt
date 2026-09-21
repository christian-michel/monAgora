package org.monagora.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncCursorTest {

    @Test
    fun `withSafetyMargin retire 5 minutes a next_since`() {
        val resultat = SyncCursor.withSafetyMargin("2026-09-21T10:12:00Z")

        assertEquals("2026-09-21T10:07:00Z", resultat)
    }

    @Test
    fun `withSafetyMargin gere le passage a l heure precedente`() {
        val resultat = SyncCursor.withSafetyMargin("2026-09-21T10:03:00Z")

        assertEquals("2026-09-21T09:58:00Z", resultat)
    }

    @Test
    fun `withSafetyMargin rejette un horodatage qui n est pas ISO 8601`() {
        assertFailsWith<Exception> { SyncCursor.withSafetyMargin("pas-un-horodatage") }
    }
}
