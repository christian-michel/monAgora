package org.monagora.core.identity

import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Encodage des clés et signatures tel qu'utilisé dans les objets signés
 * (ex. `"author": "b64u:<root_pubkey>"` dans docs/identite-revocation.md) :
 * Base64 URL-safe sans padding, préfixé par "b64u:".
 */
object Base64Url {
    private const val PREFIX = "b64u:"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val logger = LoggerFactory.getLogger(Base64Url::class.java)

    fun encode(bytes: ByteArray): String = PREFIX + encoder.encodeToString(bytes)

    /**
     * @throws IllegalArgumentException si le préfixe est absent ou si le contenu
     *   n'est pas du Base64 URL-safe valide. L'appelant décide comment réagir
     *   (ex. rejeter l'objet signé qui contenait cette valeur) — cette fonction
     *   se contente de logger la raison avant de relancer.
     */
    fun decode(value: String): ByteArray {
        require(value.startsWith(PREFIX)) {
            "valeur b64u invalide : préfixe '$PREFIX' manquant"
        }
        return try {
            decoder.decode(value.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            logger.warn("b64u_decode_failed reason=\"{}\"", e.message)
            throw e
        }
    }
}
