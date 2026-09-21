package org.monagora.core.objects.wastereport

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.monagora.core.identity.GuestSession
import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import org.slf4j.LoggerFactory

/**
 * Création et lecture de l'objet `waste_report` (docs/format-objets-signes.md,
 * section 5). Comme pour [org.monagora.core.objects.identity.IdentityObjects],
 * il ne s'agit que d'un [SignedObject] ordinaire avec un `type` et un `payload`
 * précis — le mécanisme de signature/vérification reste [SignedObjects].
 *
 * [createAsGuest] existe parce que docs/constitution-technique.md, section 8,
 * autorise explicitement l'écriture en mode invité pour GPS citoyen
 * ("signalement utile même sans identité durable") — contrairement à AgoraVote
 * ou AgoraVoix.
 */
object WasteReports {
    const val TYPE_WASTE_REPORT = "waste_report"

    private val logger = LoggerFactory.getLogger(WasteReports::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun create(
        authorPublicKey: ByteArray,
        authorPrivateKey: ByteArray,
        createdAt: String,
        lat: Double,
        lon: Double,
        category: String,
        note: String? = null,
        photoHash: String? = null,
    ): SignedObject = SignedObjects.create(
        type = TYPE_WASTE_REPORT,
        version = 1,
        authorPublicKey = authorPublicKey,
        authorPrivateKey = authorPrivateKey,
        createdAt = createdAt,
        payload = payloadOf(lat, lon, category, note, photoHash),
    )

    fun createAsGuest(
        session: GuestSession,
        createdAt: String,
        lat: Double,
        lon: Double,
        category: String,
        note: String? = null,
        photoHash: String? = null,
    ): SignedObject = SignedObjects.createAsGuest(
        type = TYPE_WASTE_REPORT,
        version = 1,
        session = session,
        createdAt = createdAt,
        payload = payloadOf(lat, lon, category, note, photoHash),
    )

    private fun payloadOf(lat: Double, lon: Double, category: String, note: String?, photoHash: String?) =
        json.encodeToJsonElement(
            WasteReportPayload(lat = lat, lon = lon, category = category, note = note, photoHash = photoHash),
        ).jsonObject

    /**
     * @return le payload typé si `obj.type == "waste_report"` et que le payload
     *   s'y conforme, `null` sinon — loggé en WARN dans le second cas. Ne dit
     *   rien sur la validité de la signature : voir [SignedObjects.verify].
     */
    fun asWasteReport(obj: SignedObject): WasteReportPayload? {
        if (obj.type != TYPE_WASTE_REPORT) return null
        return try {
            json.decodeFromJsonElement<WasteReportPayload>(obj.payload)
        } catch (e: SerializationException) {
            logger.warn(
                "waste_report_payload_rejected id={} reason=\"payload non conforme au schéma waste_report : {}\"",
                obj.id,
                e.message,
            )
            null
        }
    }
}
