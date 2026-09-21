package org.monagora.core.objects

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.erdtman.jcs.JsonCanonicalizer

/**
 * Sérialisation canonique (RFC 8785 — JSON Canonicalization Scheme) du
 * sous-ensemble `{type, version, author, created_at, payload}` d'un objet :
 * exactement ce que couvrent `id` et `signature` (docs/format-objets-signes.md,
 * section 3). Deux objets logiquement identiques donnent toujours les mêmes
 * octets ici, quel que soit l'ordre dans lequel leurs champs ont été construits —
 * c'est ce qui rend `id`/`signature` indépendants de cet ordre.
 */
internal object CanonicalEnvelope {
    fun bytes(
        type: String,
        version: Int,
        author: String,
        createdAt: String,
        payload: JsonObject,
    ): ByteArray {
        val envelope = buildJsonObject {
            put("type", type)
            put("version", version)
            put("author", author)
            put("created_at", createdAt)
            put("payload", payload)
        }
        return JsonCanonicalizer(envelope.toString()).encodedUTF8
    }
}
