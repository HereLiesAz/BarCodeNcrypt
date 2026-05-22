package com.hereliesaz.barcodencrypt.crypto

/**
 * Immutable snapshot of one side of a Signal-style double ratchet, scoped to a contact.
 *
 * Field naming matches the Signal spec:
 * - `rk`              root key
 * - `cks`             sending chain key
 * - `ckr`             receiving chain key
 * - `dhSelfPriv/Pub`  our current X25519 ephemeral keypair (null until the DH ratchet
 *                     is engaged via [RatchetEngine.startSession])
 * - `dhRemotePub`     the latest X25519 public key we have received from the peer
 * - `ns`              messages sent under the current sending chain
 * - `nr`              messages received under the current receiving chain
 * - `pn`              messages that were sent under the *previous* sending chain
 *                     before the last DH ratchet step (needed to derive skipped keys
 *                     for in-flight messages from the prior chain)
 * - `skipped`         bounded map of `(dhPublic, messageNumber) → mk` for messages we
 *                     advanced past without seeing yet
 */
data class RatchetState(
    val rk: ByteArray,
    val cks: ByteArray?,
    val ckr: ByteArray?,
    val dhSelfPriv: ByteArray?,
    val dhSelfPub: ByteArray?,
    val dhRemotePub: ByteArray?,
    val ns: Int,
    val nr: Int,
    val pn: Int,
    val skipped: Map<SkippedKeyId, ByteArray>,
) {
    init {
        require(rk.size == 32) { "rk must be 32 bytes" }
    }
}

data class SkippedKeyId(val dhPublic: ByteArray, val n: Int) {
    override fun equals(other: Any?): Boolean =
        other is SkippedKeyId && n == other.n && dhPublic.contentEquals(other.dhPublic)

    override fun hashCode(): Int = 31 * dhPublic.contentHashCode() + n
}
