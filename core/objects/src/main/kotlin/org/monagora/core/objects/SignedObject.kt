package org.monagora.core.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Un objet signé, tel qu'échangé entre appareils (docs/format-objets-signes.md,
 * section 2). Tous les champs sont obligatoires : la désérialisation JSON
 * (voir [SignedObjectCodec]) échoue si l'un d'eux manque, plutôt que de produire
 * un objet partiel qui passerait silencieusement les vérifications suivantes.
 *
 * Ce type ne garantit PAS à lui seul que `id`/`signature` sont corrects — voir
 * [SignedObjects.verify]. Il représente juste "un JSON structurellement complet",
 * pas encore "un objet auquel on peut faire confiance".
 */
@Serializable
data class SignedObject(
    val type: String,
    val version: Int,
    val author: String,
    @SerialName("created_at") val createdAt: String,
    val payload: JsonObject,
    val id: String,
    val signature: String,
)
