package org.monagora.core.trust

import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.identity.DeviceRevocationReason
import org.monagora.core.objects.identity.IdentityObjects
import org.monagora.core.storage.SqliteSignedObjectStore
import kotlin.test.Test
import kotlin.test.assertEquals

class TrustResolverTest {

    @Test
    fun `appareil autorise et jamais revoque est de confiance`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Trusted, resultat)
        }
    }

    @Test
    fun `appareil jamais autorise n est pas de confiance`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Untrusted(DeviceTrust.Reason.NO_DEVICE_AUTHORIZATION), resultat)
        }
    }

    @Test
    fun `autorisation pour une autre identite ne compte pas`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val autreRoot = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    autreRoot.publicKey, autreRoot.privateKey, device.publicKey, "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Untrusted(DeviceTrust.Reason.NO_DEVICE_AUTHORIZATION), resultat)
        }
    }

    @Test
    fun `appareil revoque n est plus de confiance, meme si la revocation precede l autorisation`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            // Révocation antérieure à l'autorisation dans le temps : la règle 2 dit
            // explicitement "sans tenir compte de l'horodatage".
            store.save(
                IdentityObjects.createDeviceRevocation(
                    root.publicKey, root.privateKey, device.publicKey, DeviceRevocationReason.STOLEN, "2026-01-01T00:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Untrusted(DeviceTrust.Reason.DEVICE_REVOKED), resultat)
        }
    }

    @Test
    fun `revocation d un autre appareil ne revoque pas celui-ci`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            val autreDevice = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-09-21T09:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createDeviceRevocation(
                    root.publicKey, root.privateKey, autreDevice.publicKey, DeviceRevocationReason.LOST, "2026-09-21T09:01:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Trusted, resultat)
        }
    }

    @Test
    fun `identite revoquee par la vraie cle de revocation invalide tous ses appareils`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val revocation = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createIdentityDeclaration(
                    root.publicKey, root.privateKey, revocation.publicKey, "2026-01-01T00:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-01-02T00:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createIdentityRevocation(
                    revocation.publicKey, revocation.privateKey, root.publicKey, "compromised", "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Untrusted(DeviceTrust.Reason.IDENTITY_REVOKED), resultat)
        }
    }

    @Test
    fun `identity_revocation signee par une cle qui n est pas la cle de revocation declaree est ignoree`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val vraieRevocation = Ed25519Keys.generate()
            val fausseRevocation = Ed25519Keys.generate() // n'importe qui peut générer une clé et prétendre révoquer
            val device = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createIdentityDeclaration(
                    root.publicKey, root.privateKey, vraieRevocation.publicKey, "2026-01-01T00:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-01-02T00:00:00Z",
                ),
            )
            store.save(
                IdentityObjects.createIdentityRevocation(
                    fausseRevocation.publicKey, fausseRevocation.privateKey, root.publicKey, "compromised", "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Trusted, resultat)
        }
    }

    @Test
    fun `sans identity_declaration, aucun identity_revocation ne peut etre valide`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val pretendantRevocation = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            store.save(
                IdentityObjects.createDeviceAuthorization(
                    root.publicKey, root.privateKey, device.publicKey, "2026-01-02T00:00:00Z",
                ),
            )
            // Aucun identity_declaration n'a jamais été publié pour root : cette
            // "révocation" ne peut donc correspondre à aucune revocation_pubkey déclarée.
            store.save(
                IdentityObjects.createIdentityRevocation(
                    pretendantRevocation.publicKey, pretendantRevocation.privateKey, root.publicKey, "compromised", "2026-09-21T09:00:00Z",
                ),
            )

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Trusted, resultat)
        }
    }

    @Test
    fun `un device_authorization dont la signature a ete alteree n accorde pas confiance`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val root = Ed25519Keys.generate()
            val device = Ed25519Keys.generate()
            val authorization = IdentityObjects.createDeviceAuthorization(
                root.publicKey, root.privateKey, device.publicKey, "2026-09-21T09:00:00Z",
            )
            // save() ne re-vérifie pas (ce n'est pas son rôle) : on peut donc y stocker
            // directement un objet altéré pour tester la défense en profondeur de TrustResolver.
            store.save(authorization.copy(signature = "b64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="))

            val resultat = TrustResolver(store).resolve(Base64Url.encode(root.publicKey), Base64Url.encode(device.publicKey))

            assertEquals(DeviceTrust.Untrusted(DeviceTrust.Reason.NO_DEVICE_AUTHORIZATION), resultat)
        }
    }
}
