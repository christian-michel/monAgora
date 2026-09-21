package org.monagora.core.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.SignedObjects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SyncMessageCodecTest {

    @Test
    fun `hello encode puis decode redonne le meme message`() {
        val hello = Hello(protocolVersion = 1, devicePubkey = "b64u:abc", supportedTypes = listOf("waste_report"))

        val decoded = SyncMessageCodec.decode(SyncMessageCodec.encode(hello))

        assertEquals(hello, decoded)
    }

    @Test
    fun `sync_request avec valeurs par defaut encode puis decode redonne le meme message`() {
        val request = SyncRequest()

        val decoded = SyncMessageCodec.decode(SyncMessageCodec.encode(request))

        assertEquals(request, decoded)
    }

    @Test
    fun `sync_response avec un objet signe encode puis decode redonne le meme message`() {
        val keyPair = Ed25519Keys.generate()
        val obj = SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = "2026-09-21T10:15:00Z",
            payload = buildJsonObject { put("category", "overflowing_bin") },
        )
        val response = SyncResponse(objects = listOf(obj), hasMore = true, nextSince = "2026-09-21T10:15:00Z")

        val decoded = SyncMessageCodec.decode(SyncMessageCodec.encode(response))

        assertEquals(response, decoded)
    }

    @Test
    fun `file_request encode puis decode redonne le meme message`() {
        val fileRequest = FileRequest(hash = "sha256:9f86d0...")

        assertEquals(fileRequest, SyncMessageCodec.decode(SyncMessageCodec.encode(fileRequest)))
    }

    @Test
    fun `error encode puis decode redonne le meme message`() {
        val error = SyncError(code = "unsupported_version", detail = "attendu 1")

        assertEquals(error, SyncMessageCodec.decode(SyncMessageCodec.encode(error)))
    }

    @Test
    fun `decode distingue correctement les types de message via le champ msg`() {
        val decoded = SyncMessageCodec.decode("""{"msg":"sync_request","since":null,"types":null,"limit":50}""")

        assertIs<SyncRequest>(decoded)
        assertEquals(50, decoded.limit)
    }

    @Test
    fun `decode rejette un JSON syntaxiquement invalide`() {
        assertNull(SyncMessageCodec.decode("{pas du JSON"))
    }

    @Test
    fun `decode rejette un msg inconnu`() {
        assertNull(SyncMessageCodec.decode("""{"msg":"un_message_qui_n_existe_pas"}"""))
    }

    @Test
    fun `decode rejette un hello auquel il manque device_pubkey`() {
        val json = """{"msg":"hello","protocol_version":1,"supported_types":["waste_report"]}"""

        assertNull(SyncMessageCodec.decode(json))
    }

    @Test
    fun `decode rejette un file_request auquel il manque hash`() {
        assertNull(SyncMessageCodec.decode("""{"msg":"file_request"}"""))
    }
}
