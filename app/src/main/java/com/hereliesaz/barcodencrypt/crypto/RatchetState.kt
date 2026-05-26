package com.hereliesaz.barcodencrypt.crypto

/**
 * Immutable snapshot of one side of the v6 ratchet, scoped to a contact.
 *
 * The symmetric path is bidirectional: each device owns an independent *sending* chain
 * seeded from its own random [sendSalt], and tracks the peer's *receiving* chain seeded
 * from the [recvSalt] it learns from the first inbound message. Because the two salts
 * differ, the two directions never collide on a message key even though both start at
 * message number 0.
 *
 * - `sendSalt`         our per-contact Argon2 salt; transmitted in every outbound header
 * - `cks`              our sending chain key (HKDF of `Argon2(barcode, sendSalt)`)
 * - `ns`               messages sent under the current sending chain
 * - `recvSalt`         the peer's Argon2 salt, learned from inbound headers (null until
 *                      the first message is received)
 * - `ckr`              the peer's receiving chain key (null until `recvSalt` is known)
 * - `nr`               messages received under the current receiving chain
 * - `skipped`          bounded map of `(dhPublic, messageNumber) → mk` for messages we
 *                      advanced past without seeing yet
 * - `rk`               root key (used by the optional DH ratchet path)
 * - `dhSelfPriv/Pub`   our current X25519 ephemeral keypair (null until the DH ratchet
 *                      is engaged via [RatchetEngine.startSession])
 * - `dhRemotePub`      the latest X25519 public key we have received from the peer
 * - `pn`               messages sent under the *previous* sending chain before the last
 *                      DH ratchet step
 */
data class RatchetState(
    val sendSalt: ByteArray,
    val cks: ByteArray?,
    val ns: Int,
    val recvSalt: ByteArray?,
    val ckr: ByteArray?,
    val nr: Int,
    val skipped: Map<SkippedKeyId, ByteArray>,
    val rk: ByteArray,
    val dhSelfPriv: ByteArray?,
    val dhSelfPub: ByteArray?,
    val dhRemotePub: ByteArray?,
    val pn: Int,
) {
    init {
        require(rk.size == 32) { "rk must be 32 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetState) return false
        return sendSalt.contentEquals(other.sendSalt) &&
            nullableContentEquals(cks, other.cks) &&
            ns == other.ns &&
            nullableContentEquals(recvSalt, other.recvSalt) &&
            nullableContentEquals(ckr, other.ckr) &&
            nr == other.nr &&
            skippedMapsEqual(skipped, other.skipped) &&
            rk.contentEquals(other.rk) &&
            nullableContentEquals(dhSelfPriv, other.dhSelfPriv) &&
            nullableContentEquals(dhSelfPub, other.dhSelfPub) &&
            nullableContentEquals(dhRemotePub, other.dhRemotePub) &&
            pn == other.pn
    }

    override fun hashCode(): Int {
        var r = sendSalt.contentHashCode()
        r = 31 * r + (cks?.contentHashCode() ?: 0)
        r = 31 * r + ns
        r = 31 * r + (recvSalt?.contentHashCode() ?: 0)
        r = 31 * r + (ckr?.contentHashCode() ?: 0)
        r = 31 * r + nr
        r = 31 * r + skippedMapHashCode(skipped)
        r = 31 * r + rk.contentHashCode()
        r = 31 * r + (dhSelfPriv?.contentHashCode() ?: 0)
        r = 31 * r + (dhSelfPub?.contentHashCode() ?: 0)
        r = 31 * r + (dhRemotePub?.contentHashCode() ?: 0)
        r = 31 * r + pn
        return r
    }

    private fun nullableContentEquals(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a === b else a.contentEquals(b)

    private fun skippedMapsEqual(
        a: Map<SkippedKeyId, ByteArray>,
        b: Map<SkippedKeyId, ByteArray>,
    ): Boolean {
        if (a.size != b.size) return false
        return a.all { (key, value) ->
            val other = b[key]
            other != null && value.contentEquals(other)
        }
    }

    private fun skippedMapHashCode(map: Map<SkippedKeyId, ByteArray>): Int {
        var result = 0
        for ((key, value) in map) {
            result += 31 * key.hashCode() + value.contentHashCode()
        }
        return result
    }
}

data class SkippedKeyId(val dhPublic: ByteArray, val n: Int) {
    override fun equals(other: Any?): Boolean =
        other is SkippedKeyId && n == other.n && dhPublic.contentEquals(other.dhPublic)

    override fun hashCode(): Int = 31 * dhPublic.contentHashCode() + n
}
