package org.monagora.core.identity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class Ed25519KeysTest {

    @Test
    fun `signature valide est acceptee`() {
        val keyPair = Ed25519Keys.generate()
        val message = "vote:2026-09-21:oui".toByteArray()

        val signature = Ed25519Keys.sign(keyPair.privateKey, message)

        assertTrue(Ed25519Keys.verify(keyPair.publicKey, message, signature))
    }

    @Test
    fun `signature rejetee si le message est modifie apres signature`() {
        val keyPair = Ed25519Keys.generate()
        val signature = Ed25519Keys.sign(keyPair.privateKey, "montant:10".toByteArray())

        val accepted = Ed25519Keys.verify(keyPair.publicKey, "montant:1000".toByteArray(), signature)

        assertFalse(accepted)
    }

    @Test
    fun `signature rejetee si signee par une autre cle`() {
        val message = "signalement:poubelle_pleine".toByteArray()
        val signature = Ed25519Keys.sign(Ed25519Keys.generate().privateKey, message)
        val autreClePublique = Ed25519Keys.generate().publicKey

        assertFalse(Ed25519Keys.verify(autreClePublique, message, signature))
    }

    @Test
    fun `verification rejetee sans exception si la cle publique a une taille invalide`() {
        val message = "test".toByteArray()
        val signature = Ed25519Keys.sign(Ed25519Keys.generate().privateKey, message)

        val accepted = Ed25519Keys.verify(ByteArray(4), message, signature)

        assertFalse(accepted)
    }

    @Test
    fun `verification rejetee sans exception si la signature a une taille invalide`() {
        val keyPair = Ed25519Keys.generate()
        val message = "test".toByteArray()

        val accepted = Ed25519Keys.verify(keyPair.publicKey, message, ByteArray(3))

        assertFalse(accepted)
    }

    @Test
    fun `signature rejetee si la cle privee a une taille invalide`() {
        assertFailsWith<IllegalArgumentException> {
            Ed25519Keys.sign(ByteArray(4), "test".toByteArray())
        }
    }

    @Test
    fun `deux paires de cles generees sont distinctes`() {
        val a = Ed25519Keys.generate()
        val b = Ed25519Keys.generate()

        assertFalse(a.publicKey.contentEquals(b.publicKey))
        assertFalse(a.privateKey.contentEquals(b.privateKey))
    }
}
