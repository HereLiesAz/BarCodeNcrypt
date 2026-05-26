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

    /** Loads the [RatchetState] for [contactKey] if any; null if no state exists yet. */
    suspend fun load(contactKey: String): RatchetState? {
        val e = dao.get(contactKey) ?: return null
        val skippedRaw: Map<String, String> =
            runCatching { gson.fromJson<Map<String, String>>(e.skippedJson, skippedMapType) }
                .getOrNull() ?: emptyMap()
        val skipped = skippedRaw.entries.mapNotNull { (k, v) ->
            parseSkippedEntry(k, v)
        }.toMap()
        return RatchetState(
            sendSalt = e.sendSalt,
            cks = e.cks,
            ns = e.ns,
            recvSalt = e.recvSalt,
            ckr = e.ckr,
            nr = e.nr,
            skipped = skipped,
            rk = e.rk,
            dhSelfPriv = e.dhSelfPriv,
            dhSelfPub = e.dhSelfPub,
            dhRemotePub = e.dhRemotePub,
            pn = e.pn,
        )
    }

    suspend fun save(contactKey: String, state: RatchetState) {
        val skippedRaw = state.skipped.entries.associate { (id, mk) ->
            "${Base64.encodeToString(id.dhPublic, base64Flags)}:${id.n}" to
                Base64.encodeToString(mk, base64Flags)
        }
        dao.upsert(
            RatchetStateEntity(
                contactLookupKey = contactKey,
                sendSalt = state.sendSalt,
                cks = state.cks,
                ns = state.ns,
                recvSalt = state.recvSalt,
                ckr = state.ckr,
                nr = state.nr,
                skippedJson = gson.toJson(skippedRaw),
                rk = state.rk,
                dhSelfPriv = state.dhSelfPriv,
                dhSelfPub = state.dhSelfPub,
                dhRemotePub = state.dhRemotePub,
                pn = state.pn,
            )
        )
    }

    suspend fun delete(contactKey: String) {
        dao.delete(contactKey)
    }

    private fun parseSkippedEntry(key: String, value: String): Pair<SkippedKeyId, ByteArray>? {
        val idx = key.lastIndexOf(':')
        if (idx <= 0 || idx == key.length - 1) return null
        val n = key.substring(idx + 1).toIntOrNull() ?: return null
        return runCatching {
            val dh = Base64.decode(key.substring(0, idx), base64Flags)
            val mk = Base64.decode(value, base64Flags)
            SkippedKeyId(dh, n) to mk
        }.getOrNull()
    }
}
