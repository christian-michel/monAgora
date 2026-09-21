package org.monagora.core.identity

/**
 * Une paire de clés Ed25519 : clé racine, clé d'appareil ou clé de révocation
 * selon l'usage (cf. docs/identite-revocation.md, section 2) — cette classe ne
 * distingue pas les trois rôles, c'est à l'appelant de savoir laquelle il génère.
 *
 * Pas de `data class` ici : `ByteArray` n'a pas d'égalité structurelle, et un
 * `equals`/`hashCode` généré silencieusement sur l'identité de référence serait
 * trompeur pour du matériel cryptographique. Comparer `publicKey`/`privateKey`
 * avec `contentEquals` explicitement si besoin.
 */
class Ed25519KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
)
