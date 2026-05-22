package com.hereliesaz.barcodencrypt.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HashingTest {
    @Test
    fun `sha256 of empty string matches known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hashing.sha256("")
        )
    }

    @Test
    fun `sha256 of abc matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256("abc")
        )
    }
}
