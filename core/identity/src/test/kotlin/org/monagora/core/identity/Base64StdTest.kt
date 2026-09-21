package org.monagora.core.identity

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base64StdTest {

    @Test
    fun `encoder puis decoder redonne les octets d origine`() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 127, -128)

        val decoded = Base64Std.decode(Base64Std.encode(original))

        assertTrue(original.contentEquals(decoded))
    }

    @Test
    fun `encode prefixe avec b64 et pas b64u`() {
        val encoded = Base64Std.encode(byteArrayOf(1, 2, 3))

        assertTrue(encoded.startsWith("b64:"))
    }

    @Test
    fun `decode rejette une valeur avec le prefixe b64u`() {
        val cleEncodee = Base64Url.encode(byteArrayOf(1, 2, 3))

        assertFailsWith<IllegalArgumentException> {
            Base64Std.decode(cleEncodee)
        }
    }

    @Test
    fun `decode rejette un contenu qui n est pas du base64 valide`() {
        assertFailsWith<IllegalArgumentException> {
            Base64Std.decode("b64:!!!pas-du-base64!!!")
        }
    }
}
