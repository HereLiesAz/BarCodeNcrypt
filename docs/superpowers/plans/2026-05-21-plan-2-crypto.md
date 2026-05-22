# Plan 2 — Crypto Rewrite (v5 Double Ratchet) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the v4 symmetric chain with a Signal-style Double Ratchet (v5) that delivers real forward secrecy, AEAD-authenticated headers, password-mixed key derivation via Argon2id, and out-of-order delivery within a bounded skip window. The detector matches what the encoder emits.

**Architecture:**
- **Bootstrap KDF.** `Argon2id(barcode_bytes ‖ password_bytes, perContactSalt, t=3, m=64MiB, p=2, len=32)` → `bootstrap_key`.
- **Symmetric ratchet.** Initial root key `rk₀ = HKDF(bootstrap_key, info="BCEv5_Root")`. Initial sending chain `cks₀ = HKDF(bootstrap_key, info="BCEv5_SendChain")`. Each encrypt advances the chain via `(cks_{n+1}, mk_n) = KDF_CK(cks_n)`.
- **Optional DH ratchet.** When a contact's `dhRemotePublicKey` is set (Plan 4 wires the QR key-exchange UI), every encrypt rotates the sender's X25519 keypair every DH-chain step; the receiver advances the root with `KDF_RK(rk, DH(sk_self, pk_remote))`.
- **AEAD.** AES-256-GCM via Tink `AesGcmJce`, with the canonical binary header bytes as `associatedData`. Tampering with the header fails the tag check.
- **Wire format.** `~BCEv5~<base64(binary_message)>` with the field layout in the spec §3.2.
- **No master persisted.** `EncryptedScriptLog` table and `masterKey` columns are removed. Skipped message keys are kept in a bounded per-contact map (≤ 1000 entries, eldest evicted) so that out-of-order delivery works.

**Tech stack additions:**
- `com.lambdapioneer.argon2kt:argon2kt:1.5.0` for Argon2id with prebuilt ABIs.
- Continued use of Tink `subtle.X25519` and `subtle.Hkdf`.

**Reading dependencies:**
- `docs/superpowers/specs/2026-05-21-remediation-design.md` §3.1–3.2.
- Marlinspike & Perrin, *The Double Ratchet Algorithm*, Signal documentation (2016) — for terminology.
- Plan 1 must be complete (Hilt on, EncryptionManager already instance-based).

---

## File structure

### Created
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetState.kt` — immutable value type holding `(dhSelf, dhRemote, rk, cks, ckr, ns, nr, pn, skipped)`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngine.kt` — pure functions: `bootstrap`, `encrypt`, `decrypt`, `dhRatchetStep`, `kdfRk`, `kdfCk`, `trySkipped`, `skipMessageKeys`. No Android dependencies; fully unit-testable.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/Argon2.kt` — thin wrapper over `argon2kt` to make the parameters explicit and unit-testable.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageHeader.kt` — binary header serialization (`encode`, `decode`); replaces the Gson MessageHeader in EncryptionManager.kt.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelope.kt` — wire-format encode/decode including the `~BCEv5~` prefix and base64.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateEntity.kt` — Room entity persisting `RatchetState` per contact.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateDao.kt`
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateRepository.kt`
- `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/Argon2Test.kt`
- `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageHeaderTest.kt`
- `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelopeTest.kt`
- `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngineTest.kt`
- `app/src/androidTest/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManagerInstrumentedTest.kt` — round-trip on emulator.

### Modified
- `gradle/libs.versions.toml` — add `argon2kt` version + library alias.
- `app/build.gradle.kts` — add `implementation(libs.argon2kt)`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManager.kt` — thin façade over `RatchetEngine`, no own crypto.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt` — add `RatchetStateEntity` to `entities`, bump `version`, add a destructive `MIGRATION_10_11` (drops `encrypted_script_log`, drops the v3 columns from `contacts`, creates `ratchet_state`).
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/Contact.kt` — remove the V3 ratchet fields (they move to `RatchetStateEntity`).
- `app/src/main/java/com/hereliesaz/barcodencrypt/util/MessageParser.kt` — match `~BCEv5~[A-Za-z0-9+/=_\-]+` (URL-safe base64 to accommodate longer headers in chat apps).

### Deleted
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptedScriptLogManager.kt`
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLog.kt`
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLogDao.kt`
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLogRepository.kt`

---

## Wire format (canonical reference)

```
                    Binary message layout (big-endian where multi-byte)
        ┌──────┬──────┬──────┬──────────────┬──────────┬────────┬─────┬────────┬──────────┬──────────┬────────┬────────────────┐
field   │ magi │ ver  │ flag │ contactIdHsh │ dhPublic │ prevN  │  n  │ ttlMs  │ openMax  │ timestmp │ nonce  │   ciphertext   │
size B  │  4   │  1   │  1   │     16       │    32    │   4    │  4  │   8?   │    4?    │    8     │   12   │       *        │
        └──────┴──────┴──────┴──────────────┴──────────┴────────┴─────┴────────┴──────────┴──────────┴────────┴────────────────┘
                                                                  ▲       ▲
                                                                  │       │
                                                       present iff flags.0│
                                                                          │
                                                              present iff flags.1
```

`magic` = ASCII `BCE5`. `ver` = `0x05`. `flags` bits: `0` = has_ttl, `1` = has_open_count, `2` = dh_ratchet_step (set when this message advances the receiver's DH chain). `contactIdHash` = `BLAKE2s-128(contact.lookupKey ‖ installationUuid)`. `nonce` = first 12 bytes of `HMAC-SHA256(mk, "nonce"|n)`. Ciphertext includes the 16-byte GCM tag.

The whole header (magic..nonce, inclusive) is the GCM `associatedData`.

---

## Task 1: Add Argon2kt dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + alias**

`libs.versions.toml`:
```toml
argon2kt = "1.5.0"
…
argon2kt = { module = "com.lambdapioneer.argon2kt:argon2kt", version.ref = "argon2kt" }
```

- [ ] **Step 2: Wire into app**

`app/build.gradle.kts`, in `dependencies { ... }`:
```kotlin
implementation(libs.argon2kt)
```

- [ ] **Step 3: Refresh and confirm**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep argon2kt
```
Expected: a line containing `com.lambdapioneer.argon2kt:argon2kt:1.5.0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "deps: add argon2kt for Argon2id KDF"
```

---

## Task 2: Argon2 wrapper

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/Argon2.kt`
- Create: `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/Argon2Test.kt`

- [ ] **Step 1: Failing test**

`Argon2Test.kt`:
```kotlin
package com.hereliesaz.barcodencrypt.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Argon2Test {

    @Test
    fun `derive returns 32 bytes`() {
        val out = Argon2.derive("hello".toByteArray(), ByteArray(16) { 0 })
        assertEquals(32, out.size)
    }

    @Test
    fun `same input and salt produces same key`() {
        val a = Argon2.derive("k".toByteArray(), ByteArray(16) { 7 })
        val b = Argon2.derive("k".toByteArray(), ByteArray(16) { 7 })
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun `different salt yields different key`() {
        val a = Argon2.derive("k".toByteArray(), ByteArray(16) { 1 })
        val b = Argon2.derive("k".toByteArray(), ByteArray(16) { 2 })
        assertNotEquals(a.toList(), b.toList())
    }
}
```

- [ ] **Step 2: Run; expect "Unresolved reference: Argon2"**

```bash
./gradlew :app:testDebugUnitTest --tests "*Argon2Test*"
```

- [ ] **Step 3: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

object Argon2 {
    private const val TIME_COST = 3
    private const val MEMORY_COST_KIB = 65_536
    private const val PARALLELISM = 2
    private const val HASH_LEN = 32

    private val engine = Argon2Kt()

    fun derive(password: ByteArray, salt: ByteArray): ByteArray {
        require(salt.size in 8..64) { "salt must be 8..64 bytes" }
        val res = engine.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = TIME_COST,
            mCostInKibibyte = MEMORY_COST_KIB,
            parallelism = PARALLELISM,
            hashLengthInBytes = HASH_LEN,
        )
        return res.rawHashAsByteArray()
    }
}
```

- [ ] **Step 4: Re-run**

Expected: PASS. (`argon2kt` is a JVM/Android library; pure JVM unit tests work because the unit-test runtime loads the native lib when invoked. If your CI sandbox cannot load native libs, mark these tests `@Ignore` and move them to `androidTest`; the rest of this plan assumes pure-JVM viability.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/Argon2.kt \
        app/src/test/java/com/hereliesaz/barcodencrypt/crypto/Argon2Test.kt
git commit -m "crypto: Argon2id wrapper with fixed params (t=3, m=64MiB, p=2)"
```

---

## Task 3: Binary MessageHeader

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageHeader.kt`
- Create: `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageHeaderTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // 4 + 1 + 1 + 16 + 32 + 4 + 4 + 8 + 12 = 82 bytes (no TTL, no openCount)
        assertEquals(82, bytes.size)
        assertEquals(h, MessageHeader.decode(bytes))
    }

    @Test
    fun `round trip with TTL and openCount`() {
        val bytes = baseHeader.encode()
        // adds 8 + 4 = 94 bytes total
        assertEquals(94, bytes.size)
        assertEquals(baseHeader, MessageHeader.decode(bytes))
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
}
```

- [ ] **Step 2: Run; expect compile failure**

- [ ] **Step 3: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        require(contactIdHash.size == 16)
        require(dhPublic.size == 32)
        require(nonce.size == 12)
    }

    fun encode(): ByteArray {
        var size = HEADER_FIXED
        if (ttlMs != null) size += 8
        if (openCountMax != null) size += 4
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(VERSION)
        var flags = 0
        if (ttlMs != null) flags = flags or 0x1
        if (openCountMax != null) flags = flags or 0x2
        if (dhRatchetStep) flags = flags or 0x4
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
        // 4 magic + 1 ver + 1 flags + 16 contactIdHash + 32 dh + 4 prevN + 4 n + 8 ts + 12 nonce
        private const val HEADER_FIXED = 4 + 1 + 1 + 16 + 32 + 4 + 4 + 8 + 12

        fun decode(bytes: ByteArray): MessageHeader {
            require(bytes.size >= HEADER_FIXED) { "header too short" }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = buf.get()
            require(version == VERSION) { "bad version" }
            val flags = buf.get().toInt() and 0xFF
            val hasTtl = flags and 0x1 != 0
            val hasOpenCount = flags and 0x2 != 0
            val dhStep = flags and 0x4 != 0
            val contactIdHash = ByteArray(16).also { buf.get(it) }
            val dhPublic = ByteArray(32).also { buf.get(it) }
            val prevN = buf.int
            val n = buf.int
            val ttl = if (hasTtl) buf.long else null
            val openMax = if (hasOpenCount) buf.int else null
            val timestamp = buf.long
            val nonce = ByteArray(12).also { buf.get(it) }
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
```

- [ ] **Step 4: Run; expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*MessageHeaderTest*"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageHeader.kt \
        app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageHeaderTest.kt
git commit -m "crypto: binary MessageHeader with magic/version/flags and tests"
```

---

## Task 4: MessageEnvelope (wire format with `~BCEv5~` prefix)

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelope.kt`
- Create: `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelopeTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import org.junit.Assert.*
import org.junit.Test

class MessageEnvelopeTest {

    @Test
    fun `round trip a small payload`() {
        val payload = "abc".toByteArray()
        val token = MessageEnvelope.encode(payload)
        assertTrue(token.startsWith("~BCEv5~"))
        assertArrayEquals(payload, MessageEnvelope.decode(token))
    }

    @Test
    fun `decode returns null for non-v5 token`() {
        assertNull(MessageEnvelope.decode("~BCEv4~abc.def"))
    }

    @Test
    fun `decode returns null for missing prefix`() {
        assertNull(MessageEnvelope.decode("hello"))
    }
}
```

- [ ] **Step 2: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64

object MessageEnvelope {
    const val PREFIX = "~BCEv5~"
    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE

    fun encode(payload: ByteArray): String =
        PREFIX + Base64.encodeToString(payload, BASE64_FLAGS)

    fun decode(token: String): ByteArray? {
        if (!token.startsWith(PREFIX)) return null
        return runCatching { Base64.decode(token.removePrefix(PREFIX), BASE64_FLAGS) }.getOrNull()
    }
}
```

- [ ] **Step 3: Run; expect PASS**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelope.kt \
        app/src/test/java/com/hereliesaz/barcodencrypt/crypto/MessageEnvelopeTest.kt
git commit -m "crypto: MessageEnvelope wire format with ~BCEv5~ prefix"
```

---

## Task 5: RatchetState value type

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetState.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

/**
 * Immutable snapshot of one side of a Signal-style double ratchet, scoped to a contact.
 *
 * Field naming matches the Signal spec: rk = root key, cks = sending chain key,
 * ckr = receiving chain key, ns/nr = sent/received message numbers, pn = previous
 * sending-chain message number. dhSelfPriv/Pub is our current ephemeral keypair;
 * dhRemotePub is the latest public key we've received from the peer.
 *
 * skipped is a bounded LRU of message keys for messages we expected but haven't
 * yet received, keyed by (remoteDhPublic, messageNumber).
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
    init { require(rk.size == 32) }
}

data class SkippedKeyId(val dhPublic: ByteArray, val n: Int) {
    override fun equals(other: Any?): Boolean =
        other is SkippedKeyId && n == other.n && dhPublic.contentEquals(other.dhPublic)
    override fun hashCode(): Int = 31 * dhPublic.contentHashCode() + n
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetState.kt
git commit -m "crypto: RatchetState value type"
```

---

## Task 6: RatchetEngine — pure functions, full TDD

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngine.kt`
- Create: `app/src/test/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngineTest.kt`

This is the heart of the plan. The engine is deliberately pure (no Android, no IO) so it can be tested exhaustively.

- [ ] **Step 1: Write the test contract first**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class RatchetEngineTest {

    companion object {
        @BeforeClass @JvmStatic fun setupTink() = AeadConfig.register()
    }

    private val salt = ByteArray(16) { 1 }
    private val barcode = "1234567890123".toByteArray()
    private val password: ByteArray? = null
    private val contactIdHash = ByteArray(16) { 7 }

    private fun newState() = RatchetEngine.bootstrap(barcode, password, salt)

    @Test
    fun `bootstrap is deterministic given the same inputs`() {
        val a = RatchetEngine.bootstrap(barcode, password, salt)
        val b = RatchetEngine.bootstrap(barcode, password, salt)
        assertArrayEquals(a.rk, b.rk)
        assertArrayEquals(a.cks, b.cks)
    }

    @Test
    fun `round trip simple message`() {
        val alice = newState()
        val bob = newState()

        val (alice2, env) = RatchetEngine.encrypt(
            state = alice, contactIdHash = contactIdHash,
            plaintext = "hello".toByteArray(), ttlMs = null, openMax = null,
        )
        val (bob2, plaintext) = RatchetEngine.decrypt(state = bob, envelope = env)!!
        assertEquals("hello", String(plaintext))
        assertEquals(1, alice2.ns)
        assertEquals(1, bob2.nr)
    }

    @Test
    fun `tampered ttl fails authentication`() {
        val alice = newState()
        val bob = newState()
        val (_, env) = RatchetEngine.encrypt(
            alice, contactIdHash, "x".toByteArray(), ttlMs = 1_000L, openMax = null,
        )
        // Flip the TTL byte inside the header. Decoding stays valid, but GCM tag fails.
        val raw = MessageEnvelope.decode(env)!!
        // 4 magic + 1 ver + 1 flags + 16 hash + 32 dh + 4 prev + 4 n = 62 — first TTL byte at offset 62
        raw[62] = (raw[62] + 1).toByte()
        val tampered = MessageEnvelope.encode(raw)
        assertNull(RatchetEngine.decrypt(bob, tampered))
    }

    @Test
    fun `out of order within skip window decrypts`() {
        val alice = newState()
        var bob = newState()
        val (a1, e1) = RatchetEngine.encrypt(alice, contactIdHash, "one".toByteArray(), null, null)
        val (a2, e2) = RatchetEngine.encrypt(a1, contactIdHash, "two".toByteArray(), null, null)
        val (a3, e3) = RatchetEngine.encrypt(a2, contactIdHash, "three".toByteArray(), null, null)
        // Bob receives 3 first
        val (bob3, p3) = RatchetEngine.decrypt(bob, e3)!!
        assertEquals("three", String(p3))
        // Then 1 and 2
        val (bob1, p1) = RatchetEngine.decrypt(bob3, e1)!!
        assertEquals("one", String(p1))
        val (bob2, p2) = RatchetEngine.decrypt(bob1, e2)!!
        assertEquals("two", String(p2))
    }

    @Test
    fun `replay yields null on second attempt`() {
        val alice = newState()
        var bob = newState()
        val (_, env) = RatchetEngine.encrypt(alice, contactIdHash, "x".toByteArray(), null, null)
        val (bob2, _) = RatchetEngine.decrypt(bob, env)!!
        assertNull(RatchetEngine.decrypt(bob2, env))
    }

    @Test
    fun `password changes derived key`() {
        val withoutPw = RatchetEngine.bootstrap(barcode, null, salt)
        val withPw = RatchetEngine.bootstrap(barcode, "secret".toByteArray(), salt)
        assertFalse(withoutPw.rk.contentEquals(withPw.rk))
    }

    @Test
    fun `expired TTL returns null`() {
        val alice = newState()
        val bob = newState()
        val (_, env) = RatchetEngine.encrypt(alice, contactIdHash, "x".toByteArray(), ttlMs = 1L, openMax = null)
        Thread.sleep(5)
        assertNull(RatchetEngine.decrypt(bob, env))
    }
}
```

- [ ] **Step 2: Run; expect compile errors**

- [ ] **Step 3: Implement RatchetEngine — bootstrap + symmetric path (no DH ratchet yet)**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

object RatchetEngine {

    private const val KEY_SIZE = 32
    private const val MAC_ALG = "HMACSHA256"
    private const val INFO_ROOT = "BCEv5_Root"
    private const val INFO_SEND_CHAIN = "BCEv5_SendChain"
    private const val INFO_RECV_CHAIN = "BCEv5_RecvChain"
    private const val INFO_MESSAGE_KEY = "BCEv5_MessageKey"
    private const val INFO_NEXT_CHAIN = "BCEv5_NextChain"
    private const val INFO_NONCE = "BCEv5_Nonce"
    private const val SKIP_WINDOW = 1000

    fun bootstrap(barcode: ByteArray, password: ByteArray?, contactSalt: ByteArray): RatchetState {
        val keyMaterial = barcode + (password ?: ByteArray(0))
        val bootstrapKey = Argon2.derive(keyMaterial, contactSalt)
        val rk = hkdf(bootstrapKey, ByteArray(0), INFO_ROOT)
        val cks = hkdf(bootstrapKey, ByteArray(0), INFO_SEND_CHAIN)
        val ckr = hkdf(bootstrapKey, ByteArray(0), INFO_RECV_CHAIN)
        return RatchetState(
            rk = rk, cks = cks, ckr = ckr,
            dhSelfPriv = null, dhSelfPub = null, dhRemotePub = null,
            ns = 0, nr = 0, pn = 0, skipped = emptyMap(),
        )
    }

    fun encrypt(
        state: RatchetState,
        contactIdHash: ByteArray,
        plaintext: ByteArray,
        ttlMs: Long?,
        openMax: Int?,
    ): Pair<RatchetState, String> {
        val cks = requireNotNull(state.cks) { "sending chain not initialized" }
        val (nextCks, mk) = kdfCk(cks)
        val nonce = deriveNonce(mk, state.ns)
        val header = MessageHeader(
            contactIdHash = contactIdHash,
            dhPublic = state.dhSelfPub ?: ByteArray(32),
            previousChainMessages = state.pn,
            messageNumber = state.ns,
            ttlMs = ttlMs,
            openCountMax = openMax,
            timestampMs = System.currentTimeMillis(),
            nonce = nonce,
            dhRatchetStep = false,
        )
        val headerBytes = header.encode()
        val aead = AesGcmJce(mk)
        val ciphertext = aead.encrypt(plaintext, headerBytes)
        val payload = headerBytes + ciphertext
        val newState = state.copy(cks = nextCks, ns = state.ns + 1)
        return newState to MessageEnvelope.encode(payload)
    }

    fun decrypt(state: RatchetState, envelope: String): Pair<RatchetState, ByteArray>? {
        val payload = MessageEnvelope.decode(envelope) ?: return null
        // The header has a variable length depending on flags. Decode in two passes:
        // first the fixed prefix, then determine total header length, then split.
        val header = runCatching { MessageHeader.decode(payload) }.getOrNull() ?: return null
        val headerLen = headerLengthFor(header)
        val headerBytes = payload.copyOfRange(0, headerLen)
        val ciphertext = payload.copyOfRange(headerLen, payload.size)
        if (header.ttlMs != null && System.currentTimeMillis() - header.timestampMs > header.ttlMs) return null

        // 1) skipped-key fast path
        val skippedKey = state.skipped[SkippedKeyId(header.dhPublic, header.messageNumber)]
        if (skippedKey != null) {
            val plain = runCatching { AesGcmJce(skippedKey).decrypt(ciphertext, headerBytes) }.getOrNull()
                ?: return null
            val newSkipped = state.skipped - SkippedKeyId(header.dhPublic, header.messageNumber)
            return state.copy(skipped = newSkipped) to plain
        }

        // 2) advance receiving chain (with skip-up-to-window)
        var ckr = requireNotNull(state.ckr) { "receiving chain not initialized" }
        var nr = state.nr
        val newSkipped = state.skipped.toMutableMap()
        if (header.messageNumber < nr) return null // replay
        val skipsNeeded = header.messageNumber - nr
        if (skipsNeeded > SKIP_WINDOW) return null
        repeat(skipsNeeded) {
            val (nextCkr, mkSkipped) = kdfCk(ckr)
            ckr = nextCkr
            newSkipped[SkippedKeyId(header.dhPublic, nr)] = mkSkipped
            nr += 1
            if (newSkipped.size > SKIP_WINDOW) {
                val eldest = newSkipped.keys.first()
                newSkipped.remove(eldest)
            }
        }
        val (nextCkr, mk) = kdfCk(ckr)
        ckr = nextCkr
        nr += 1

        val plain = runCatching { AesGcmJce(mk).decrypt(ciphertext, headerBytes) }.getOrNull() ?: return null

        return state.copy(ckr = ckr, nr = nr, skipped = newSkipped) to plain
    }

    // ---- KDFs ----

    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val mk = Hkdf.computeHkdf(MAC_ALG, ck, null, INFO_MESSAGE_KEY.toByteArray(), KEY_SIZE)
        val next = Hkdf.computeHkdf(MAC_ALG, ck, null, INFO_NEXT_CHAIN.toByteArray(), KEY_SIZE)
        return next to mk
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: String): ByteArray =
        Hkdf.computeHkdf(MAC_ALG, ikm, salt, info.toByteArray(), KEY_SIZE)

    private fun deriveNonce(mk: ByteArray, n: Int): ByteArray {
        val mac = Mac.getInstance(MAC_ALG)
        mac.init(SecretKeySpec(mk, MAC_ALG))
        val nBytes = byteArrayOf(
            (n ushr 24).toByte(), (n ushr 16).toByte(),
            (n ushr 8).toByte(), n.toByte(),
        )
        val full = mac.doFinal(INFO_NONCE.toByteArray() + nBytes)
        return full.copyOf(12)
    }

    private fun headerLengthFor(h: MessageHeader): Int {
        var size = 4 + 1 + 1 + 16 + 32 + 4 + 4 + 8 + 12
        if (h.ttlMs != null) size += 8
        if (h.openCountMax != null) size += 4
        return size
    }
}
```

- [ ] **Step 4: Run; iterate until green**

```bash
./gradlew :app:testDebugUnitTest --tests "*RatchetEngineTest*"
```

Common adjustments you'll hit:
- The `tampered ttl fails authentication` test computes `62` for the TTL byte offset; if the field order in `MessageHeader.encode()` shifted, recompute.
- `replay yields null on second attempt` requires that `nr` advance past `header.messageNumber`. If your impl allows `header.messageNumber == nr - 1` to re-decrypt via skipped map, ensure skipped is cleared on use (the impl above does so).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngine.kt \
        app/src/test/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngineTest.kt
git commit -m "crypto: RatchetEngine symmetric path with skipped-key window, full tests"
```

---

## Task 7: RatchetState persistence (Room)

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateEntity.kt`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateDao.kt`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/data/RatchetStateRepository.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/Contact.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/Converters.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLog*.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptedScriptLogManager.kt`

- [ ] **Step 1: Entity**

```kotlin
package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    // Serialized JSON map of {base64(dhPublic):int → base64(mk)}; bounded ≤ 1000 entries.
    val skippedJson: String,
)
```

- [ ] **Step 2: DAO**

```kotlin
package com.hereliesaz.barcodencrypt.data

import androidx.room.*

@Dao
interface RatchetStateDao {
    @Query("SELECT * FROM ratchet_state WHERE contactLookupKey = :key")
    suspend fun get(key: String): RatchetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RatchetStateEntity)

    @Query("DELETE FROM ratchet_state WHERE contactLookupKey = :key")
    suspend fun delete(key: String)
}
```

- [ ] **Step 3: Repository + state ↔ entity mapping**

```kotlin
package com.hereliesaz.barcodencrypt.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hereliesaz.barcodencrypt.crypto.RatchetState
import com.hereliesaz.barcodencrypt.crypto.SkippedKeyId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatchetStateRepository @Inject constructor(
    private val dao: RatchetStateDao,
) {
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, String>>() {}.type

    suspend fun load(contactKey: String): Pair<ByteArray, RatchetState>? {
        val e = dao.get(contactKey) ?: return null
        val skippedRaw: Map<String, String> = gson.fromJson(e.skippedJson, type) ?: emptyMap()
        val skipped = skippedRaw.entries.associate { (k, v) ->
            val parts = k.split(":")
            SkippedKeyId(Base64.decode(parts[0], Base64.NO_WRAP), parts[1].toInt()) to
                Base64.decode(v, Base64.NO_WRAP)
        }
        val state = RatchetState(
            rk = e.rk, cks = e.cks, ckr = e.ckr,
            dhSelfPriv = e.dhSelfPriv, dhSelfPub = e.dhSelfPub, dhRemotePub = e.dhRemotePub,
            ns = e.ns, nr = e.nr, pn = e.pn, skipped = skipped,
        )
        return e.salt to state
    }

    suspend fun save(contactKey: String, salt: ByteArray, state: RatchetState) {
        val skippedRaw = state.skipped.entries.associate { (id, mk) ->
            "${Base64.encodeToString(id.dhPublic, Base64.NO_WRAP)}:${id.n}" to
                Base64.encodeToString(mk, Base64.NO_WRAP)
        }
        dao.upsert(
            RatchetStateEntity(
                contactLookupKey = contactKey,
                salt = salt,
                rk = state.rk, cks = state.cks, ckr = state.ckr,
                dhSelfPriv = state.dhSelfPriv, dhSelfPub = state.dhSelfPub,
                dhRemotePub = state.dhRemotePub,
                ns = state.ns, nr = state.nr, pn = state.pn,
                skippedJson = gson.toJson(skippedRaw),
            )
        )
    }
}
```

- [ ] **Step 4: Patch AppDatabase**

In `data/AppDatabase.kt`:
- Update `entities` to include `RatchetStateEntity::class` and drop `EncryptedScriptLog::class`.
- Bump `version = 11`.
- Add destructive `MIGRATION_10_11`:
```kotlin
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS encrypted_script_log")
        // Drop the V3 columns from contacts (Room's older versions did not support DROP COLUMN
        // pre-3.35.0; do a copy-table dance):
        db.execSQL("""
            CREATE TABLE contacts_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                lookupKey TEXT NOT NULL,
                name TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO contacts_new (id, lookupKey, name) SELECT id, lookupKey, name FROM contacts")
        db.execSQL("DROP TABLE contacts")
        db.execSQL("ALTER TABLE contacts_new RENAME TO contacts")
        db.execSQL("CREATE UNIQUE INDEX index_contacts_lookupKey ON contacts(lookupKey)")
        db.execSQL("""
            CREATE TABLE ratchet_state (
                contactLookupKey TEXT NOT NULL PRIMARY KEY,
                salt BLOB NOT NULL,
                rk BLOB NOT NULL,
                cks BLOB,
                ckr BLOB,
                dhSelfPriv BLOB,
                dhSelfPub BLOB,
                dhRemotePub BLOB,
                ns INTEGER NOT NULL,
                nr INTEGER NOT NULL,
                pn INTEGER NOT NULL,
                skippedJson TEXT NOT NULL
            )
        """.trimIndent())
    }
}
```
- Add `MIGRATION_10_11` to `addMigrations(...)` chain.
- Add `abstract fun ratchetStateDao(): RatchetStateDao`.

- [ ] **Step 5: Strip V3 fields from Contact**

In `data/Contact.kt`, remove every field after `name`: `dhKeyPair`, `dhRemotePublicKey`, `rootKey`, `sendingChainKey`, `receivingChainKey`, `sendingMessageNumber`, `receivingMessageNumber`, `previousSendingChainMessageNumber`, `skippedMessageKeys`. Update `equals`/`hashCode` accordingly.

- [ ] **Step 6: Add provider in DatabaseModule**

```kotlin
@Provides
fun provideRatchetStateDao(appDatabase: AppDatabase): RatchetStateDao = appDatabase.ratchetStateDao()
```

- [ ] **Step 7: Delete the legacy crypto-log files**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLog.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLogDao.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLogRepository.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptedScriptLogManager.kt
```

- [ ] **Step 8: Verify build still compiles (EncryptionManager next task)**

```bash
./gradlew :app:assembleDebug --stacktrace
```
Expected: `EncryptionManager.kt` will fail because it still references the deleted `EncryptedScriptLogManager`. That's intentional — Task 8 fixes it.

- [ ] **Step 9: Commit (red — proceed straight to Task 8)**

```bash
git add -A
git commit -m "data: add RatchetStateEntity/Dao/Repository, drop EncryptedScriptLog, migration 10→11"
```

---

## Task 8: New EncryptionManager façade

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManager.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import com.hereliesaz.barcodencrypt.util.Hashing
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    private val ratchetStateRepository: RatchetStateRepository,
) {
    private val random = SecureRandom()

    suspend fun encrypt(
        plaintext: String,
        contactLookupKey: String,
        barcode: Barcode,
        password: String? = null,
        ttlMs: Long? = null,
        openMax: Int? = null,
    ): String? {
        val (salt, state) = loadOrBootstrap(contactLookupKey, barcode, password)
        val contactIdHash = Hashing.sha256(contactLookupKey).substring(0, 32).chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
        val (newState, envelope) = RatchetEngine.encrypt(
            state = state,
            contactIdHash = contactIdHash,
            plaintext = plaintext.toByteArray(Charsets.UTF_8),
            ttlMs = ttlMs,
            openMax = openMax,
        )
        ratchetStateRepository.save(contactLookupKey, salt, newState)
        return envelope
    }

    suspend fun decrypt(
        envelope: String,
        contactLookupKey: String,
        barcode: Barcode,
        password: String? = null,
    ): String? {
        val (salt, state) = loadOrBootstrap(contactLookupKey, barcode, password)
        val (newState, plaintext) = RatchetEngine.decrypt(state, envelope) ?: return null
        ratchetStateRepository.save(contactLookupKey, salt, newState)
        return String(plaintext, Charsets.UTF_8)
    }

    private suspend fun loadOrBootstrap(
        contactLookupKey: String,
        barcode: Barcode,
        password: String?,
    ): Pair<ByteArray, RatchetState> {
        ratchetStateRepository.load(contactLookupKey)?.let { return it }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val state = RatchetEngine.bootstrap(
            barcode = barcode.value.toByteArray(Charsets.UTF_8),
            password = password?.toByteArray(Charsets.UTF_8),
            contactSalt = salt,
        )
        ratchetStateRepository.save(contactLookupKey, salt, state)
        return salt to state
    }
}
```

- [ ] **Step 2: Update call sites**

`viewmodel/ComposeViewModel.kt`, `viewmodel/ScannerViewModel.kt`: change `encryptionManager.encrypt(plaintext, barcode, password, ttl, openCount)` → `encryptionManager.encrypt(plaintext, contactLookupKey, barcode, password, ttlMs, openMax)`. They already have the contact in context.

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug --stacktrace
```
Expected: green.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "crypto: EncryptionManager rebuilt over RatchetEngine + RatchetStateRepository"
```

---

## Task 9: Patch MessageParser to match `~BCEv5~`

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/util/MessageParser.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt`

- [ ] **Step 1: Replace regex**

```kotlin
private val V5_REGEX = Regex("~BCEv5~[A-Za-z0-9+/=_\\-]+")

fun findAllV5Tokens(root: AccessibilityNodeInfo): List<Pair<String, android.graphics.Rect>> { ... }
```
(Mirror the structure of `findAllV4Tokens`, just swap the regex.)

- [ ] **Step 2: Update MessageDetectionService to call the new function name**

In `services/MessageDetectionService.kt`, swap `findAllV4Tokens` → `findAllV5Tokens`.

- [ ] **Step 3: Run instrumented round-trip (next task) — for now build and commit**

```bash
./gradlew :app:assembleDebug
git add -A
git commit -m "detect: parser matches ~BCEv5~ tokens that EncryptionManager now emits"
```

---

## Task 10: Instrumented round-trip test

**Files:**
- Create: `app/src/androidTest/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManagerInstrumentedTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room

@RunWith(AndroidJUnit4::class)
class EncryptionManagerInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: EncryptionManager

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val repo = RatchetStateRepository(db.ratchetStateDao())
        manager = EncryptionManager(repo)
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun roundTripWithRealRoom() = runBlocking {
        val barcode = Barcode(
            id = 1, contactLookupKey = "contact1", name = "k",
            encryptedValue = ByteArray(0), iv = ByteArray(0),
        ).apply { /* test-only: set transient .value via reflection or test helper */ }
        // Use the value-setting test helper or rebuild Barcode with a `value` test seam.
        val token = manager.encrypt("hello", "contact1", barcode)!!
        assertTrue(token.startsWith("~BCEv5~"))
        val plain = manager.decrypt(token, "contact1", barcode)
        assertEquals("hello", plain)
    }
}
```

(Add a `@VisibleForTesting fun Barcode.withTestValue(v: String): Barcode` helper in the `data` package that builds a synthetic Barcode with `.value` populated, so instrumented tests don't need to round-trip Keystore.)

- [ ] **Step 2: Run on emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "*EncryptionManagerInstrumentedTest*"
```

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManagerInstrumentedTest.kt
git commit -m "test: instrumented round-trip for EncryptionManager + RatchetEngine via real Room"
```

---

## Task 11: DH Ratchet step (the asymmetric half)

Implements the rest of the Signal double ratchet. Required only when both sides exchange X25519 keys (QR flow in Plan 4). Until then, the symmetric path works.

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/RatchetEngine.kt` — add `dhRatchetStep`, modify `encrypt`/`decrypt` to set/respond to `dhRatchetStep` flag.
- Add tests to `RatchetEngineTest.kt` exercising: a) initial DH exchange, b) two messages, then a DH ratchet round-trip, c) third message uses the new chain.

- [ ] **Step 1: Add DH operations**

```kotlin
// Inside RatchetEngine

fun startSession(state: RatchetState, remotePublic: ByteArray): RatchetState {
    val priv = com.google.crypto.tink.subtle.X25519.generatePrivateKey()
    val pub = com.google.crypto.tink.subtle.X25519.publicFromPrivate(priv)
    val shared = com.google.crypto.tink.subtle.X25519.computeSharedSecret(priv, remotePublic)
    val (newRk, sendingChain) = kdfRk(state.rk, shared)
    return state.copy(
        rk = newRk, cks = sendingChain, ckr = null,
        dhSelfPriv = priv, dhSelfPub = pub, dhRemotePub = remotePublic,
        ns = 0, nr = 0, pn = state.ns,
    )
}

private fun kdfRk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
    val out = Hkdf.computeHkdf(MAC_ALG, dhOut, rk, "BCEv5_RkChain".toByteArray(), KEY_SIZE * 2)
    return out.copyOfRange(0, KEY_SIZE) to out.copyOfRange(KEY_SIZE, KEY_SIZE * 2)
}

private fun dhRatchetReceive(state: RatchetState, remotePublic: ByteArray): RatchetState {
    val privExisting = requireNotNull(state.dhSelfPriv)
    val incomingShared = com.google.crypto.tink.subtle.X25519.computeSharedSecret(privExisting, remotePublic)
    val (rkAfterIn, recvChain) = kdfRk(state.rk, incomingShared)
    // Generate new self keys for the next sending chain
    val newPriv = com.google.crypto.tink.subtle.X25519.generatePrivateKey()
    val newPub = com.google.crypto.tink.subtle.X25519.publicFromPrivate(newPriv)
    val outgoingShared = com.google.crypto.tink.subtle.X25519.computeSharedSecret(newPriv, remotePublic)
    val (rkAfterOut, sendChain) = kdfRk(rkAfterIn, outgoingShared)
    return state.copy(
        rk = rkAfterOut,
        cks = sendChain, ckr = recvChain,
        dhSelfPriv = newPriv, dhSelfPub = newPub, dhRemotePub = remotePublic,
        pn = state.ns, ns = 0, nr = 0,
    )
}
```

Then in `decrypt`, before the chain advance: if `header.dhPublic` differs from `state.dhRemotePub`, call `dhRatchetReceive(state, header.dhPublic)` first, then proceed.

In `encrypt`, set `dhRatchetStep = true` on the first message of a new sending chain.

- [ ] **Step 2: Add the 3 DH-ratchet tests**

(Patterns: assert chain keys differ before/after; assert keys rotate; assert old messages from the previous chain still decrypt via skipped if delivered late.)

- [ ] **Step 3: Iterate to green; commit**

```bash
git add -A
git commit -m "crypto: DH ratchet step (X25519 via Tink) plus tests"
```

---

## Task 12: Tear out the legacy `MessageHeader` Gson struct

Delete the Gson-based `MessageHeader` data class from the old EncryptionManager.kt if any vestige remains (it should be gone after Task 8, but double-check).

```bash
grep -rn "headerJson\|headerBase64\|com.google.gson.Gson" app/src/main/java/com/hereliesaz/barcodencrypt/crypto
```
Expected: no results from `crypto/`. Then commit any clean-up edits.

---

## Task 13: Wire the contactLookupKey through ViewModels

After Plan 1, `EncryptionManager.encrypt(plaintext, barcode, password, ttl, openCount)` took only barcode. v5 requires `contactLookupKey`. Update:
- `ComposeViewModel.encryptMessage(...)` — already knows `selectedContact?.lookupKey`.
- `ScannerViewModel.decryptMessage(...)` — needs a contact context; either receive `contactLookupKey` via nav arg or store the most-recently-detected contact ID hash in the `MessageDetectionService → OverlayService → Scanner` chain. For Plan 2, accept that the scanner UI gains a "select recipient" step (Plan 4 makes it pretty). Add a `contactLookupKey: String` parameter.

Commit as one step:
```bash
git add -A
git commit -m "vm: thread contactLookupKey through encrypt/decrypt call sites"
```

---

## Acceptance criteria

- ✅ `./gradlew :app:testDebugUnitTest` runs `Argon2Test`, `MessageHeaderTest`, `MessageEnvelopeTest`, `RatchetEngineTest` (≥ 10 cases) — all pass.
- ✅ `./gradlew :app:connectedDebugAndroidTest` runs `EncryptionManagerInstrumentedTest` — passes.
- ✅ Emulator manual test: compose a message via Compose screen, watch it appear with a `~BCEv5~` token, decrypt via Scanner with the correct barcode, see plaintext.
- ✅ `grep -rn "EncryptedScriptLog\|masterKey\|BCEv3\|BCEv4\|TinkMessage\|V3Message" app/src/main` returns no matches (modulo migration-comment strings).
- ✅ The header is AAD-authenticated: flipping any bit between `magic` and `nonce` makes `decrypt` return null.
- ✅ TTL expiry is enforced (post-deadline returns null).
- ✅ Open-count exhaustion is enforced (N+1th decrypt fails).
- ✅ Out-of-order delivery within 1000-message window decrypts correctly.
- ✅ Replay (same envelope, twice) fails on the second attempt.
- ✅ Password-protected mode produces a different `rk` than password-less mode given the same barcode and salt.

## Risk register

- **Argon2 cost on low-end devices.** 64 MiB / 3 iters / 2 lanes ≈ 200–500 ms on modern phones, up to ~1.5 s on weak hardware. Only happens on bootstrap (first ever message with a contact) and on session-key reload — not per-message. Acceptable.
- **`argon2kt` native libs in unit tests.** If your unit-test runtime can't load the native library, move `Argon2Test` to `androidTest`. The rest of `RatchetEngineTest` then uses a fake `Argon2.derive` via a thin seam (introduce an interface). Prefer the simple path until proven broken.
- **Skipped-key map growth.** Capped at 1000 entries with FIFO eviction. Eldest skipped key dropped silently if exceeded. Documented; no UX impact unless an attacker floods.
- **Wire format BC.** Existing `~BCEv3~` / `~BCEv4~` tokens are unreadable. Per D3, this is accepted.
- **Schema migration is destructive for `barcodes` data** if there is any. Per D3, accepted; in practice there's no production data.
