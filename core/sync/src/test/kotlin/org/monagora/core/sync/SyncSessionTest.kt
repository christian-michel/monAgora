package org.monagora.core.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.Ed25519Keys
import org.monagora.core.objects.SignedObject
import org.monagora.core.objects.SignedObjects
import org.monagora.core.storage.SqliteSignedObjectStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncSessionTest {

    private fun objetSigne(createdAt: String, note: String = "test"): SignedObject {
        val keyPair = Ed25519Keys.generate()
        return SignedObjects.create(
            type = "waste_report",
            version = 1,
            authorPublicKey = keyPair.publicKey,
            authorPrivateKey = keyPair.privateKey,
            createdAt = createdAt,
            payload = buildJsonObject { put("note", note) },
        )
    }

    /** Deux canaux unidirectionnels reliés en mémoire, comme un socket bidirectionnel. */
    private class Pipe {
        val a2b = PipedOutputStream()
        val bIn = PipedInputStream(a2b, 65536)
        val b2a = PipedOutputStream()
        val aIn = PipedInputStream(b2a, 65536)
    }

    @Test
    fun `deux appareils echangent leurs objets respectifs dans les deux sens`() {
        val storeA = SqliteSignedObjectStore(":memory:")
        val storeB = SqliteSignedObjectStore(":memory:")
        val cursorsA = SqliteSyncCursorStore(":memory:")
        val cursorsB = SqliteSyncCursorStore(":memory:")
        try {
            val objA = objetSigne("2026-09-21T09:00:00Z", "depuis A")
            val objB = objetSigne("2026-09-21T09:01:00Z", "depuis B")
            storeA.save(objA)
            storeB.save(objB)

            val deviceA = Base64Url.encode(Ed25519Keys.generate().publicKey)
            val deviceB = Base64Url.encode(Ed25519Keys.generate().publicKey)
            val pipe = Pipe()

            val sessionA = SyncSession(pipe.aIn, pipe.a2b, deviceA, listOf("waste_report"), storeA, cursorsA, "test")
            val sessionB = SyncSession(pipe.bIn, pipe.b2a, deviceB, listOf("waste_report"), storeB, cursorsB, "test")

            val threadA = Thread({ sessionA.run() }, "session-A")
            val threadB = Thread({ sessionB.run() }, "session-B")
            threadA.start()
            threadB.start()
            threadA.join(10_000)
            threadB.join(10_000)

            assertFalse(threadA.isAlive, "session A n'a pas terminé à temps")
            assertFalse(threadB.isAlive, "session B n'a pas terminé à temps")
            assertNotNull(storeB.findById(objA.id), "B doit avoir reçu l'objet de A")
            assertNotNull(storeA.findById(objB.id), "A doit avoir reçu l'objet de B")
            assertNotNull(cursorsA.getCursor(deviceB), "A doit avoir mis à jour son curseur pour B")
            assertNotNull(cursorsB.getCursor(deviceA), "B doit avoir mis à jour son curseur pour A")
        } finally {
            storeA.close()
            storeB.close()
            cursorsA.close()
            cursorsB.close()
        }
    }

    @Test
    fun `la pagination fonctionne quand un appareil a plus d objets que la limite`() {
        val storeA = SqliteSignedObjectStore(":memory:")
        val storeB = SqliteSignedObjectStore(":memory:")
        val cursorsA = SqliteSyncCursorStore(":memory:")
        val cursorsB = SqliteSyncCursorStore(":memory:")
        try {
            val objets = listOf(
                objetSigne("2026-09-21T09:00:00Z"),
                objetSigne("2026-09-21T09:01:00Z"),
                objetSigne("2026-09-21T09:02:00Z"),
            )
            objets.forEach { storeA.save(it) }

            val deviceA = Base64Url.encode(Ed25519Keys.generate().publicKey)
            val deviceB = Base64Url.encode(Ed25519Keys.generate().publicKey)
            val pipe = Pipe()

            // Limite basse côté A : force B à récupérer les 3 objets en plusieurs pages.
            val sessionA = SyncSession(pipe.aIn, pipe.a2b, deviceA, listOf("waste_report"), storeA, cursorsA, "test", requestLimit = 2)
            val sessionB = SyncSession(pipe.bIn, pipe.b2a, deviceB, listOf("waste_report"), storeB, cursorsB, "test", requestLimit = 200)

            val threadA = Thread({ sessionA.run() }, "session-A")
            val threadB = Thread({ sessionB.run() }, "session-B")
            threadA.start()
            threadB.start()
            threadA.join(10_000)
            threadB.join(10_000)

            assertFalse(threadA.isAlive)
            assertFalse(threadB.isAlive)
            assertEquals(3L, storeB.count())
            objets.forEach { assertNotNull(storeB.findById(it.id)) }
        } finally {
            storeA.close()
            storeB.close()
            cursorsA.close()
            cursorsB.close()
        }
    }

    @Test
    fun `un message JSON malforme provoque un error et la fermeture de la session`() {
        val store = SqliteSignedObjectStore(":memory:")
        val cursors = SqliteSyncCursorStore(":memory:")
        try {
            val toSession = PipedOutputStream()
            val sessionInput = PipedInputStream(toSession, 65536)
            val sessionOutput = PipedOutputStream()
            val fromSession = BufferedReader(InputStreamReader(PipedInputStream(sessionOutput, 65536), StandardCharsets.UTF_8))

            val session = SyncSession(sessionInput, sessionOutput, "b64u:device-sous-test", listOf("waste_report"), store, cursors, "test")
            val thread = Thread({ session.run() }, "session-sous-test")
            thread.start()

            fromSession.readLine() // le hello envoyé au démarrage, non pertinent ici

            toSession.write("{ceci n'est pas du JSON\n".toByteArray(StandardCharsets.UTF_8))
            toSession.flush()

            val errorLine = fromSession.readLine()
            thread.join(5_000)

            assertFalse(thread.isAlive, "la session doit se terminer après un message malformé")
            assertNotNull(errorLine)
            val decoded = SyncMessageCodec.decode(errorLine)
            assertTrue(decoded is SyncError)
            assertEquals("malformed_message", (decoded as SyncError).code)
        } finally {
            store.close()
            cursors.close()
        }
    }

    @Test
    fun `un hello avec une version de protocole incompatible provoque un error et la fermeture`() {
        val store = SqliteSignedObjectStore(":memory:")
        val cursors = SqliteSyncCursorStore(":memory:")
        try {
            val toSession = PipedOutputStream()
            val sessionInput = PipedInputStream(toSession, 65536)
            val sessionOutput = PipedOutputStream()
            val fromSession = BufferedReader(InputStreamReader(PipedInputStream(sessionOutput, 65536), StandardCharsets.UTF_8))

            val session = SyncSession(sessionInput, sessionOutput, "b64u:device-sous-test", listOf("waste_report"), store, cursors, "test")
            val thread = Thread({ session.run() }, "session-sous-test")
            thread.start()

            fromSession.readLine() // le hello envoyé au démarrage

            val helloIncompatible = SyncMessageCodec.encode(
                Hello(protocolVersion = 99, devicePubkey = "b64u:pair-incompatible", supportedTypes = emptyList()),
            )
            toSession.write((helloIncompatible + "\n").toByteArray(StandardCharsets.UTF_8))
            toSession.flush()

            val errorLine = fromSession.readLine()
            thread.join(5_000)

            assertFalse(thread.isAlive)
            assertNotNull(errorLine)
            val decoded = SyncMessageCodec.decode(errorLine)
            assertTrue(decoded is SyncError)
            assertEquals("unsupported_version", (decoded as SyncError).code)
        } finally {
            store.close()
            cursors.close()
        }
    }
}
