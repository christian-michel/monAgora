package org.monagora.core.sync

/**
 * Contrat de persistance du curseur de synchronisation, conservé par pair
 * connu et identifié par sa clé publique d'appareil
 * (docs/protocole-synchronisation.md, section 5). Le calcul de la valeur à
 * écrire (avec la marge de sécurité) est fait par [SyncCursor], pas ici : ce
 * fichier ne fait que lire/écrire une chaîne, [SyncCursor] porte la logique.
 *
 * Comme [org.monagora.core.storage.SignedObjectStore] et
 * [org.monagora.core.storage.GuestQuota] (docs/architecture.md, section 4),
 * cette interface a deux implémentations qui partagent le même contrat :
 * [SqliteSyncCursorStore] (JDBC desktop, JVM pur) et
 * [org.monagora.core.sync.android.AndroidSyncCursorStore] (`android.database.sqlite`,
 * dans le module core/sync-android) — un remplacement mécanique côté appelant,
 * jamais un second protocole. Utilisée par [SyncSession] pour lire `since` au
 * début d'une session et écrire `last_synced_at` à la fin.
 */
interface SyncCursorStore {
    /** `null` si aucune synchronisation réussie n'a encore eu lieu avec ce pair. */
    fun getCursor(peerDevicePubkey: String): String?

    fun setCursor(peerDevicePubkey: String, lastSyncedAt: String)
}
