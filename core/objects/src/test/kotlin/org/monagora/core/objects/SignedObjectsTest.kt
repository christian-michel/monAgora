package org.monagora.core.objects

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Ed25519Keys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Couvre [SignedObjects.create]/[SignedObjects.verify] suivant
 * docs/format-objets-signes.md, section 3 : création et vérification réussies,
 * rejet si le payload ou la signature sont altérés après coup ou si l'objet
 * est signé par une clé différente de `author`, non-rejet volontaire d'un
 * `type` inconnu (portée de `verify` limitée à id/signature), rejet sans
 * exception si `author`/`signature` ne sont pas du Base64 valide, et stabilité
 * de l'`id` face à l'ordre de construction du payload (canonicalisation).
 */
class SignedObjectsTest {

    private val payload = buildJsonObject {
        put("lat", 48.8566)
        put("lon", 2.3522)
        put("category", "overflowing_bin")
    }

    @Test
    fun `un objet cree est verifie avec succes`() {
        val keyPair = Ed25519Keys.generate()

        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )

        assertTrue(SignedObjects.verify(obj))
        assertEquals(64, obj.id.length)
        assertTrue(obj.author.startsWith("b64u:"))
        assertTrue(obj.signature.startsWith("b64:"))
    }

    @Test
    fun `objet rejete si le payload est modifie apres signature`() {
        val keyPair = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )

        val trafique = obj.copy(payload = buildJsonObject { put("category", "resolved") })

        assertFalse(SignedObjects.verify(trafique))
    }

    @Test
    fun `objet rejete si la signature ne correspond pas au contenu`() {
        val keyPair = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )
        val autreObjet = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T11:00:00Z",
            payload = payload,
        )

        // id toujours cohérent avec son propre contenu, mais signature d'un autre objet.
        val trafique = obj.copy(signature = autreObjet.signature)

        assertFalse(SignedObjects.verify(trafique))
    }

    @Test
    fun `objet rejete si signe par une autre cle que celle declaree en author`() {
        val keyPair = Ed25519Keys.generate()
        val autreCle = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = autreCle.privateKey, // signé par une clé différente de author
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )

        assertFalse(SignedObjects.verify(obj))
    }

    @Test
    fun `un type inconnu n est pas rejete par verify (portee limitee a id et signature)`() {
        val keyPair = Ed25519Keys.generate()

        val obj = SignedObjects.create(
            type = "un_type_que_personne_ne_connait_encore",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        )

        assertTrue(SignedObjects.verify(obj))
    }

    @Test
    fun `verification rejetee sans exception si author n est pas un b64u valide`() {
        val keyPair = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        ).copy(author = "pas-un-b64u-valide")

        assertFalse(SignedObjects.verify(obj))
    }

    @Test
    fun `verification rejetee sans exception si signature n est pas un b64 valide`() {
        val keyPair = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = payload,
        ).copy(signature = "pas-une-signature-valide")

        assertFalse(SignedObjects.verify(obj))
    }

    @Test
    fun `deux objets logiquement identiques ont le meme id quel que soit l ordre de construction du payload`() {
        val keyPair = Ed25519Keys.generate()
        val payloadOrdreA = buildJsonObject {
            put("lat", 48.8566)
            put("lon", 2.3522)
        }
        val payloadOrdreB = buildJsonObject {
            put("lon", 2.3522)
            put("lat", 48.8566)
        }

        val objA = SignedObjects.create(
            "waste_report", 1, keyPair.publicKey, keyPair.privateKey, "2026-09-21T10:15:00Z", payloadOrdreA,
        )
        val objB = SignedObjects.create(
            "waste_report", 1, keyPair.publicKey, keyPair.privateKey, "2026-09-21T10:15:00Z", payloadOrdreB,
        )

        assertEquals(objA.id, objB.id)
    }
}
