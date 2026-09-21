package org.monagora.core.identity

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuestSessionTest {

    @Test
    fun `une session invitee produit des signatures verifiables`() {
        val session = GuestSession.start()
        val message = "signalement:poubelle_pleine".toByteArray()

        val signature = session.sign(message)

        assertTrue(Ed25519Keys.verify(session.publicKey, message, signature))
    }

    @Test
    fun `deux sessions invitees ont des cles distinctes`() {
        val a = GuestSession.start()
        val b = GuestSession.start()

        assertFalse(a.publicKey.contentEquals(b.publicKey))
    }

    @Test
    fun `close empeche toute signature ulterieure`() {
        val session = GuestSession.start()

        session.close()

        assertTrue(session.isClosed)
        assertFailsWith<IllegalStateException> { session.sign("test".toByteArray()) }
    }

    @Test
    fun `close est idempotent`() {
        val session = GuestSession.start()

        session.close()
        session.close() // ne doit pas lancer d'exception

        assertTrue(session.isClosed)
    }

    @Test
    fun `isClosed est faux avant fermeture`() {
        val session = GuestSession.start()

        assertFalse(session.isClosed)
    }
}
