# BarCodeNcrypt v6 wire format

This document is the canonical reference for the on-wire format of v6 encrypted messages.
Round-trip unit tests (`RatchetEngineTest`, `MessageHeaderTest`) assert against it.

## 1. Token

```
~BCEv6~<base64>
```

- Prefix is the literal seven characters `~BCEv6~`.
- `<base64>` uses the URL-safe alphabet (`A-Z a-z 0-9 - _`) with no padding and no
  line breaks.
- The base64 decodes to a binary payload of `[ header || ciphertext ]`.

## 2. Binary header (big-endian, multi-byte fields)

| Field                  | Size | Notes                                                   |
| ---------------------- | ---: | ------------------------------------------------------- |
| `magic`                |    4 | ASCII `"BCE6"` (`0x42 0x43 0x45 0x36`)                  |
| `version`              |    1 | `0x06`                                                  |
| `flags`                |    1 | bit 0: `has_ttl`, bit 1: `has_open_count`, bit 2: `dh_ratchet_step` |
| `contactIdHash`        |   16 | First 16 bytes of `SHA-256(contact.lookupKey)`          |
| `salt`                 |   16 | The sender's per-contact Argon2 salt                    |
| `dhPublic`             |   32 | Sender's current X25519 public key (zero when symmetric only)  |
| `previousChainMessages` | 4   | Message count in the previous sending chain (`pn`)      |
| `messageNumber`        |    4 | Position within the current sending chain (`n`)         |
| `ttlMs`                |    8 | Present iff `flags.0`                                   |
| `openCountMax`         |    4 | Present iff `flags.1`                                   |
| `timestampMs`          |    8 | `System.currentTimeMillis()` at encrypt time            |

Fixed header size (no flags set): **86 bytes**. With both TTL and `openCount`: **98 bytes**.

The whole encoded header is fed to AES-GCM as `associatedData`. Flipping any bit in the
header therefore fails the GCM tag, which the receiver surfaces as a generic decryption
failure. The 96-bit GCM IV is generated randomly by the AEAD (Tink `AesGcmJce`) and is
carried at the front of the ciphertext — it is **not** a header field.

## 3. Cryptographic construction

### 3.1 Bootstrap (first encrypt/decrypt for a contact)

```
keyMaterial   = barcode_bytes ‖ password_bytes_or_empty
bootstrap_key = Argon2id(keyMaterial, salt, t=3, m=64 MiB, p=2, len=32)
rk_0  = HKDF-SHA256(bootstrap_key, info="BCEv6_Root",  len=32)
ck_0  = HKDF-SHA256(bootstrap_key, info="BCEv6_Chain", len=32)
```

`salt` is 16 bytes of `SecureRandom`, generated once per contact on the **sending** side
and carried in every outbound header. Both peers derive the **same** chain key `ck_0` from
the same `(barcode, password, salt)` triple, so the sender's chain key for message `n`
equals the receiver's chain key for message `n`.

The path is bidirectional: each peer uses its **own** salt for its sending chain, and
seeds a separate **receiving** chain from the salt it reads out of the first inbound
header. Because the two salts differ, the two directions never collide on a message key
even though both start at `n = 0`.

### 3.2 Symmetric ratchet step

```
mk_n     = HKDF-SHA256(ck_n, info="BCEv6_MessageKey", len=32)
ck_n+1   = HKDF-SHA256(ck_n, info="BCEv6_NextChain",  len=32)
iv_n     = random 96-bit GCM IV (chosen by the AEAD)
ct_n     = iv_n ‖ AES-256-GCM(mk_n, iv_n, plaintext, header_n) ‖ tag
```

`mk_n` is single-use. The chain key advances on every encrypt and on every decrypt
(including for skipped keys retained in the skipped map).

### 3.3 DH ratchet step (engaged via QR key exchange in Plan 4)

```
dh_out         = X25519(self_priv, remote_pub)
(rk_next, ck)  = HKDF-SHA256(salt=rk, ikm=dh_out, info="BCEv6_RkChain", len=64)
                 // first 32 bytes → rk_next, second 32 → ck
```

On `startSession(remote_pub)`, the sender derives `(rk_next, cks)` and emits the next
message with `flags.2` set and `dhPublic` set to its freshly-generated public. A receiver
that observes an unfamiliar `dhPublic` first performs an incoming DH ratchet step, then
rotates its own ephemeral keypair to seed the sending chain. (The DH UI is staged for
Plan 4; v1.0 ships the symmetric path.)

### 3.4 Out-of-order delivery

Receivers advance the chain to `header.messageNumber`, retaining `(dhPublic, k)` → `mk`
entries in a bounded map (`RatchetEngine.SKIP_WINDOW = 1000`, FIFO eviction of the oldest
entry). Late arrivals within the window decrypt via the stored `mk` and remove the entry.
Replays of an already-consumed `mk` fail because the entry is gone and the chain has
advanced past it.

## 4. Test vector

`RatchetEngineTest.cross-device round trip` asserts the core property: Alice bootstraps a
sending chain with salt `S_A`, encrypts `"hello"`, and Bob — who learns `S_A` from the
header and re-derives the same Argon2 key — decrypts it to `"hello"`. A second decrypt of
the same envelope against the advanced receiver state fails. With `plaintext = "hello"`
and no flags, the decoded payload is `86 (header) + 12 (IV) + 5 (ct) + 16 (tag) = 119`
bytes.

## 5. Compatibility

There is no in-app v3/v4/v5 → v6 migration. Existing ciphertext will not decrypt under
v6; `MIGRATION_11_12` drops the legacy `ratchet_state` table and rebuilds it for the v6
schema. v6 bootstraps fresh from the next encrypt/decrypt.
