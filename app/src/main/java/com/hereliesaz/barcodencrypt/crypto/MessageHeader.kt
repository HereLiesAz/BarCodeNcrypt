package com.hereliesaz.barcodencrypt.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary header carried alongside every v5 ciphertext.
 *
 * Wire layout (big-endian, see `docs/crypto/wire-format.md`):
 *
 * ```
 * magic[4] = "BCE5"
 * version[1] = 0x05
 * flags[1]   = bit 0: has_ttl, bit 1: has_open_count, bit 2: dh_ratchet_step
 * contactIdHash[16]
 * dhPublic[32]
 * previousChainMessages[4]
 * messageNumber[4]
 * ttlMs[8]              (present iff flags.0)
 * openCountMax[4]       (present iff flags.1)
 * timestampMs[8]
 * nonce[12]
 * ```
 *
 * The whole encoded header (magic..nonce, inclusive) is fed to AES-GCM as
 * `associatedData`, so flipping any bit there fails the GCM tag.
 */
data class MessageHeader(
    val contactIdHash: ByteArray,
    val dhPublic: ByteArray,
    val previousChainMessages: Int,
    val messageNumber: Int,
    val ttlMs: Long?,
    val openCountMax: Int?,
    val timestampMs: Long,
    val nonce: ByteArray,
    val dhRatchetStep: Boolean,
) {
    init {
        require(contactIdHash.size == CONTACT_ID_HASH_SIZE)
        require(dhPublic.size == DH_PUBLIC_SIZE)
        require(nonce.size == NONCE_SIZE)
    }

    fun encode(): ByteArray {
        var size = HEADER_FIXED_SIZE
        if (ttlMs != null) size += TTL_SIZE
        if (openCountMax != null) size += OPEN_COUNT_SIZE
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(VERSION)
        var flags = 0
        if (ttlMs != null) flags = flags or FLAG_HAS_TTL
        if (openCountMax != null) flags = flags or FLAG_HAS_OPEN_COUNT
        if (dhRatchetStep) flags = flags or FLAG_DH_RATCHET_STEP
        buf.put(flags.toByte())
        buf.put(contactIdHash)
        buf.put(dhPublic)
        buf.putInt(previousChainMessages)
        buf.putInt(messageNumber)
        if (ttlMs != null) buf.putLong(ttlMs)
        if (openCountMax != null) buf.putInt(openCountMax)
        buf.putLong(timestampMs)
        buf.put(nonce)
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
            dhPublic.contentEquals(other.dhPublic) &&
            previousChainMessages == other.previousChainMessages &&
            messageNumber == other.messageNumber &&
            ttlMs == other.ttlMs &&
            openCountMax == other.openCountMax &&
            timestampMs == other.timestampMs &&
            nonce.contentEquals(other.nonce) &&
            dhRatchetStep == other.dhRatchetStep
    }

    override fun hashCode(): Int {
        var r = contactIdHash.contentHashCode()
        r = 31 * r + dhPublic.contentHashCode()
        r = 31 * r + previousChainMessages
        r = 31 * r + messageNumber
        r = 31 * r + (ttlMs?.hashCode() ?: 0)
        r = 31 * r + (openCountMax ?: 0)
        r = 31 * r + timestampMs.hashCode()
        r = 31 * r + nonce.contentHashCode()
        r = 31 * r + dhRatchetStep.hashCode()
        return r
    }

    companion object {
        private val MAGIC = "BCE5".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 0x05

        private const val MAGIC_SIZE = 4
        private const val VERSION_SIZE = 1
        private const val FLAGS_SIZE = 1
        const val CONTACT_ID_HASH_SIZE = 16
        const val DH_PUBLIC_SIZE = 32
        private const val PREV_N_SIZE = 4
        private const val MESSAGE_NUMBER_SIZE = 4
        private const val TIMESTAMP_SIZE = 8
        const val NONCE_SIZE = 12
        private const val TTL_SIZE = 8
        private const val OPEN_COUNT_SIZE = 4

        const val HEADER_FIXED_SIZE = MAGIC_SIZE + VERSION_SIZE + FLAGS_SIZE +
            CONTACT_ID_HASH_SIZE + DH_PUBLIC_SIZE + PREV_N_SIZE +
            MESSAGE_NUMBER_SIZE + TIMESTAMP_SIZE + NONCE_SIZE

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
            val contactIdHash = ByteArray(CONTACT_ID_HASH_SIZE).also { buf.get(it) }
            val dhPublic = ByteArray(DH_PUBLIC_SIZE).also { buf.get(it) }
            val prevN = buf.int
            val n = buf.int
            val ttl = if (hasTtl) buf.long else null
            val openMax = if (hasOpenCount) buf.int else null
            val timestamp = buf.long
            val nonce = ByteArray(NONCE_SIZE).also { buf.get(it) }
            return MessageHeader(
                contactIdHash = contactIdHash,
                dhPublic = dhPublic,
                previousChainMessages = prevN,
                messageNumber = n,
                ttlMs = ttl,
                openCountMax = openMax,
                timestampMs = timestamp,
                nonce = nonce,
                dhRatchetStep = dhStep,
            )
        }
    }
}
