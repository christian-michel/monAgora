package org.monagora.core.trust

/** Résultat de [TrustResolver.resolve] pour un couple (identité racine, appareil). */
sealed interface DeviceTrust {
    data object Trusted : DeviceTrust

    data class Untrusted(val reason: Reason) : DeviceTrust

    /** Correspond exactement aux trois règles de docs/identite-revocation.md, section 4. */
    enum class Reason {
        /** Règle 1 : aucun `device_authorization` valide (author=R, payload.device_pubkey=D). */
        NO_DEVICE_AUTHORIZATION,

        /** Règle 2 : un `device_revocation` existe pour ce couple (R, D) — peu importe l'horodatage. */
        DEVICE_REVOKED,

        /** Règle 3 : un `identity_revocation` valide existe pour R. */
        IDENTITY_REVOKED,
    }
}
