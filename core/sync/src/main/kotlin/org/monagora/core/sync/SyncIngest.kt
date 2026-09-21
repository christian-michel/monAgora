package org.monagora.core.sync

import org.monagora.core.objects.SignedObjects
import org.monagora.core.storage.SignedObjectStore
import org.slf4j.LoggerFactory

/**
 * Nombre d'objets, parmi ceux d'un [SyncResponse], effectivement acceptés
 * (nouveaux et stockés), rejetés (id/signature invalide) ou dédupliqués (déjà
 * connus) — exactement ce que CLAUDE.md demande de logger pour une synchronisation
 * (section Journalisation : "le nombre d'objets effectivement acceptés vs
 * rejetés vs dédupliqués").
 */
data class SyncIngestResult(val accepted: Int, val rejected: Int, val deduplicated: Int) {
    val total: Int get() = accepted + rejected + deduplicated
}

/**
 * Applique la procédure de validation et de rejet de
 * docs/protocole-synchronisation.md, section 6, à un [SyncResponse] reçu :
 *
 * 1. Vérifier id/signature de chaque objet ([SignedObjects.verify]).
 * 2. Objet invalide → jamais stocké, jamais retransmis (ici : simplement pas
 *    stocké — la retransmission relève d'une couche transport qui n'existe
 *    pas encore).
 * 3. Objet valide (même si `type` inconnu de l'application locale) → stocké
 *    quand même ([SignedObjectStore.save] ne connaît pas la notion de `type`
 *    "connu" ; cf. la note de portée dans [SignedObjects]).
 */
object SyncIngest {
    private val logger = LoggerFactory.getLogger(SyncIngest::class.java)

    fun ingest(response: SyncResponse, store: SignedObjectStore): SyncIngestResult {
        var accepted = 0
        var rejected = 0
        var deduplicated = 0

        for (obj in response.objects) {
            if (!SignedObjects.verify(obj)) {
                // La raison précise est déjà loggée par SignedObjects.verify() lui-même.
                rejected++
                continue
            }
            if (store.save(obj)) accepted++ else deduplicated++
        }

        logger.info(
            "sync_response_ingested accepted={} rejected={} deduplicated={} has_more={}",
            accepted,
            rejected,
            deduplicated,
            response.hasMore,
        )
        return SyncIngestResult(accepted, rejected, deduplicated)
    }
}
