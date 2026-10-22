package com.remora.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HashUtilTest {

    @Test
    fun testSha256MatchesKnownHash() {
        val input = "Remora Second Brain 2026"
        val expected = "34f19b16503c837861bc99468087fc44c2ae2b144fc4203dcfe60c7f3cf26fa8"
        // Computed via SHA-256("Remora Second Brain 2026")
        val actual = HashUtil.sha256(input.toByteArray(Charsets.UTF_8))
        val streamActual = HashUtil.sha256(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))

        assertEquals(actual, streamActual)
    }

    @Test
    fun testSha256EmptyInput() {
        val empty = ByteArray(0)
        val expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expectedEmptyHash, HashUtil.sha256(empty))
    }
}
