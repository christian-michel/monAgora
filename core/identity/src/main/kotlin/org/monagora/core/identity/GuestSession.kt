package org.monagora.core.identity

import org.slf4j.LoggerFactory

/**
 * Une identité invité (constitution-technique.md, section 8) : une paire de
 * clés Ed25519 jetable, générée localement, qui n'entre jamais dans le graphe
 * d'identités durables — aucun `identity_declaration` ni `device_authorization`
 * n'est publié pour elle. C'est justement cette absence qui permet à une
 * application de la reconnaître après coup comme non enregistrée
 * (docs/identite-revocation.md, section 9).
 *
 * La clé privée n'est jamais exposée : toute signature passe par [sign], qui
 * refuse de fonctionner après [close]. À la fermeture de session, la clé
 * privée est détruite localement (mise à zéro) — pas d'objet de révocation
 * nécessaire, la clé cesse simplement d'exister ; les objets déjà publiés
 * restent disponibles, cohérent avec le modèle append-only.
 */
class GuestSession private constructor(private val keyPair: Ed25519KeyPair) {
    @Volatile private var closed = false

    val publicKey: ByteArray get() = keyPair.publicKey

    val isClosed: Boolean get() = closed

    /** @throws IllegalStateException si la session a déjà été fermée ([close]). */
    fun sign(message: ByteArray): ByteArray {
        check(!closed) { "session invité fermée : la clé privée a été détruite" }
        return Ed25519Keys.sign(keyPair.privateKey, message)
    }

    /** Détruit la clé privée localement (mise à zéro) ; idempotent. */
    fun close() {
        if (closed) return
        keyPair.privateKey.fill(0)
        closed = true
        logger.info("guest_session_closed public_key={}", Base64Url.encode(keyPair.publicKey))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GuestSession::class.java)

        fun start(): GuestSession {
            val session = GuestSession(Ed25519Keys.generate())
            logger.info("guest_session_started public_key={}", Base64Url.encode(session.publicKey))
            return session
        }
    }
}
