package org.monagora.core.storage

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Ed25519KeyPair
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Couvre [SqliteSignedObjectStore] : écriture/relecture (`save`/`findById`),
 * déduplication par `id` (cas de rejet silencieux d'un doublon), filtres de
 * `query` (`since`, `types` — y compris liste vide, `author`, `limit`) et leur
 * tri par `created_at`, comptage (`count`), et propagation d'une erreur SQL
 * (cas d'erreur : écriture après fermeture de la connexion, cf. CLAUDE.md,
 * section Journalisation — une écriture en échec doit remonter, jamais être
 * avalée silencieusement).
 */
class SqliteSignedObjectStoreTest {

    private fun store() = SqliteSignedObjectStore(":memory:")

    private fun objetSigne(
        type: String = "waste_report",
        createdAt: String = "2026-09-21T10:15:00Z",
        keyPair: Ed25519KeyPair = Ed25519Keys.generate(),
    ): SignedObject {
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
    fun `save puis findById redonne un objet identique`() {
        store().use { s ->
            val obj = objetSigne()

            assertTrue(s.save(obj))

            assertEquals(obj, s.findById(obj.id))
        }
    }

    @Test
    fun `findById renvoie null pour un id inconnu`() {
        store().use { s ->
            assertNull(s.findById("id-qui-n-existe-pas"))
        }
    }

    @Test
    fun `save d un objet deja connu (meme id) est deduplique`() {
        store().use { s ->
            val obj = objetSigne()

            assertTrue(s.save(obj))
            assertFalse(s.save(obj))

            assertEquals(1L, s.count())
        }
    }

    @Test
    fun `query filtre par since et trie par created_at croissant`() {
        store().use { s ->
            val obj1 = objetSigne(createdAt = "2026-09-21T09:00:00Z")
            val obj2 = objetSigne(createdAt = "2026-09-21T10:00:00Z")
            val obj3 = objetSigne(createdAt = "2026-09-21T11:00:00Z")
            listOf(obj3, obj1, obj2).forEach { s.save(it) } // ordre d'insertion volontairement mélangé

            val resultat = s.query(since = "2026-09-21T09:00:00Z")

            assertEquals(listOf(obj2.id, obj3.id), resultat.map { it.id })
        }
    }

    @Test
    fun `query sans since renvoie tout depuis le debut`() {
        store().use { s ->
            s.save(objetSigne(createdAt = "2026-09-21T09:00:00Z"))
            s.save(objetSigne(createdAt = "2026-09-21T10:00:00Z"))

            assertEquals(2, s.query().size)
        }
    }

    @Test
    fun `query filtre par types`() {
        store().use { s ->
            val report = objetSigne(type = "waste_report", createdAt = "2026-09-21T09:00:00Z")
            val vote = objetSigne(type = "vote", createdAt = "2026-09-21T09:01:00Z")
            s.save(report)
            s.save(vote)

            val resultat = s.query(types = listOf("vote"))

            assertEquals(listOf(vote.id), resultat.map { it.id })
        }
    }

    @Test
    fun `query filtre par author`() {
        store().use { s ->
            val alice = Ed25519Keys.generate()
            val bob = Ed25519Keys.generate()
            val objetAlice = objetSigne(createdAt = "2026-09-21T09:00:00Z", keyPair = alice)
            val objetBob = objetSigne(createdAt = "2026-09-21T09:01:00Z", keyPair = bob)
            s.save(objetAlice)
            s.save(objetBob)

            val resultat = s.query(author = objetAlice.author)

            assertEquals(listOf(objetAlice.id), resultat.map { it.id })
        }
    }

    @Test
    fun `query avec une liste de types vide ne renvoie rien plutot que tout`() {
        store().use { s ->
            s.save(objetSigne())

            assertEquals(emptyList(), s.query(types = emptyList()))
        }
    }

    @Test
    fun `query respecte la limite`() {
        store().use { s ->
            repeat(5) { i -> s.save(objetSigne(createdAt = "2026-09-21T09:0$i:00Z")) }

            assertEquals(3, s.query(limit = 3).size)
        }
    }

    @Test
    fun `count reflete le nombre d objets stockes`() {
        store().use { s ->
            assertEquals(0L, s.count())

            s.save(objetSigne())

            assertEquals(1L, s.count())
        }
    }

    @Test
    fun `une ecriture apres fermeture du store remonte une erreur au lieu d echouer silencieusement`() {
        val s = store()
        s.close()

        assertFailsWith<SQLException> { s.save(objetSigne()) }
    }
}
