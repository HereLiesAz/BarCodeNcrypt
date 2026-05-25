package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted snapshot of a contact's [com.hereliesaz.barcodencrypt.crypto.RatchetState].
 *
 * `sendSalt` is our own per-contact Argon2 salt; `recvSalt` is the peer's salt learned
 * from inbound headers (null until the first message arrives). `skippedJson` is a JSON
 * object keyed by `base64(dhPublic):n` → `base64(mk)`, bounded to
 * [com.hereliesaz.barcodencrypt.crypto.RatchetEngine.SKIP_WINDOW] entries.
 */
@Entity(tableName = "ratchet_state")
data class RatchetStateEntity(
    @PrimaryKey val contactLookupKey: String,
    val sendSalt: ByteArray,
    val cks: ByteArray?,
    val ns: Int,
    val recvSalt: ByteArray?,
    val ckr: ByteArray?,
    val nr: Int,
    val skippedJson: String,
    val rk: ByteArray,
    val dhSelfPriv: ByteArray?,
    val dhSelfPub: ByteArray?,
    val dhRemotePub: ByteArray?,
    val pn: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetStateEntity) return false
        return contactLookupKey == other.contactLookupKey &&
            sendSalt.contentEquals(other.sendSalt) &&
            nullableContentEquals(cks, other.cks) &&
            ns == other.ns &&
            nullableContentEquals(recvSalt, other.recvSalt) &&
            nullableContentEquals(ckr, other.ckr) &&
            nr == other.nr &&
            skippedJson == other.skippedJson &&
            rk.contentEquals(other.rk) &&
            nullableContentEquals(dhSelfPriv, other.dhSelfPriv) &&
            nullableContentEquals(dhSelfPub, other.dhSelfPub) &&
            nullableContentEquals(dhRemotePub, other.dhRemotePub) &&
            pn == other.pn
    }

    override fun hashCode(): Int {
        var r = contactLookupKey.hashCode()
        r = 31 * r + sendSalt.contentHashCode()
        r = 31 * r + (cks?.contentHashCode() ?: 0)
        r = 31 * r + ns
        r = 31 * r + (recvSalt?.contentHashCode() ?: 0)
        r = 31 * r + (ckr?.contentHashCode() ?: 0)
        r = 31 * r + nr
        r = 31 * r + skippedJson.hashCode()
        r = 31 * r + rk.contentHashCode()
        r = 31 * r + (dhSelfPriv?.contentHashCode() ?: 0)
        r = 31 * r + (dhSelfPub?.contentHashCode() ?: 0)
        r = 31 * r + (dhRemotePub?.contentHashCode() ?: 0)
        r = 31 * r + pn
        return r
    }

    private fun nullableContentEquals(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a === b else a.contentEquals(b)
}
