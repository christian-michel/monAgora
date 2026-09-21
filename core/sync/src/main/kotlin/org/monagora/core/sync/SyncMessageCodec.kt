package org.monagora.core.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * (Dé)sérialisation d'un [SyncMessage] au format "une ligne JSON"
 * (docs/protocole-synchronisation.md, section 3 : framing `\n`, UTF-8).
 *
 * `decode` ne lance jamais d'exception : un message malformé (JSON invalide,
 * `msg` absent ou inconnu, champ obligatoire manquant) est loggé en WARN et
 * donne `null` — c'est à l'appelant de réagir en envoyant un [SyncError] et
 * en fermant la connexion locale, ou en répondant HTTP 400 côté distant
 * (section 6). Côté transport local, c'est [SyncSession.run] qui fait
 * exactement ça sur un `decode` qui renvoie `null`.
 */
object SyncMessageCodec {
    private val logger = LoggerFactory.getLogger(SyncMessageCodec::class.java)
    private val json = Json {
        classDiscriminator = "msg"
        ignoreUnknownKeys = true
    }

    fun encode(message: SyncMessage): String = json.encodeToString(SyncMessage.serializer(), message)

    fun decode(rawLine: String): SyncMessage? = try {
        json.decodeFromString(SyncMessage.serializer(), rawLine)
    } catch (e: SerializationException) {
        logger.warn("sync_message_rejected reason=\"JSON malformé, msg inconnu, ou champ obligatoire manquant : {}\"", e.message)
        null
    } catch (e: IllegalArgumentException) {
        logger.warn("sync_message_rejected reason=\"JSON invalide : {}\"", e.message)
        null
    }
}
