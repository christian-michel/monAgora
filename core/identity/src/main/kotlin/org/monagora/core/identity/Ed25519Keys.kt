package org.monagora.core.identity

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.slf4j.LoggerFactory
import java.security.SecureRandom

/**
 * Génération de paires de clés Ed25519, signature et vérification.
 *
 * Implémentée avec l'API "lightweight" de BouncyCastle (org.bouncycastle.crypto.*),
 * pas le provider JCA (java.security.Signature) : sur Android, le provider crypto
 * système n'a pas toujours exposé Ed25519 nativement selon la version d'API visée,
 * alors que l'API lightweight de BC ne dépend d'aucun provider enregistré et se
 * comporte identiquement sur JVM classique et sur Android (cf. constitution-technique.md,
 * section 9 : "Crypto : Ed25519 ... via Tink ou BouncyCastle").
 */
object Ed25519Keys {
    private val logger = LoggerFactory.getLogger(Ed25519Keys::class.java)

    fun generate(random: SecureRandom = SecureRandom()): Ed25519KeyPair {
        val privateKeyParams = Ed25519PrivateKeyParameters(random)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        logger.debug("ed25519_keypair_generated public_key={}", Base64Url.encode(publicKeyParams.encoded))
        return Ed25519KeyPair(publicKeyParams.encoded, privateKeyParams.encoded)
    }

    /**
     * @throws IllegalArgumentException si `privateKey` n'a pas la taille attendue
     *   pour une clé privée Ed25519. Ne journalise jamais `privateKey` ni `message`
     *   si le contenu peut être sensible (cf. CLAUDE.md, section Journalisation).
     */
    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        require(privateKey.size == Ed25519PrivateKeyParameters.KEY_SIZE) {
            "clé privée Ed25519 invalide : ${privateKey.size} octets, ${Ed25519PrivateKeyParameters.KEY_SIZE} attendus"
        }
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    /**
     * Ne lance jamais d'exception : une clé, un message ou une signature malformés
     * (taille incorrecte, octets invalides) sont traités comme une vérification
     * échouée plutôt que de faire planter l'appelant — utile ici puisque ces données
     * peuvent provenir d'un pair réseau non fiable. Chaque échec est loggé en WARN
     * avec la clé publique concernée (donnée publique par nature) et la raison,
     * jamais avec la signature ou le message en clair.
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            require(publicKey.size == Ed25519PublicKeyParameters.KEY_SIZE) {
                "clé publique Ed25519 invalide : ${publicKey.size} octets, ${Ed25519PublicKeyParameters.KEY_SIZE} attendus"
            }
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            signer.update(message, 0, message.size)
            val valid = signer.verifySignature(signature)
            if (!valid) {
                logger.warn(
                    "ed25519_signature_rejected public_key={} reason=\"signature invalide\"",
                    Base64Url.encode(publicKey),
                )
            }
            valid
        } catch (e: Exception) {
            // Volontairement large : une clé/signature malformée venant d'un pair
            // réseau peut faire échouer BouncyCastle de plusieurs façons différentes
            // (IllegalArgumentException, ArrayIndexOutOfBoundsException...) — toutes
            // doivent aboutir à un rejet loggé, jamais à un crash de l'appelant.
            logger.warn(
                "ed25519_signature_rejected public_key_size={} reason=\"{}\"",
                publicKey.size,
                e.message,
            )
            false
        }
    }
}
