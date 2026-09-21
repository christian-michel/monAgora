package org.monagora.core.sync

import java.time.Duration
import java.time.Instant

/**
 * Calcul du curseur de synchronisation par pair (docs/protocole-synchronisation.md,
 * section 5) : après une synchronisation réussie, `last_synced_at` est mis à
 * `next_since` reçu, moins une marge de sécurité, pour absorber un éventuel
 * décalage d'horloge entre appareils. La spécification donne "ex. 5 minutes"
 * comme ordre de grandeur, pas une valeur imposée au bit près — reprise telle
 * quelle ici plutôt qu'inventée.
 *
 * Objet pur (pas d'I/O) : ne fait que le calcul, appelé par [SyncSession] une
 * fois la dernière page de sa propre requête reçue (`ourPullDone`), juste
 * avant d'écrire le résultat dans un [SyncCursorStore]. Pécher par excès de
 * prudence ici n'est jamais un problème : la déduplication par `id` à
 * l'ingestion ([SyncIngest]) rend un objet redemandé par erreur inoffensif,
 * juste un peu de bande passante gaspillée (même section 5).
 */
object SyncCursor {
    private val SAFETY_MARGIN: Duration = Duration.ofMinutes(5)

    /**
     * @param nextSince horodatage ISO 8601 UTC (`next_since` d'un [SyncResponse]).
     * @throws java.time.format.DateTimeParseException si `nextSince` n'est pas
     *   un horodatage ISO 8601 valide — l'appelant décide comment réagir, cette
     *   fonction ne l'avale pas silencieusement.
     */
    fun withSafetyMargin(nextSince: String): String = Instant.parse(nextSince).minus(SAFETY_MARGIN).toString()
}
