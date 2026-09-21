package org.monagora.core.sync

/**
 * Curseur de synchronisation conservé par pair connu, identifié par sa clé
 * publique d'appareil (docs/protocole-synchronisation.md, section 5).
 */
interface SyncCursorStore {
    /** `null` si aucune synchronisation réussie n'a encore eu lieu avec ce pair. */
    fun getCursor(peerDevicePubkey: String): String?

    fun setCursor(peerDevicePubkey: String, lastSyncedAt: String)
}
