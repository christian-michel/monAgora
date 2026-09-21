package org.monagora.core.trust

import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import org.monagora.core.objects.identity.IdentityObjects
import org.monagora.core.storage.SignedObjectStore
import org.slf4j.LoggerFactory

/**
 * Implémente les règles de validation de docs/identite-revocation.md, section 4 :
 * avant de faire confiance à un objet quelconque signé par une clé d'appareil `D`
 * au nom d'une identité `R` (clé racine), il faut :
 *
 * 1. Qu'il existe un `device_authorization` valide, `author = R`, `payload.device_pubkey = D`.
 * 2. Qu'il n'existe **aucun** `device_revocation`, `author = R`, `payload.device_pubkey = D` —
 *    sans tenir compte de l'horodatage (règle volontairement simple pour la v0.1).
 * 3. Qu'il n'existe **aucun** `identity_revocation` valide pour `R` — c'est-à-dire signé
 *    par la clé déclarée dans le `identity_declaration` de `R`.
 *
 * `R` doit être fourni par l'appelant (ce module ne devine pas à quelle identité
 * rattacher un appareil `D` inconnu — la spec elle-même part du principe qu'on
 * évalue la confiance "au nom d'une identité R" déjà déterminée par le contexte,
 * par exemple un contact déjà connu).
 *
 * Chaque objet candidat (`device_authorization`, `device_revocation`,
 * `identity_declaration`, `identity_revocation`) est re-vérifié ici via
 * [SignedObjects.verify], sans supposer que [SignedObjectStore] ne contient que
 * des objets déjà vérifiés — défense en profondeur pour une décision aussi
 * sensible qu'une résolution de confiance (constitution-technique.md, principe 7 :
 * vérifiabilité).
 */
class TrustResolver(private val store: SignedObjectStore) {
    private val logger = LoggerFactory.getLogger(TrustResolver::class.java)

    /**
     * Plafond défensif sur le nombre d'objets considérés par requête interne :
     * le nombre de `device_authorization`/`device_revocation`/`identity_declaration`/
     * `identity_revocation` par identité reste faible en usage normal (quelques
     * appareils, rarement révoqués) — ce plafond évite juste qu'un `query()` par
     * défaut (200) tronque silencieusement une requête liée à la sécurité.
     */
    private val maxCandidates = 10_000

    fun resolve(rootPublicKey: String, devicePublicKey: String): DeviceTrust {
        if (findValidDeviceAuthorization(rootPublicKey, devicePublicKey) == null) {
            logger.warn(
                "device_trust_denied root={} device={} reason={}",
                rootPublicKey,
                devicePublicKey,
                DeviceTrust.Reason.NO_DEVICE_AUTHORIZATION,
            )
            return DeviceTrust.Untrusted(DeviceTrust.Reason.NO_DEVICE_AUTHORIZATION)
        }

        if (hasValidDeviceRevocation(rootPublicKey, devicePublicKey)) {
            logger.warn(
                "device_trust_denied root={} device={} reason={}",
                rootPublicKey,
                devicePublicKey,
                DeviceTrust.Reason.DEVICE_REVOKED,
            )
            return DeviceTrust.Untrusted(DeviceTrust.Reason.DEVICE_REVOKED)
        }

        if (hasValidIdentityRevocation(rootPublicKey)) {
            logger.warn(
                "device_trust_denied root={} device={} reason={}",
                rootPublicKey,
                devicePublicKey,
                DeviceTrust.Reason.IDENTITY_REVOKED,
            )
            return DeviceTrust.Untrusted(DeviceTrust.Reason.IDENTITY_REVOKED)
        }

        logger.info("device_trust_granted root={} device={}", rootPublicKey, devicePublicKey)
        return DeviceTrust.Trusted
    }

    /**
     * Une identité invité (constitution-technique.md, section 8) ne publie jamais
     * de `device_authorization` — c'est précisément cette absence qui permet de la
     * reconnaître après coup (docs/identite-revocation.md, section 9). Contrairement
     * à [resolve], on ne connaît pas de `R` ici : on cherche si `devicePublicKey`
     * appartient au graphe d'identités durables pour N'IMPORTE QUELLE racine.
     *
     * Ne dit rien de la validité de la signature de l'objet évalué lui-même — voir
     * [SignedObjects.verify] pour ça.
     */
    fun isRegisteredDevice(devicePublicKey: String): Boolean =
        store.query(types = listOf(IdentityObjects.TYPE_DEVICE_AUTHORIZATION), limit = maxCandidates)
            .any { obj -> IdentityObjects.asDeviceAuthorization(obj)?.devicePubkey == devicePublicKey && SignedObjects.verify(obj) }

    /** Confort : `!isRegisteredDevice(obj.author)`, à partir de l'objet plutôt que de la clé nue. */
    fun isFromGuestIdentity(obj: SignedObject): Boolean = !isRegisteredDevice(obj.author)

    private fun findValidDeviceAuthorization(rootPublicKey: String, devicePublicKey: String): SignedObject? =
        store.query(types = listOf(IdentityObjects.TYPE_DEVICE_AUTHORIZATION), author = rootPublicKey, limit = maxCandidates)
            .firstOrNull { obj ->
                IdentityObjects.asDeviceAuthorization(obj)?.devicePubkey == devicePublicKey && SignedObjects.verify(obj)
            }

    private fun hasValidDeviceRevocation(rootPublicKey: String, devicePublicKey: String): Boolean =
        store.query(types = listOf(IdentityObjects.TYPE_DEVICE_REVOCATION), author = rootPublicKey, limit = maxCandidates)
            .any { obj ->
                IdentityObjects.asDeviceRevocation(obj)?.devicePubkey == devicePublicKey && SignedObjects.verify(obj)
            }

    /**
     * `null` s'il n'existe aucun `identity_declaration` valide pour `rootPublicKey` —
     * dans ce cas, aucun `identity_revocation` ne peut être valide pour cette
     * identité (rien ne peut correspondre à la clé de révocation déclarée,
     * puisqu'aucune ne l'est), donc la règle 3 est satisfaite par construction.
     * S'il en existe plusieurs (anomalie, la spec dit "publiée une fois"), la plus
     * ancienne valide l'emporte et un WARN est loggé — pour ne pas cacher le cas.
     */
    private fun findRevocationPubkey(rootPublicKey: String): String? {
        val declarations = store.query(types = listOf(IdentityObjects.TYPE_IDENTITY_DECLARATION), author = rootPublicKey, limit = maxCandidates)
            .filter { SignedObjects.verify(it) }
            .sortedBy { it.createdAt }

        if (declarations.size > 1) {
            logger.warn(
                "identity_declaration_anomaly root={} count={} reason=\"plusieurs identity_declaration valides, la plus ancienne fait foi\"",
                rootPublicKey,
                declarations.size,
            )
        }

        return declarations.firstOrNull()?.let { IdentityObjects.asIdentityDeclaration(it)?.revocationPubkey }
    }

    private fun hasValidIdentityRevocation(rootPublicKey: String): Boolean {
        val revocationPubkey = findRevocationPubkey(rootPublicKey) ?: return false

        return store.query(types = listOf(IdentityObjects.TYPE_IDENTITY_REVOCATION), author = revocationPubkey, limit = maxCandidates)
            .any { obj ->
                IdentityObjects.asIdentityRevocation(obj)?.rootPubkey == rootPublicKey && SignedObjects.verify(obj)
            }
    }
}
