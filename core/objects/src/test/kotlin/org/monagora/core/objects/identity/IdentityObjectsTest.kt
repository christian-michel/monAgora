package org.monagora.core.objects.identity

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.SignedObjects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityObjectsTest {

    @Test
    fun `identity_declaration est cree, verifie, et son payload se relit correctement`() {
        val root = Ed25519Keys.generate()
        val revocation = Ed25519Keys.generate()

        val obj = IdentityObjects.createIdentityDeclaration(
            rootPublicKey = root.publicKey,
            rootPrivateKey = root.privateKey,
            revocationPublicKey = revocation.publicKey,
            createdAt = "2026-09-21T09:00:00Z",
        )

        assertEquals(IdentityObjects.TYPE_IDENTITY_DECLARATION, obj.type)
        assertEquals(Base64Url.encode(root.publicKey), obj.author) // signée par la clé racine
        assertTrue(SignedObjects.verify(obj))

        val payload = IdentityObjects.asIdentityDeclaration(obj)
        assertEquals(Base64Url.encode(revocation.publicKey), payload?.revocationPubkey)
    }

    @Test
    fun `device_authorization est cree, verifie, et son payload se relit correctement`() {
        val root = Ed25519Keys.generate()
        val device = Ed25519Keys.generate()

        val obj = IdentityObjects.createDeviceAuthorization(
            rootPublicKey = root.publicKey,
            rootPrivateKey = root.privateKey,
            devicePublicKey = device.publicKey,
            createdAt = "2026-09-21T09:00:00Z",
        )

        assertEquals(IdentityObjects.TYPE_DEVICE_AUTHORIZATION, obj.type)
        assertEquals(Base64Url.encode(root.publicKey), obj.author)
        assertTrue(SignedObjects.verify(obj))

        val payload = IdentityObjects.asDeviceAuthorization(obj)
        assertEquals(Base64Url.encode(device.publicKey), payload?.devicePubkey)
    }

    @Test
    fun `device_revocation est cree, verifie, avec chaque motif possible`() {
        val root = Ed25519Keys.generate()
        val device = Ed25519Keys.generate()

        for (motif in DeviceRevocationReason.entries) {
            val obj = IdentityObjects.createDeviceRevocation(
                rootPublicKey = root.publicKey,
                rootPrivateKey = root.privateKey,
                revokedDevicePublicKey = device.publicKey,
                reason = motif,
                createdAt = "2026-09-21T09:00:00Z",
            )

            assertTrue(SignedObjects.verify(obj))
            val payload = IdentityObjects.asDeviceRevocation(obj)
            assertEquals(Base64Url.encode(device.publicKey), payload?.devicePubkey)
            assertEquals(motif, payload?.reason)
        }
    }

    @Test
    fun `identity_revocation est signee par la cle de revocation, jamais par la cle racine`() {
        val root = Ed25519Keys.generate()
        val revocation = Ed25519Keys.generate()

        val obj = IdentityObjects.createIdentityRevocation(
            revocationPublicKey = revocation.publicKey,
            revocationPrivateKey = revocation.privateKey,
            revokedRootPublicKey = root.publicKey,
            reason = "compromised",
            createdAt = "2026-09-21T09:00:00Z",
        )

        assertEquals(IdentityObjects.TYPE_IDENTITY_REVOCATION, obj.type)
        assertEquals(Base64Url.encode(revocation.publicKey), obj.author)
        assertTrue(obj.author != Base64Url.encode(root.publicKey))
        assertTrue(SignedObjects.verify(obj))

        val payload = IdentityObjects.asIdentityRevocation(obj)
        assertEquals(Base64Url.encode(root.publicKey), payload?.rootPubkey)
        assertEquals("compromised", payload?.reason)
    }

    @Test
    fun `les noms de champs JSON du payload correspondent exactement a la spec`() {
        val root = Ed25519Keys.generate()
        val device = Ed25519Keys.generate()

        val obj = IdentityObjects.createDeviceRevocation(
            rootPublicKey = root.publicKey,
            rootPrivateKey = root.privateKey,
            revokedDevicePublicKey = device.publicKey,
            reason = DeviceRevocationReason.STOLEN,
            createdAt = "2026-09-21T09:00:00Z",
        )

        assertTrue(obj.payload.containsKey("device_pubkey"))
        assertTrue(obj.payload.containsKey("reason"))
        assertEquals("\"stolen\"", obj.payload["reason"].toString())
    }

    @Test
    fun `asXxx renvoie null quand le type de l objet ne correspond pas`() {
        val root = Ed25519Keys.generate()
        val revocation = Ed25519Keys.generate()
        val declaration = IdentityObjects.createIdentityDeclaration(
            root.publicKey, root.privateKey, revocation.publicKey, "2026-09-21T09:00:00Z",
        )

        assertNull(IdentityObjects.asDeviceAuthorization(declaration))
        assertNull(IdentityObjects.asDeviceRevocation(declaration))
        assertNull(IdentityObjects.asIdentityRevocation(declaration))
    }

    @Test
    fun `asDeviceAuthorization renvoie null si device_pubkey manque dans le payload`() {
        val root = Ed25519Keys.generate()
        val objAvecPayloadIncomplet = SignedObjects.create(
            type = IdentityObjects.TYPE_DEVICE_AUTHORIZATION,
            version = 1,
            authorPublicKey = root.publicKey,
            authorPrivateKey = root.privateKey,
            createdAt = "2026-09-21T09:00:00Z",
            payload = buildJsonObject { put("champ_qui_n_est_pas_device_pubkey", "valeur") },
        )

        assertNull(IdentityObjects.asDeviceAuthorization(objAvecPayloadIncomplet))
    }

    @Test
    fun `asDeviceRevocation renvoie null si reason n est pas une valeur reconnue`() {
        val root = Ed25519Keys.generate()
        val device = Ed25519Keys.generate()
        val objAvecMotifInconnu = SignedObjects.create(
            type = IdentityObjects.TYPE_DEVICE_REVOCATION,
            version = 1,
            authorPublicKey = root.publicKey,
            authorPrivateKey = root.privateKey,
            createdAt = "2026-09-21T09:00:00Z",
            payload = buildJsonObject {
                put("device_pubkey", Base64Url.encode(device.publicKey))
                put("reason", "un_motif_qui_n_existe_pas")
            },
        )

        assertNull(IdentityObjects.asDeviceRevocation(objAvecMotifInconnu))
    }
}
