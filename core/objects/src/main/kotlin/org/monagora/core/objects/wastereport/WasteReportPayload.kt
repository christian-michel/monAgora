package org.monagora.core.objects.wastereport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schéma du `payload` pour `waste_report` v1 (docs/format-objets-signes.md,
 * section 5) — premier type concret vers l'application GPS citoyen
 * (docs/constitution-technique.md, section 10, étape 5).
 *
 * `category` reste une chaîne libre : la spec ne fournit qu'un exemple
 * (`overflowing_bin`), pas un ensemble fermé — contrairement à
 * `DeviceRevocationReason`, on n'invente pas ici une contrainte qu'elle ne pose
 * pas. `note` et `photoHash` sont optionnels : un signalement sans photo ni
 * commentaire reste un `waste_report` valide.
 */
@Serializable
data class WasteReportPayload(
    val lat: Double,
    val lon: Double,
    val category: String,
    val note: String? = null,
    @SerialName("photo_hash") val photoHash: String? = null,
)
