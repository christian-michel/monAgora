package org.monagora.core.objects

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.GuestSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Couvre [SignedObjects.createAsGuest] : l'objet produit est un [SignedObject]
 * ordinaire et vérifiable, la création échoue si la session invité est déjà
 * fermée, et deux objets créés par la même session restent chacun vérifiables
 * indépendamment.
 */
class SignedObjectsGuestTest {

    private val payload = buildJsonObject { put("category", "overflowing_bin") }

    @Test
    fun `un objet cree par une session invitee est un SignedObject ordinaire, verifie avec succes`() {
        val session = GuestSession.start()

        val obj = SignedObjects.createAsGuest(
            type = "waste_report",
            version = 1,
            session = session,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )

        assertEquals(Base64Url.encode(session.publicKey), obj.author)
        assertTrue(SignedObjects.verify(obj))
    }

    @Test
    fun `createAsGuest echoue si la session a deja ete fermee`() {
        val session = GuestSession.start()
        session.close()

        assertFailsWith<IllegalStateException> {
            SignedObjects.createAsGuest(
                type = "waste_report",
                version = 1,
                session = session,
                createdAt = "2026-09-21T10:15:00Z",
                payload = payload,
            )
        }
    }

    @Test
    fun `deux objets crees par la meme session invitee restent verifiables independamment`() {
        val session = GuestSession.start()

        val obj1 = SignedObjects.createAsGuest("waste_report", 1, session, "2026-09-21T10:15:00Z", payload)
        val obj2 = SignedObjects.createAsGuest("waste_report", 1, session, "2026-09-21T10:16:00Z", payload)

        assertTrue(SignedObjects.verify(obj1))
        assertTrue(SignedObjects.verify(obj2))
        assertEquals(obj1.author, obj2.author) // même identité invité pour toute la session
    }
}
