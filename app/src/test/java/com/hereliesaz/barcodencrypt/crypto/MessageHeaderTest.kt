package com.hereliesaz.barcodencrypt.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageHeaderTest {

    private val baseHeader = MessageHeader(
        contactIdHash = ByteArray(16) { 1 },
        dhPublic = ByteArray(32) { 2 },
        previousChainMessages = 5,
        messageNumber = 7,
        ttlMs = 10_000L,
        openCountMax = 3,
        timestampMs = 1_716_300_000_000L,
        nonce = ByteArray(12) { 9 },
        dhRatchetStep = false,
    )

    @Test
    fun `round trip without TTL or openCount`() {
        val h = baseHeader.copy(ttlMs = null, openCountMax = null)
        val bytes = h.encode()
        assertEquals(82, bytes.size)
        assertEquals(h, MessageHeader.decode(bytes))
    }

    @Test
    fun `round trip with TTL and openCount`() {
        val bytes = baseHeader.encode()
        assertEquals(94, bytes.size)
        assertEquals(baseHeader, MessageHeader.decode(bytes))
    }

    @Test
    fun `round trip with dhRatchetStep flag`() {
        val h = baseHeader.copy(dhRatchetStep = true, ttlMs = null, openCountMax = null)
        val decoded = MessageHeader.decode(h.encode())
        assertEquals(h, decoded)
    }

    @Test
    fun `decode rejects wrong magic`() {
        val bytes = baseHeader.encode()
        bytes[0] = 'X'.code.toByte()
        assertNotNull(runCatching { MessageHeader.decode(bytes) }.exceptionOrNull())
    }

    @Test
    fun `decode rejects wrong version`() {
        val bytes = baseHeader.encode()
        bytes[4] = 0x04
        assertNotNull(runCatching { MessageHeader.decode(bytes) }.exceptionOrNull())
    }

    @Test
    fun `decode rejects too-short input`() {
        val short = ByteArray(10)
        assertNotNull(runCatching { MessageHeader.decode(short) }.exceptionOrNull())
    }

    @Test
    fun `init rejects wrong-size contactIdHash`() {
        assertNotNull(
            runCatching {
                MessageHeader(
                    contactIdHash = ByteArray(8),
                    dhPublic = baseHeader.dhPublic,
                    previousChainMessages = 0,
                    messageNumber = 0,
                    ttlMs = null,
                    openCountMax = null,
                    timestampMs = 0L,
                    nonce = baseHeader.nonce,
                    dhRatchetStep = false,
                )
            }.exceptionOrNull(),
        )
    }
}
