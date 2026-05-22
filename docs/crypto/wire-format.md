# BarCodeNcrypt v5 wire format

This document is the canonical reference for the on-wire format of v5 encrypted messages.
Round-trip unit tests assert against the test vector in §4.

## 1. Token

```
~BCEv5~<base64>
```

- Prefix is the literal seven characters `~BCEv5~`.
- `<base64>` uses the URL-safe alphabet (`A-Z a-z 0-9 - _`) with no padding and no
  line breaks.
- The base64 decodes to a binary payload of `[ header || ciphertext ]`.

## 2. Binary header (big-endian, multi-byte fields)

| Field                  | Size | Notes                                                   |
| ---------------------- | ---: | ------------------------------------------------------- |
| `magic`                |    4 | ASCII `"BCE5"` (`0x42 0x43 0x45 0x35`)                  |
| `version`              |    1 | `0x05`                                                  |
| `flags`                |    1 | bit 0: `has_ttl`, bit 1: `has_open_count`, bit 2: `dh_ratchet_step` |
| `contactIdHash`        |   16 | First 16 bytes of `SHA-256(contact.lookupKey)`          |
| `dhPublic`             |   32 | Sender's current X25519 public key (zero when symmetric only)  |
| `previousChainMessages` | 4   | Message count in the previous sending chain (`pn`)      |
| `messageNumber`        |    4 | Position within the current sending chain (`n`)         |
| `ttlMs`                |    8 | Present iff `flags.0`                                   |
| `openCountMax`         |    4 | Present iff `flags.1`                                   |
| `timestampMs`          |    8 | `System.currentTimeMillis()` at encrypt time            |
| `nonce`                |   12 | `HMAC-SHA256(mk, "BCEv5_Nonce" ‖ n_be32)[0..12)`        |

Fixed header size (no flags set): **82 bytes**.
With both TTL and `openCount`: **94 bytes**.

The whole encoded header (bytes `0..endOfNonce`, inclusive) is fed to AES-GCM as
`associatedData`. Flipping any bit in the header therefore fails the GCM tag, which
the receiver surfaces as a generic decryption failure.

## 3. Cryptographic construction

### 3.1 Bootstrap (first encrypt/decrypt for a contact)

```
keyMaterial   = barcode_bytes ‖ password_bytes_or_empty
bootstrap_key = Argon2id(keyMaterial, perContactSalt,
                         t=3, m=64 MiB, p=2, len=32)
rk_0  = HKDF-SHA256(bootstrap_key, info="BCEv5_Root",       len=32)
cks_0 = HKDF-SHA256(bootstrap_key, info="BCEv5_SendChain",  len=32)
ckr_0 = HKDF-SHA256(bootstrap_key, info="BCEv5_RecvChain",  len=32)
```

Both sides bootstrap from the same `(barcode, password, salt)` triple, so the initial
chain keys match. `perContactSalt` is 16 bytes of `SecureRandom` per contact, persisted
in `ratchet_state.salt`.

### 3.2 Symmetric ratchet step

```
mk_n     = HKDF-SHA256(cks_n, info="BCEv5_MessageKey", len=32)
cks_n+1  = HKDF-SHA256(cks_n, info="BCEv5_NextChain",  len=32)
nonce_n  = HMAC-SHA256(mk_n, "BCEv5_Nonce" ‖ be32(n))[0..12)
ct_n     = AES-256-GCM_encrypt(mk_n, nonce_n, plaintext, header_n)
```

`mk_n` is single-use. The chain key advances on every encrypt and on every decrypt
(including for skipped keys retained in the skipped map).

### 3.3 DH ratchet step (engaged via QR key exchange in Plan 4)

```
dh_out         = X25519(self_priv, remote_pub)
(rk_next, ck)  = HKDF-SHA256(salt=rk, ikm=dh_out, info="BCEv5_RkChain", len=64)
                 // first 32 bytes → rk_next, second 32 → ck
```

On `startSession(remote_pub)`, the sender derives `(rk_next, cks)` and emits the next
message with `flags.2` set and `dhPublic` set to its freshly-generated public.

A receiver that observes an unfamiliar `dhPublic` first performs an incoming DH ratchet
step (deriving `(rk', ckr')`), then rotates its own ephemeral keypair to seed the
sending chain.

### 3.4 Out-of-order delivery

Receivers advance the chain to `header.messageNumber`, retaining `(dhPublic, k)` → `mk`
entries in a bounded map (`RatchetEngine.SKIP_WINDOW = 1000`, FIFO eviction). Late
arrivals within the window decrypt via the stored `mk` and remove the entry. Replays
of an already-consumed `mk` fail since the entry is gone.

## 4. Test vector

Inputs:

- `barcode      = "1234567890123"` (UTF-8 bytes)
- `password     = null`
- `salt         = 0x01 0x02 … 0x10` (16 bytes, byte = (i + 1) for i in 0..15)
- `contactIdHash = 0x07` × 16
- `plaintext    = "hello"`
- `ttlMs        = null`
- `openMax      = null`

After bootstrap, the first encrypt produces an envelope whose decoded payload is
**82 + 21 = 103 bytes** (16-byte GCM tag + 5-byte ciphertext = 21 bytes appended to the
82-byte header). Round-tripping through `RatchetEngine.encrypt` then
`RatchetEngine.decrypt` yields `"hello"`. The unit test
`RatchetEngineTest.round trip simple message` asserts this.

Decrypting the *same* envelope twice against a fresh receiver state must fail the
second time (the chain key advanced past `n=0` after the first decrypt).

## 5. Compatibility

There is no in-app v3 → v5 or v4 → v5 migration. Existing v3/v4 ciphertext will not
decrypt under v5; the database migration `MIGRATION_10_11` drops the legacy state
tables wholesale. v1.0 ships v5 only.
