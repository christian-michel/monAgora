package org.monagora.core.objects.identity

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.monagora.core.identity.Base64Url
import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import org.slf4j.LoggerFactory

/**
 * Création et lecture des quatre objets d'identité/révocation définis par
 * docs/identite-revocation.md, section 3. Chacun n'est qu'un [SignedObject]
 * ordinaire avec un `type` et un `payload` précis — rien de nouveau au niveau
 * du mécanisme de signature/vérification, qui reste [SignedObjects].
 *
 * Ce module ne fait PAS encore la résolution de confiance de la section 4
 * (« un objet signé par l'appareil D est-il actuellement valide pour
 * l'identité R ? », qui suppose de retrouver le device_authorization, les
 * device_revocation et l'identity_revocation pertinents dans le stockage local) —
 * c'est une étape distincte, plus conséquente, qui n'est pas couverte ici.
 */
object IdentityObjects {
    const val TYPE_IDENTITY_DECLARATION = "identity_declaration"
    const val TYPE_DEVICE_AUTHORIZATION = "device_authorization"
    const val TYPE_DEVICE_REVOCATION = "device_revocation"
    const val TYPE_IDENTITY_REVOCATION = "identity_revocation"

    private val logger = LoggerFactory.getLogger(IdentityObjects::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> payloadOf(value: T): JsonObject = json.encodeToJsonElement(value).jsonObject

    /** Publiée une fois, à la création de l'identité — signée par la clé racine elle-même. */
    fun createIdentityDeclaration(
        rootPublicKey: ByteArray,
        rootPrivateKey: ByteArray,
        revocationPublicKey: ByteArray,
        createdAt: String,
    ): SignedObject = SignedObjects.create(
        type = TYPE_IDENTITY_DECLARATION,
        version = 1,
        authorPublicKey = rootPublicKey,
        authorPrivateKey = rootPrivateKey,
        createdAt = createdAt,
        payload = payloadOf(IdentityDeclarationPayload(revocationPubkey = Base64Url.encode(revocationPublicKey))),
    )

    /** Publiée à chaque nouvel appareil — signée par la clé racine. */
    fun createDeviceAuthorization(
        rootPublicKey: ByteArray,
        rootPrivateKey: ByteArray,
        devicePublicKey: ByteArray,
        createdAt: String,
    ): SignedObject = SignedObjects.create(
        type = TYPE_DEVICE_AUTHORIZATION,
        version = 1,
        authorPublicKey = rootPublicKey,
        authorPrivateKey = rootPrivateKey,
        createdAt = createdAt,
        payload = payloadOf(DeviceAuthorizationPayload(devicePubkey = Base64Url.encode(devicePublicKey))),
    )

    /** Un appareil perdu/volé, l'identité reste valide — signée par la clé racine. */
    fun createDeviceRevocation(
        rootPublicKey: ByteArray,
        rootPrivateKey: ByteArray,
        revokedDevicePublicKey: ByteArray,
        reason: DeviceRevocationReason,
        createdAt: String,
    ): SignedObject = SignedObjects.create(
        type = TYPE_DEVICE_REVOCATION,
        version = 1,
        authorPublicKey = rootPublicKey,
        authorPrivateKey = rootPrivateKey,
        createdAt = createdAt,
        payload = payloadOf(DeviceRevocationPayload(devicePubkey = Base64Url.encode(revokedDevicePublicKey), reason = reason)),
    )

    /**
     * La clé racine elle-même est compromise, ou sortie volontaire — signée par
     * la clé de révocation, JAMAIS par la clé racine (docs/identite-revocation.md,
     * section 3 : "c'est tout l'intérêt d'avoir une clé séparée").
     */
    fun createIdentityRevocation(
        revocationPublicKey: ByteArray,
        revocationPrivateKey: ByteArray,
        revokedRootPublicKey: ByteArray,
        reason: String,
        createdAt: String,
    ): SignedObject = SignedObjects.create(
        type = TYPE_IDENTITY_REVOCATION,
        version = 1,
        authorPublicKey = revocationPublicKey,
        authorPrivateKey = revocationPrivateKey,
        createdAt = createdAt,
        payload = payloadOf(IdentityRevocationPayload(rootPubkey = Base64Url.encode(revokedRootPublicKey), reason = reason)),
    )

    /**
     * @return le payload typé si `obj.type == "identity_declaration"` et que le
     *   payload s'y conforme, `null` sinon (type différent, ou payload qui ne
     *   correspond pas au schéma attendu — loggé en WARN dans ce second cas).
     *   Ne dit rien sur la validité de la signature : voir [SignedObjects.verify].
     */
    fun asIdentityDeclaration(obj: SignedObject): IdentityDeclarationPayload? =
        decodePayload(obj, TYPE_IDENTITY_DECLARATION)

    fun asDeviceAuthorization(obj: SignedObject): DeviceAuthorizationPayload? =
        decodePayload(obj, TYPE_DEVICE_AUTHORIZATION)

    fun asDeviceRevocation(obj: SignedObject): DeviceRevocationPayload? =
        decodePayload(obj, TYPE_DEVICE_REVOCATION)

    fun asIdentityRevocation(obj: SignedObject): IdentityRevocationPayload? =
        decodePayload(obj, TYPE_IDENTITY_REVOCATION)

    private inline fun <reified T> decodePayload(obj: SignedObject, expectedType: String): T? {
        if (obj.type != expectedType) return null
        return try {
            json.decodeFromJsonElement<T>(obj.payload)
        } catch (e: SerializationException) {
            logger.warn(
                "identity_payload_rejected id={} type={} reason=\"payload non conforme au schéma {} : {}\"",
                obj.id,
                obj.type,
                expectedType,
                e.message,
            )
            null
        }
    }
}
