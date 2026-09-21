package org.monagora.core.storage

import org.monagora.core.objects.SignedObject

/**
 * Persistance locale des objets signés (constitution-technique.md, section 7 :
 * "SQLite pour toutes les données locales... table des objets signés reçus/créés").
 *
 * Ne valide rien : ce contrat suppose que l'appelant a déjà vérifié l'objet
 * ([org.monagora.core.objects.SignedObjects.verify]) avant de le confier à
 * [save] — docs/protocole-synchronisation.md, section 6, est explicite : "un
 * objet invalide n'est jamais stocké". Séparer validation et persistance
 * garde chaque module responsable d'une seule chose (constitution-technique.md,
 * section 3).
 *
 * Interface implémentée deux fois, un même contrat pour deux plateformes
 * (docs/architecture.md, section 4, "motif JDBC ↔ Android") : `SqliteSignedObjectStore`
 * (ce module, JDBC desktop, utilisable en JVM pur pour dev/test) et
 * `AndroidSignedObjectStore` (`core/storage-android`, `android.database.sqlite` —
 * remplacement mécanique côté appelant, jamais un second protocole).
 */
interface SignedObjectStore {

    /**
     * @return `true` si l'objet a été inséré (nouveau), `false` s'il était déjà
     *   connu (déduplication par `id`, cf. docs/format-objets-signes.md,
     *   section 7) — un doublon n'est jamais une erreur, juste un no-op.
     */
    fun save(obj: SignedObject): Boolean

    fun findById(id: String): SignedObject?

    /**
     * @param since ne retourne que les objets dont `created_at` est strictement
     *   postérieur à cette valeur ; `null` = depuis le début. Utilisé plus tard
     *   pour répondre à un `sync_request` (docs/protocole-synchronisation.md,
     *   section 2).
     * @param types liste blanche de `type` à inclure ; `null` = tous les types.
     * @param author ne retourne que les objets dont `author` correspond exactement
     *   à cette valeur (clé publique encodée `b64u:...`) ; `null` = tous les auteurs.
     *   Utilisé par la résolution de confiance (`core/trust`) pour retrouver les
     *   objets `identity_declaration`/`device_authorization`/`device_revocation`
     *   publiés par une clé racine donnée (docs/identite-revocation.md, section 4).
     * @return les objets triés par `created_at` croissant, au plus `limit`.
     */
    fun query(since: String? = null, types: List<String>? = null, author: String? = null, limit: Int = 200): List<SignedObject>

    /** Nombre total d'objets stockés. */
    fun count(): Long
}
