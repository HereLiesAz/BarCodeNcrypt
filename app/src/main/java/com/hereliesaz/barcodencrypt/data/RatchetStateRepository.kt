package com.hereliesaz.barcodencrypt.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hereliesaz.barcodencrypt.crypto.RatchetState
import com.hereliesaz.barcodencrypt.crypto.SkippedKeyId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Room persistence and the pure [RatchetState] value type.
 *
 * The `skipped` map is serialized as JSON `{"base64(dhPub):n": "base64(mk)", ...}` so
 * Room doesn't need a TypeConverter for the nested map structure.
 */
@Singleton
class RatchetStateRepository @Inject constructor(
    private val dao: RatchetStateDao,
) {
    private val gson = Gson()
    private val skippedMapType = object : TypeToken<Map<String, String>>() {}.type
    private val base64Flags = Base64.NO_WRAP

    /** Loads `(salt, state)` for [contactKey] if any; null if no state exists yet. */
    suspend fun load(contactKey: String): Pair<ByteArray, RatchetState>? {
        val e = dao.get(contactKey) ?: return null
        val skippedRaw: Map<String, String> = gson.fromJson(e.skippedJson, skippedMapType) ?: emptyMap()
        val skipped = skippedRaw.entries.associate { (k, v) ->
            val idx = k.lastIndexOf(':')
            val dh = Base64.decode(k.substring(0, idx), base64Flags)
            val n = k.substring(idx + 1).toInt()
            SkippedKeyId(dh, n) to Base64.decode(v, base64Flags)
        }
        val state = RatchetState(
            rk = e.rk,
            cks = e.cks,
            ckr = e.ckr,
            dhSelfPriv = e.dhSelfPriv,
            dhSelfPub = e.dhSelfPub,
            dhRemotePub = e.dhRemotePub,
            ns = e.ns,
            nr = e.nr,
            pn = e.pn,
            skipped = skipped,
        )
        return e.salt to state
    }

    suspend fun save(contactKey: String, salt: ByteArray, state: RatchetState) {
        val skippedRaw = state.skipped.entries.associate { (id, mk) ->
            "${Base64.encodeToString(id.dhPublic, base64Flags)}:${id.n}" to
                Base64.encodeToString(mk, base64Flags)
        }
        dao.upsert(
            RatchetStateEntity(
                contactLookupKey = contactKey,
                salt = salt,
                rk = state.rk,
                cks = state.cks,
                ckr = state.ckr,
                dhSelfPriv = state.dhSelfPriv,
                dhSelfPub = state.dhSelfPub,
                dhRemotePub = state.dhRemotePub,
                ns = state.ns,
                nr = state.nr,
                pn = state.pn,
                skippedJson = gson.toJson(skippedRaw),
            )
        )
    }

    suspend fun delete(contactKey: String) {
        dao.delete(contactKey)
    }
}
