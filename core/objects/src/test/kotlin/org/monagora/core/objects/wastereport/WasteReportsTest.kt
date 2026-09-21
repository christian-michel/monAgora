package org.monagora.core.objects.wastereport

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.identity.GuestSession
import org.monagora.core.objects.SignedObjects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WasteReportsTest {

    @Test
    fun `waste_report est cree, verifie, et son payload se relit correctement`() {
        val author = Ed25519Keys.generate()

        val obj = WasteReports.create(
            authorPublicKey = author.publicKey,
            authorPrivateKey = author.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            lat = 48.8566,
            lon = 2.3522,
            category = "overflowing_bin",
            note = "Poubelle débordante depuis plusieurs jours",
            photoHash = "sha256:9f86d0...",
        )

        assertEquals(WasteReports.TYPE_WASTE_REPORT, obj.type)
        assertEquals(Base64Url.encode(author.publicKey), obj.author)
        assertTrue(SignedObjects.verify(obj))

        val payload = WasteReports.asWasteReport(obj)
        assertEquals(48.8566, payload?.lat)
        assertEquals(2.3522, payload?.lon)
        assertEquals("overflowing_bin", payload?.category)
        assertEquals("Poubelle débordante depuis plusieurs jours", payload?.note)
        assertEquals("sha256:9f86d0...", payload?.photoHash)
    }

    @Test
    fun `waste_report sans note ni photo reste valide - les deux sont optionnels`() {
        val author = Ed25519Keys.generate()

        val obj = WasteReports.create(
            authorPublicKey = author.publicKey,
            authorPrivateKey = author.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            lat = 48.8566,
            lon = 2.3522,
            category = "overflowing_bin",
        )

        assertTrue(SignedObjects.verify(obj))
        val payload = WasteReports.asWasteReport(obj)
        assertNull(payload?.note)
        assertNull(payload?.photoHash)
    }

    @Test
    fun `waste_report cree en mode invite est signe par la cle jetable et reste verifiable`() {
        val session = GuestSession.start()

        val obj = WasteReports.createAsGuest(
            session = session,
            createdAt = "2026-09-21T10:15:00Z",
            lat = 48.8566,
            lon = 2.3522,
            category = "overflowing_bin",
        )

        assertEquals(Base64Url.encode(session.publicKey), obj.author)
        assertTrue(SignedObjects.verify(obj))
        assertEquals("overflowing_bin", WasteReports.asWasteReport(obj)?.category)
    }

    @Test
    fun `les noms de champs JSON du payload correspondent exactement a la spec`() {
        val author = Ed25519Keys.generate()

        val obj = WasteReports.create(
            authorPublicKey = author.publicKey,
            authorPrivateKey = author.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            lat = 48.8566,
            lon = 2.3522,
            category = "overflowing_bin",
            photoHash = "sha256:9f86d0...",
        )

        assertTrue(obj.payload.containsKey("lat"))
        assertTrue(obj.payload.containsKey("lon"))
        assertTrue(obj.payload.containsKey("category"))
        assertTrue(obj.payload.containsKey("photo_hash"))
    }

    @Test
    fun `asWasteReport renvoie null quand le type de l objet ne correspond pas`() {
        val author = Ed25519Keys.generate()
        val autreObjet = SignedObjects.create(
            type = "vote",
            version = 1,
            authorPublicKey = author.publicKey,
            authorPrivateKey = author.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = buildJsonObject {},
        )

        assertNull(WasteReports.asWasteReport(autreObjet))
    }

    @Test
    fun `asWasteReport renvoie null si un champ obligatoire manque dans le payload`() {
        val author = Ed25519Keys.generate()
        val objAvecPayloadIncomplet = SignedObjects.create(
            type = WasteReports.TYPE_WASTE_REPORT,
            version = 1,
            authorPublicKey = author.publicKey,
            authorPrivateKey = author.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = buildJsonObject {
                put("lat", 48.8566)
                // "lon" et "category" manquent volontairement
            },
        )

        assertNull(WasteReports.asWasteReport(objAvecPayloadIncomplet))
    }
}
