package org.monagora.core.objects.identity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schémas de `payload` pour les quatre objets d'identité/révocation
 * (docs/identite-revocation.md, section 3) — un cas particulier du "registre
 * ouvert" de types de docs/format-objets-signes.md, section 5.
 */
@Serializable
data class IdentityDeclarationPayload(
    @SerialName("revocation_pubkey") val revocationPubkey: String,
)

@Serializable
data class DeviceAuthorizationPayload(
    @SerialName("device_pubkey") val devicePubkey: String,
)

/**
 * "informatif, n'affecte pas la validation" (docs/identite-revocation.md,
 * section 3) — un type fermé communique quand même l'ensemble attendu plutôt
 * que de laisser passer une chaîne arbitraire sans avertissement.
 */
@Serializable
enum class DeviceRevocationReason {
    @SerialName("lost") LOST,
    @SerialName("stolen") STOLEN,
    @SerialName("replaced") REPLACED,
    @SerialName("other") OTHER,
}

@Serializable
data class DeviceRevocationPayload(
    @SerialName("device_pubkey") val devicePubkey: String,
    val reason: DeviceRevocationReason,
)

/**
 * `reason` reste une chaîne libre ici (contrairement à [DeviceRevocationPayload]) :
 * la spec illustre uniquement `"compromised"` sans énumérer un ensemble fermé
 * pour ce type, donc on n'invente pas de contrainte qu'elle ne pose pas.
 */
@Serializable
data class IdentityRevocationPayload(
    @SerialName("root_pubkey") val rootPubkey: String,
    val reason: String,
)
