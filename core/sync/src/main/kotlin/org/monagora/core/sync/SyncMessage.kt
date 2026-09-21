package org.monagora.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.monagora.core.objects.SignedObject

/**
 * Vocabulaire des messages du protocole de synchronisation
 * (docs/protocole-synchronisation.md, section 2). `msg` sert de discriminant
 * pour le (dé)sérialisation polymorphe (cf. [SyncMessageCodec]) — c'est le même
 * champ que celui utilisé dans les exemples JSON de la spécification.
 *
 * Un message JSON par ligne (`\n`), UTF-8, aussi bien en local (section 3) qu'en
 * distant où il correspond au corps JSON d'une requête/réponse REST (section 4).
 */
@Serializable
sealed interface SyncMessage

@Serializable
@SerialName("hello")
data class Hello(
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("device_pubkey") val devicePubkey: String,
    @SerialName("supported_types") val supportedTypes: List<String>,
) : SyncMessage

@Serializable
@SerialName("sync_request")
data class SyncRequest(
    val since: String? = null,
    val types: List<String>? = null,
    val limit: Int = 200,
) : SyncMessage

@Serializable
@SerialName("sync_response")
data class SyncResponse(
    val objects: List<SignedObject>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_since") val nextSince: String? = null,
) : SyncMessage

@Serializable
@SerialName("file_request")
data class FileRequest(val hash: String) : SyncMessage

@Serializable
@SerialName("file_response")
data class FileResponse(val hash: String, val size: Long, val data: String) : SyncMessage

@Serializable
@SerialName("error")
data class SyncError(val code: String, val detail: String? = null) : SyncMessage
