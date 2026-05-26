package com.hereliesaz.barcodencrypt.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure v6 ratchet. Argon2 is bypassed: `key(seed)` stands in for the
 * 32-byte `Argon2(barcode, salt)` output, which both peers compute identically from the
 * shared barcode and the salt carried in the header.
 */
class RatchetEngineTest {

    private val contactIdHash = ByteArray(16) { 7 }

    private fun key(seed: Int) = ByteArray(32) { seed.toByte() }
    private fun salt(seed: Int) = ByteArray(16) { (seed + it).toByte() }

    @Test
    fun `cross-device round trip`() {
        val aliceKey = key(1)
        val aliceSalt = salt(1)
        var alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        var bob = RatchetEngine.bootstrapSending(key(2), salt(2))

        val (alice2, envelope) = RatchetEngine.encrypt(
            alice, contactIdHash, "hello".toByteArray(), null, null,
        )
        alice = alice2

        assertTrue(envelope.startsWith(MessageEnvelope.PREFIX))

        // Bob learns Alice's salt from the header and seeds the receiving chain with the
        // same Argon2 key. This is the case the old v5 code could never decrypt.
        val header = MessageHeader.decode(MessageEnvelope.decode(envelope)!!)
        assertArrayEquals(aliceSalt, header.salt)
        assertTrue(RatchetEngine.needsReceivingBootstrap(bob, header.salt))
        bob = RatchetEngine.bootstrapReceiving(bob, aliceKey, header.salt)

        val result = RatchetEngine.decrypt(bob, envelope)
        assertNotNull(result)
        assertEquals("hello", String(result!!.second))
    }

    @Test
    fun `bidirectional messaging`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        val bobKey = key(2); val bobSalt = salt(2)
        var alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        var bob = RatchetEngine.bootstrapSending(bobKey, bobSalt)

        // Alice -> Bob
        val (a2, e1) = RatchetEngine.encrypt(alice, contactIdHash, "hi bob".toByteArray(), null, null)
        alice = a2
        bob = RatchetEngine.bootstrapReceiving(bob, aliceKey, aliceSalt)
        val r1 = RatchetEngine.decrypt(bob, e1)!!
        bob = r1.first
        assertEquals("hi bob", String(r1.second))

        // Bob -> Alice
        val (b2, e2) = RatchetEngine.encrypt(bob, contactIdHash, "hi alice".toByteArray(), null, null)
        bob = b2
        alice = RatchetEngine.bootstrapReceiving(alice, bobKey, bobSalt)
        val r2 = RatchetEngine.decrypt(alice, e2)!!
        alice = r2.first
        assertEquals("hi alice", String(r2.second))
    }

    @Test
    fun `out of order delivery within window`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        var alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        val envelopes = (0..2).map {
            val (next, env) = RatchetEngine.encrypt(alice, contactIdHash, "m$it".toByteArray(), null, null)
            alice = next
            env
        }

        var bob = RatchetEngine.bootstrapReceiving(
            RatchetEngine.bootstrapSending(key(2), salt(2)), aliceKey, aliceSalt,
        )

        // Receive 2, then 0, then 1.
        val r2 = RatchetEngine.decrypt(bob, envelopes[2])!!; bob = r2.first
        val r0 = RatchetEngine.decrypt(bob, envelopes[0])!!; bob = r0.first
        val r1 = RatchetEngine.decrypt(bob, envelopes[1])!!; bob = r1.first
        assertEquals("m2", String(r2.second))
        assertEquals("m0", String(r0.second))
        assertEquals("m1", String(r1.second))
    }

    @Test
    fun `replay of a consumed message fails`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        var alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        val (a2, env) = RatchetEngine.encrypt(alice, contactIdHash, "once".toByteArray(), null, null)
        alice = a2
        var bob = RatchetEngine.bootstrapReceiving(
            RatchetEngine.bootstrapSending(key(2), salt(2)), aliceKey, aliceSalt,
        )
        val first = RatchetEngine.decrypt(bob, env)!!
        bob = first.first
        assertEquals("once", String(first.second))
        assertNull(RatchetEngine.decrypt(bob, env))
    }

    @Test
    fun `tampered header fails authentication`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        val alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        val (_, env) = RatchetEngine.encrypt(alice, contactIdHash, "secret".toByteArray(), null, null)

        val payload = MessageEnvelope.decode(env)!!
        // Flip a byte inside contactIdHash (offset 6: magic[4]+version[1]+flags[1]).
        payload[6] = (payload[6] + 1).toByte()
        val tampered = MessageEnvelope.encode(payload)

        var bob = RatchetEngine.bootstrapReceiving(
            RatchetEngine.bootstrapSending(key(2), salt(2)), aliceKey, aliceSalt,
        )
        assertNull(RatchetEngine.decrypt(bob, tampered))
    }

    @Test
    fun `expired ttl is rejected`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        val alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        val (_, env) = RatchetEngine.encrypt(alice, contactIdHash, "fleeting".toByteArray(), 1L, null)

        Thread.sleep(20)
        val bob = RatchetEngine.bootstrapReceiving(
            RatchetEngine.bootstrapSending(key(2), salt(2)), aliceKey, aliceSalt,
        )
        assertNull(RatchetEngine.decrypt(bob, env))
    }

    @Test
    fun `wrong barcode key cannot decrypt`() {
        val aliceKey = key(1); val aliceSalt = salt(1)
        val alice = RatchetEngine.bootstrapSending(aliceKey, aliceSalt)
        val (_, env) = RatchetEngine.encrypt(alice, contactIdHash, "hello".toByteArray(), null, null)

        // Bob seeds the receiving chain from the wrong barcode -> wrong key.
        val bob = RatchetEngine.bootstrapReceiving(
            RatchetEngine.bootstrapSending(key(2), salt(2)), key(99), aliceSalt,
        )
        assertNull(RatchetEngine.decrypt(bob, env))
    }

    @Test
    fun `needsReceivingBootstrap tracks salt changes`() {
        val state = RatchetEngine.bootstrapSending(key(1), salt(1))
        assertTrue(RatchetEngine.needsReceivingBootstrap(state, salt(5)))
        val seeded = RatchetEngine.bootstrapReceiving(state, key(5), salt(5))
        assertFalse(RatchetEngine.needsReceivingBootstrap(seeded, salt(5)))
        assertTrue(RatchetEngine.needsReceivingBootstrap(seeded, salt(6)))
    }
}
