package org.monagora.core.objects

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Ed25519Keys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Couvre [SignedObjectCodec.parse] : décodage d'un JSON structurellement
 * complet, rejet (retour `null`, jamais d'exception) d'un JSON auquel il
 * manque un champ obligatoire ou syntaxiquement invalide, et tolérance
 * volontaire d'un champ JSON inconnu en plus des champs attendus.
 */
class SignedObjectCodecTest {

    private fun objetValide(): SignedObject {
        val keyPair = Ed25519Keys.generate()
        return SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = buildJsonObject { put("category", "overflowing_bin") },
        )
    }

    @Test
    fun `parse decode un objet JSON structurellement complet`() {
        val obj = objetValide()
        val rawJson = """
            {"type":"${obj.type}","version":${obj.version},"author":"${obj.author}",
             "created_at":"${obj.createdAt}","payload":{"category":"overflowing_bin"},
             "id":"${obj.id}","signature":"${obj.signature}"}
        """.trimIndent()

        val decoded = SignedObjectCodec.parse(rawJson)

        assertNotNull(decoded)
        assertEquals(obj.id, decoded.id)
        assertEquals(obj.type, decoded.type)
    }

    @Test
    fun `parse rejette un JSON auquel il manque le champ signature`() {
        val obj = objetValide()
        val rawJsonSansSignature = """
            {"type":"${obj.type}","version":${obj.version},"author":"${obj.author}",
             "created_at":"${obj.createdAt}","payload":{"category":"overflowing_bin"},
             "id":"${obj.id}"}
        """.trimIndent()

        assertNull(SignedObjectCodec.parse(rawJsonSansSignature))
    }

    @Test
    fun `parse rejette un JSON auquel il manque le champ author`() {
        val obj = objetValide()
        val rawJsonSansAuthor = """
            {"type":"${obj.type}","version":${obj.version},
             "created_at":"${obj.createdAt}","payload":{"category":"overflowing_bin"},
             "id":"${obj.id}","signature":"${obj.signature}"}
        """.trimIndent()

        assertNull(SignedObjectCodec.parse(rawJsonSansAuthor))
    }

    @Test
    fun `parse rejette un JSON syntaxiquement invalide`() {
        assertNull(SignedObjectCodec.parse("{ceci n'est pas du JSON"))
    }

    @Test
    fun `parse ignore un champ JSON inconnu plutot que de rejeter l objet`() {
        val obj = objetValide()
        val rawJsonAvecChampEnPlus = """
            {"type":"${obj.type}","version":${obj.version},"author":"${obj.author}",
             "created_at":"${obj.createdAt}","payload":{"category":"overflowing_bin"},
             "id":"${obj.id}","signature":"${obj.signature}","champ_futur":"valeur"}
        """.trimIndent()

        assertNotNull(SignedObjectCodec.parse(rawJsonAvecChampEnPlus))
    }
}
