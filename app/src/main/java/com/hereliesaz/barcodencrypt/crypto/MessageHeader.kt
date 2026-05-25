package com.hereliesaz.barcodencrypt.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary header carried alongside every v6 ciphertext.
 *
 * Wire layout (big-endian, see `docs/crypto/wire-format.md`):
 *
 * ```
 * magic[4] = "BCE6"
 * version[1] = 0x06
 * flags[1]   = bit 0: has_ttl, bit 1: has_open_count, bit 2: dh_ratchet_step
 * contactIdHash[16]
 * salt[16]                  the sender's per-contact Argon2 salt
 * dhPublic[32]
 * previousChainMessages[4]
 * messageNumber[4]
 * ttlMs[8]              (present iff flags.0)
 * openCountMax[4]       (present iff flags.1)
 * timestampMs[8]
 * ```
 *
 * The whole encoded header is fed to AES-GCM as `associatedData`, so flipping any bit
 * there fails the GCM tag. The 96-bit GCM IV is generated randomly by the AEAD and is
 * carried at the front of the ciphertext, not in this header.
 */
data class MessageHeader(
    val contactIdHash: ByteArray,
    val salt: ByteArray,
    val dhPublic: ByteArray,
    val previousChainMessages: Int,
    val messageNumber: Int,
    val ttlMs: Long?,
    val openCountMax: Int?,
    val timestampMs: Long,
    val dhRatchetStep: Boolean,
) {
    init {
        require(contactIdHash.size == CONTACT_ID_HASH_SIZE)
        require(salt.size == SALT_SIZE)
        require(dhPublic.size == DH_PUBLIC_SIZE)
    }

    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(encodedSize()).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(VERSION)
        var flags = 0
        if (ttlMs != null) flags = flags or FLAG_HAS_TTL
        if (openCountMax != null) flags = flags or FLAG_HAS_OPEN_COUNT
        if (dhRatchetStep) flags = flags or FLAG_DH_RATCHET_STEP
        buf.put(flags.toByte())
        buf.put(contactIdHash)
        buf.put(salt)
        buf.put(dhPublic)
        buf.putInt(previousChainMessages)
        buf.putInt(messageNumber)
        if (ttlMs != null) buf.putLong(ttlMs)
        if (openCountMax != null) buf.putInt(openCountMax)
        buf.putLong(timestampMs)
        return buf.array()
    }

    fun encodedSize(): Int {
        var size = HEADER_FIXED_SIZE
        if (ttlMs != null) size += TTL_SIZE
        if (openCountMax != null) size += OPEN_COUNT_SIZE
        return size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageHeader) return false
        return contactIdHash.contentEquals(other.contactIdHash) &&
            salt.contentEquals(other.salt) &&
            dhPublic.contentEquals(other.dhPublic) &&
            previousChainMessages == other.previousChainMessages &&
            messageNumber == other.messageNumber &&
            ttlMs == other.ttlMs &&
            openCountMax == other.openCountMax &&
            timestampMs == other.timestampMs &&
            dhRatchetStep == other.dhRatchetStep
    }

    override fun hashCode(): Int {
        var r = contactIdHash.contentHashCode()
        r = 31 * r + salt.contentHashCode()
        r = 31 * r + dhPublic.contentHashCode()
        r = 31 * r + previousChainMessages
        r = 31 * r + messageNumber
        r = 31 * r + (ttlMs?.hashCode() ?: 0)
        r = 31 * r + (openCountMax ?: 0)
        r = 31 * r + timestampMs.hashCode()
        r = 31 * r + dhRatchetStep.hashCode()
        return r
    }

    companion object {
        private val MAGIC = "BCE6".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 0x06

        private const val MAGIC_SIZE = 4
        private const val VERSION_SIZE = 1
        private const val FLAGS_SIZE = 1
        const val CONTACT_ID_HASH_SIZE = 16
        const val SALT_SIZE = 16
        const val DH_PUBLIC_SIZE = 32
        private const val PREV_N_SIZE = 4
        private const val MESSAGE_NUMBER_SIZE = 4
        private const val TIMESTAMP_SIZE = 8
        private const val TTL_SIZE = 8
        private const val OPEN_COUNT_SIZE = 4

        const val HEADER_FIXED_SIZE = MAGIC_SIZE + VERSION_SIZE + FLAGS_SIZE +
            CONTACT_ID_HASH_SIZE + SALT_SIZE + DH_PUBLIC_SIZE + PREV_N_SIZE +
            MESSAGE_NUMBER_SIZE + TIMESTAMP_SIZE

        const val FLAG_HAS_TTL = 0x1
        const val FLAG_HAS_OPEN_COUNT = 0x2
        const val FLAG_DH_RATCHET_STEP = 0x4

        fun decode(bytes: ByteArray): MessageHeader {
            require(bytes.size >= HEADER_FIXED_SIZE) { "header too short" }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC_SIZE).also { buf.get(it) }
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = buf.get()
            require(version == VERSION) { "bad version" }
            val flags = buf.get().toInt() and 0xFF
            val hasTtl = flags and FLAG_HAS_TTL != 0
            val hasOpenCount = flags and FLAG_HAS_OPEN_COUNT != 0
            val dhStep = flags and FLAG_DH_RATCHET_STEP != 0

            // Make sure the buffer is large enough for the optional fields the flags claim
            // are present, so the reads below can't run off the end.
            val required = HEADER_FIXED_SIZE +
                (if (hasTtl) TTL_SIZE else 0) +
                (if (hasOpenCount) OPEN_COUNT_SIZE else 0)
            require(bytes.size >= required) { "header truncated for declared flags" }

            return try {
                val contactIdHash = ByteArray(CONTACT_ID_HASH_SIZE).also { buf.get(it) }
                val salt = ByteArray(SALT_SIZE).also { buf.get(it) }
                val dhPublic = ByteArray(DH_PUBLIC_SIZE).also { buf.get(it) }
                val prevN = buf.int
                val n = buf.int
                val ttl = if (hasTtl) buf.long else null
                val openMax = if (hasOpenCount) buf.int else null
                val timestamp = buf.long
                MessageHeader(
                    contactIdHash = contactIdHash,
                    salt = salt,
                    dhPublic = dhPublic,
                    previousChainMessages = prevN,
                    messageNumber = n,
                    ttlMs = ttl,
                    openCountMax = openMax,
                    timestampMs = timestamp,
                    dhRatchetStep = dhStep,
                )
            } catch (e: BufferUnderflowException) {
                throw IllegalArgumentException("header truncated", e)
            }
        }
    }
}
