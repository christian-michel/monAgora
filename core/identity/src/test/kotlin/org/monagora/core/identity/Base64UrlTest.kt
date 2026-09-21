package org.monagora.core.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base64UrlTest {

    @Test
    fun `encoder puis decoder redonne les octets d origine`() {
        val original = Ed25519Keys.generate().publicKey

        val decoded = Base64Url.decode(Base64Url.encode(original))

        assertTrue(original.contentEquals(decoded))
    }

    @Test
    fun `encode prefixe toujours avec b64u`() {
        val encoded = Base64Url.encode(byteArrayOf(1, 2, 3))

        assertTrue(encoded.startsWith("b64u:"))
    }

    @Test
    fun `decode rejette une valeur sans prefixe b64u`() {
        assertFailsWith<IllegalArgumentException> {
            Base64Url.decode("SGVsbG8")
        }
    }

    @Test
    fun `decode rejette un contenu qui n est pas du base64 valide`() {
        assertFailsWith<IllegalArgumentException> {
            Base64Url.decode("b64u:!!!pas-du-base64!!!")
        }
    }

    @Test
    fun `encode n ajoute pas de padding`() {
        val encoded = Base64Url.encode(byteArrayOf(1))

        assertEquals(false, encoded.contains("="))
    }
}
