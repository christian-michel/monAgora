package org.monagora.core.objects

import java.security.MessageDigest

/**
 * Empreinte SHA-256 en hexadécimal minuscule, utilisée pour calculer le champ
 * `id` d'un objet signé à partir de son enveloppe canonique (docs/format-objets-signes.md,
 * section 3 : `id = hex(SHA256(bytes))`) — voir [SignedObjects] pour l'appelant.
 * `internal` : ce détail d'implémentation n'a pas vocation à être appelé
 * directement en dehors de ce module (contrairement à [org.monagora.core.identity.Ed25519Keys],
 * qui expose une API crypto générale, `Sha256` n'est qu'un rouage du calcul d'`id`).
 */
internal object Sha256 {
    /** @return les 32 octets du hash, encodés en 64 caractères hexadécimaux minuscules. */
    fun hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
