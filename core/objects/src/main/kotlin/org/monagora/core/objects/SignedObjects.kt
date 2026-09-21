package org.monagora.core.objects

import kotlinx.serialization.json.JsonObject
import org.monagora.core.identity.Base64Std
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.Ed25519Keys
import org.slf4j.LoggerFactory

/**
 * Création et vérification d'objets signés, suivant à la lettre
 * docs/format-objets-signes.md, section 3.
 *
 * Note volontaire sur la portée : [verify] ne vérifie que l'intégrité (`id`) et
 * l'authenticité (`signature`) — jamais si `type` est reconnu. Un `type` inconnu
 * n'est PAS un motif de rejet ici : docs/protocole-synchronisation.md, section 6,
 * est explicite là-dessus ("Objet valide mais type inconnu ... → stocké quand
 * même, mais non interprété"), et format-objets-signes.md section 6 confirme que
 * "le système ne fait que transporter et vérifier les objets, il ne décide pas de
 * leur sens". Décider quoi faire d'un type inconnu est donc laissé à la couche
 * application/stockage, pas à ce module.
 */
object SignedObjects {
    private val logger = LoggerFactory.getLogger(SignedObjects::class.java)

    /**
     * @param createdAt horodatage UTC ISO 8601 déjà formaté (ex. `AAAA-MM-JJTHH:MM:SSZ`) —
     *   fourni par l'appelant plutôt que capturé ici, pour que la fonction reste
     *   pure et testable avec un temps déterministe.
     */
    fun create(
        type: String,
        version: Int,
        authorPublicKey: ByteArray,
        authorPrivateKey: ByteArray,
        createdAt: String,
        payload: JsonObject,
    ): SignedObject {
        val author = Base64Url.encode(authorPublicKey)
        val bytes = CanonicalEnvelope.bytes(type, version, author, createdAt, payload)
        val id = Sha256.hex(bytes)
        val signature = Base64Std.encode(Ed25519Keys.sign(authorPrivateKey, bytes))
        logger.info("object_created id={} type={}", id, type)
        return SignedObject(type, version, author, createdAt, payload, id, signature)
    }

    /**
     * Ne lance jamais d'exception : un objet dont `author`/`signature` ne sont
     * même pas du Base64 valide est un rejet loggé comme les autres, pas un crash —
     * ces objets peuvent provenir d'un pair réseau non fiable (CLAUDE.md, section
     * Journalisation).
     */
    fun verify(obj: SignedObject): Boolean {
        return try {
            val bytes = CanonicalEnvelope.bytes(obj.type, obj.version, obj.author, obj.createdAt, obj.payload)

            val expectedId = Sha256.hex(bytes)
            if (expectedId != obj.id) {
                logger.warn(
                    "object_rejected id={} type={} reason=\"id incohérent avec le contenu\"",
                    obj.id,
                    obj.type,
                )
                return false
            }

            val authorPublicKey = Base64Url.decode(obj.author)
            val signatureBytes = Base64Std.decode(obj.signature)
            val valid = Ed25519Keys.verify(authorPublicKey, bytes, signatureBytes)
            if (valid) {
                logger.debug("object_accepted id={} type={}", obj.id, obj.type)
            } else {
                logger.warn("object_rejected id={} type={} reason=\"signature invalide\"", obj.id, obj.type)
            }
            valid
        } catch (e: IllegalArgumentException) {
            logger.warn("object_rejected id={} type={} reason=\"{}\"", obj.id, obj.type, e.message)
            false
        }
    }
}
