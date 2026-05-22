package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted snapshot of a contact's [com.hereliesaz.barcodencrypt.crypto.RatchetState],
 * along with the per-contact Argon2id salt used to bootstrap that state.
 *
 * `skippedJson` is a JSON object keyed by `base64(dhPublic):n` → `base64(mk)`. The
 * map is bounded to [com.hereliesaz.barcodencrypt.crypto.RatchetEngine.SKIP_WINDOW]
 * entries; eldest skipped keys are evicted on overflow.
 */
@Entity(tableName = "ratchet_state")
data class RatchetStateEntity(
    @PrimaryKey val contactLookupKey: String,
    val salt: ByteArray,
    val rk: ByteArray,
    val cks: ByteArray?,
    val ckr: ByteArray?,
    val dhSelfPriv: ByteArray?,
    val dhSelfPub: ByteArray?,
    val dhRemotePub: ByteArray?,
    val ns: Int,
    val nr: Int,
    val pn: Int,
    val skippedJson: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetStateEntity) return false
        return contactLookupKey == other.contactLookupKey &&
            salt.contentEquals(other.salt) &&
            rk.contentEquals(other.rk) &&
            (cks?.contentEquals(other.cks ?: ByteArray(0)) == (other.cks != null || cks == null)) &&
            ns == other.ns && nr == other.nr && pn == other.pn &&
            skippedJson == other.skippedJson
    }

    override fun hashCode(): Int {
        var r = contactLookupKey.hashCode()
        r = 31 * r + salt.contentHashCode()
        r = 31 * r + rk.contentHashCode()
        r = 31 * r + (cks?.contentHashCode() ?: 0)
        r = 31 * r + ns + nr + pn
        return r
    }
}
