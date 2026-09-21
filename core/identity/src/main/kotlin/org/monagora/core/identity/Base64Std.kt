package org.monagora.core.identity

import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Encodage tel qu'utilisé pour le champ `signature` d'un objet signé
 * (ex. `"signature": "b64:MEUCIQ...=="` dans docs/format-objets-signes.md, section 2) :
 * Base64 standard (RFC 4648 §4, alphabet `+`/`/` avec padding), préfixé par "b64:".
 *
 * À distinguer de [Base64Url] (préfixe "b64u:", alphabet URL-safe sans padding),
 * utilisé pour les clés publiques — les deux préfixes existent précisément pour
 * ne pas confondre les deux formats en relisant un objet brut (même document,
 * section 4).
 */
object Base64Std {
    private const val PREFIX = "b64:"
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private val logger = LoggerFactory.getLogger(Base64Std::class.java)

    fun encode(bytes: ByteArray): String = PREFIX + encoder.encodeToString(bytes)

    /**
     * @throws IllegalArgumentException si le préfixe est absent ou si le contenu
     *   n'est pas du Base64 standard valide.
     */
    fun decode(value: String): ByteArray {
        require(value.startsWith(PREFIX)) {
            "valeur b64 invalide : préfixe '$PREFIX' manquant"
        }
        return try {
            decoder.decode(value.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            logger.warn("b64_decode_failed reason=\"{}\"", e.message)
            throw e
        }
    }
}
