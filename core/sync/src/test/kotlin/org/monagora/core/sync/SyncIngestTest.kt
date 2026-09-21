package org.monagora.core.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import org.monagora.core.storage.SqliteSignedObjectStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Procédure de validation/rejet à l'ingestion d'un [SyncResponse]
 * ([SyncIngest.ingest], docs/protocole-synchronisation.md, section 6) : objets
 * valides et nouveaux acceptés, signature invalide rejetée et non stockée,
 * objet déjà connu compté comme dédupliqué (pas accepté), type inconnu de
 * l'application quand même stocké, et comptage correct sur un lot mixte des
 * trois catégories (accepted/rejected/deduplicated).
 */
class SyncIngestTest {

    private fun objetValide(
        type: String = "waste_report",
        createdAt: String = "2026-09-21T10:15:00Z",
    ): SignedObject {
        val keyPair = Ed25519Keys.generate()
        return SignedObjects.create(
            type = type,
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = createdAt,
            payload = buildJsonObject { put("note", "test") },
        )
    }

    @Test
    fun `des objets valides et nouveaux sont acceptes et stockes`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val obj1 = objetValide()
            val obj2 = objetValide(createdAt = "2026-09-21T10:16:00Z")
            val response = SyncResponse(objects = listOf(obj1, obj2), hasMore = false)

            val resultat = SyncIngest.ingest(response, store)

            assertEquals(SyncIngestResult(accepted = 2, rejected = 0, deduplicated = 0), resultat)
            assertEquals(2L, store.count())
        }
    }

    @Test
    fun `un objet a la signature invalide est rejete et non stocke`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val obj = objetValide().copy(signature = "b64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==")
            val response = SyncResponse(objects = listOf(obj), hasMore = false)

            val resultat = SyncIngest.ingest(response, store)

            assertEquals(SyncIngestResult(accepted = 0, rejected = 1, deduplicated = 0), resultat)
            assertNull(store.findById(obj.id))
        }
    }

    @Test
    fun `un objet deja connu est comptabilise comme deduplique, pas accepte`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val obj = objetValide()
            store.save(obj)

            val resultat = SyncIngest.ingest(SyncResponse(objects = listOf(obj), hasMore = false), store)

            assertEquals(SyncIngestResult(accepted = 0, rejected = 0, deduplicated = 1), resultat)
            assertEquals(1L, store.count())
        }
    }

    @Test
    fun `un objet de type inconnu de l application est accepte et stocke quand meme`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val obj = objetValide(type = "un_type_encore_jamais_vu")

            val resultat = SyncIngest.ingest(SyncResponse(objects = listOf(obj), hasMore = false), store)

            assertEquals(1, resultat.accepted)
            assertEquals(obj, store.findById(obj.id))
        }
    }

    @Test
    fun `un lot mixte est compte correctement dans les trois categories`() {
        SqliteSignedObjectStore(":memory:").use { store ->
            val dejaConnu = objetValide()
            store.save(dejaConnu)

            val nouveau = objetValide(createdAt = "2026-09-21T10:20:00Z")
            val invalide = objetValide(createdAt = "2026-09-21T10:21:00Z").copy(id = "id-qui-ne-correspond-pas-au-contenu")

            val resultat = SyncIngest.ingest(
                SyncResponse(objects = listOf(dejaConnu, nouveau, invalide), hasMore = false),
                store,
            )

            assertEquals(SyncIngestResult(accepted = 1, rejected = 1, deduplicated = 1), resultat)
            assertEquals(2L, store.count())
        }
    }
}
