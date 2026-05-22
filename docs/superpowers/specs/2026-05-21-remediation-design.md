# BarCodeNcrypt Remediation — Design Spec

**Date:** 2026-05-21
**Status:** Approved (audit + 4 decisions)
**Author:** Claude Code (Opus 4.7, 1M context), confirmed by repo owner

## 1. Context

A full audit of the repository on 2026-05-21 (see conversation transcript and `docs/superpowers/specs/2026-05-21-audit-findings.md` if archived) established that the codebase is in a broken mid-refactor state:

- **Build is red.** `processDebugGoogleServices` fails (only `google-services.template.json` is committed). Even with that fixed, ~10 call sites reference an `EncryptionManager` static API (`encryptV3`, `decryptV3`, `getIkm`, `kdfRk`, `generateV3KeyPair`, `init()`, `OPTION_SINGLE_USE`, `sha256(...)`, `getV3PublicKey`) that does not exist on the `@Singleton class` defined in `crypto/EncryptionManager.kt`. `ui/App.kt` imports `ContactsViewModel` and `TutorialViewModel` that do not exist.
- **Two parallel app shells.** `MainActivity` loads `ui/App.kt` (AzNavRail shell, broken). `ui/Navigation.kt` + `MainScreen.kt` + `AppScaffold.kt` define a complete Material3 `NavigationRail` shell that is never reached. AzNavRail is implemented twice (`ui/App.kt` and `ui/composable/AppNavRail.kt`).
- **Two parallel auth/preference paths.** `BarcodeApplication.kt:19-23` lazily creates an `AuthManager` from `"app_prefs"`. `di/DatabaseModule.kt:88-100` provides another `AuthManager` from `"barcodencrypt_prefs"`. `PasswordViewModel` creates a third `EncryptedSharedPreferences` file `"password_prefs"`. Hilt is configured but not enabled (`BarcodeApplication` lacks `@HiltAndroidApp`, no `@AndroidEntryPoint`s, 8 hand-rolled `*ViewModelFactory` classes coexist with `@HiltViewModel`).
- **The cryptography claims do not hold up.**
  - **No forward secrecy.** `EncryptedScriptLog.masterKey` is persisted alongside `currentChainKey` and `getKeyForMessageNumber` re-derives any past key from the persisted master (`EncryptedScriptLogManager.kt:51-61`). A single DB dump unrolls every past and future message.
  - **Master key is raw barcode bytes.** No KDF, no salt, no work factor (`EncryptedScriptLogManager.kt:30-31`). EAN-13 ≈ 43 bits.
  - **Unauthenticated header.** `EncryptionManager.kt:55-67, 79-103` calls AES-GCM with `associatedData = null`. The header carrying `ttl`, `openCount`, `timestamp`, `messageNumber` is tamperable. The sender-controlled "time bomb" is trivially bypassable.
  - **Password-protected mode is theater.** Password only hashes into the row lookup; the chain master is still `barcode.value.toByteArray()`. Correct barcode without password yields the same key.
  - **SQLCipher silently keyed with empty string.** `DatabaseModule.provideAppDatabase` passes `authManager.getPassword()` to `AppDatabase.getDatabase`; `getPassword()` returns `""` on any decryption failure or unset state (`AuthManager.kt:331, 334`).
- **Header / detector mismatch.** `EncryptionManager` emits `<base64JSONHeader>.<base64Cipher>` (no prefix). `MessageParser` only matches `~BCEv3~` / `~BCEv4~`. New messages produced by this app cannot be detected by this app.
- **Service issues.** Both services are declared `foregroundServiceType="specialUse"` but never call `startForeground` and never create a notification channel. On Android 14 this throws `ForegroundServiceDidNotStartInTimeException`. `BarcodeAutofillService` is not declared in the manifest at all and its `FillResponse` body is commented out. `AccessibilityNodeInfo` is leaked across processes via the `object AccessibilityNodeHolder` singleton, and `MessageDetectionService.kt:51` recycles a node that is then read by `OverlayService`.
- **Documentation lies.** `CHANGELOG.md`'s `[Unreleased]` advertises a Tink Double Ratchet with X25519, a `MockMessagesActivity`, and a `ContactDetailActivity` — none of which exist. `docs/performance.md` says the detector scans for `BCE::v…` (it doesn't). Half of `docs/` files are one-line placeholders.
- **No CI build/test workflow.** Only `jules.yml` (issue trigger) and `backup.yml` (manual). Nothing prevents regressions from landing on master.

## 2. Decisions (locked)

| # | Decision | Choice | Rationale |
|---|---|---|---|
| D1 | App shell | **AzNavRail (ui/App.kt path)** | Owner's own library; explicit instruction to read its guide. |
| D2 | V3 / Double Ratchet / Tink-X25519 / KeyExchange stubs | **Implement them for real** | Matches the security marketing; correctly delivers the forward secrecy claim. |
| D3 | Backward compatibility for already-encrypted messages | **Break BC freely** | Nothing in production yet; lets us redesign the header (with AAD), KDF (Argon2id), and key storage (no master persisted) properly. |
| D4 | At-rest DB encryption | **Keep SQLCipher, fix the key** | Closest to current code; passphrase will be derived from a Keystore-wrapped random secret unlocked by user auth, never empty. |

## 3. Target architecture

### 3.1 Identity & key model
- **User authentication layer.** Single `AuthManager` (Hilt `@Singleton`) handles both Google Sign-In (Credential Manager + Firebase Auth) and a local master password fallback. No second `PasswordViewModel`-owned `EncryptedSharedPreferences` file.
- **Local secret unlocking.** On first launch the user sets a master password (or signs in with Google + sets a recovery password). The password is fed into Argon2id with a per-install random salt to derive a 32-byte `passphraseKey`. `passphraseKey` is used as the SQLCipher passphrase. `passphraseKey` is **not** stored on disk; it lives in memory for the session. A copy of the Argon2 salt and parameters lives in plain SharedPreferences (`barcodencrypt_prefs`). A second copy of the `passphraseKey`, wrapped with a Keystore-resident AES-256-GCM key configured with `setUserAuthenticationRequired(true)` and a short validity window, may be cached to skip re-prompting between short app reopens.
- **Per-contact ratchet state.** Lives in `contacts` table. Holds the X25519 identity key pair, the current root key, sending/receiving chain keys, send/receive message numbers, previous-sending-chain message number, and a serialized map of skipped message keys (capped). No `masterKey` column anywhere.
- **Per-barcode initial keying material.** `barcodes` table holds the *encrypted* barcode payload (Keystore-wrapped AES-GCM, IV in the same row). The barcode's plaintext value, once scanned/decrypted for use, is mixed into the ratchet's initial root via Argon2id(barcode_bytes ‖ password_bytes, per-contact-salt). Both barcode and password are zeroed on the JVM heap as soon as the ratchet is updated.

### 3.2 Cryptographic protocol (v5)
- **Identity keys.** X25519 via `com.google.crypto.tink.subtle.X25519`.
- **KEM/KDF.** HKDF-SHA-256 via `com.google.crypto.tink.subtle.Hkdf` for chain-key advancement, with distinct `info` strings (`BCEv5_RootChain`, `BCEv5_SendingChain`, `BCEv5_MessageKey`).
- **Password mixing.** Argon2id (via `com.lambdapioneer.argon2kt:argon2kt` — small wrapper around Argon2 reference C lib, ABI-clean Android artifact). Parameters: time cost 3, memory cost 65536 KiB, parallelism 2, output 32 bytes. Salt 16 bytes per contact, stored in the contacts row.
- **AEAD.** AES-256-GCM via Tink's `AesGcmJce`. The canonicalized header (a fixed binary layout, not Gson) is passed as the `associatedData`.
- **Wire format.**
  ```
  ~BCEv5~<base64-of-binary-message>
  ```
  Binary message layout (big-endian where multi-byte):
  ```
  magic     : 4 bytes   = "BCE5"
  version   : 1 byte    = 0x05
  flags     : 1 byte    (bit 0: has_ttl, bit 1: has_open_count, bit 2: dh_ratchet_step)
  contact_id_hash : 16 bytes (BLAKE2s-128 of contact lookupKey || installation_uuid)
  dh_public : 32 bytes  (current sender DH ratchet public key)
  prev_n    : 4 bytes   (previous sending chain message number)
  n         : 4 bytes   (this message number in current sending chain)
  ttl_ms    : 8 bytes   (present iff has_ttl)
  open_max  : 4 bytes   (present iff has_open_count)
  timestamp : 8 bytes   (Unix ms, sender clock — for TTL only)
  nonce     : 12 bytes  (per-message, deterministic from message_key || n)
  ciphertext: N bytes
  tag       : 16 bytes  (AES-GCM auth tag, includes header as AAD)
  ```
  The whole binary blob is the AAD for the GCM call; the receiver re-canonicalizes the header bytes before calling `decrypt`. Any tampered field fails authentication.
- **TTL / open-count enforcement.** Receiver-side enforcement is unchanged in shape but now load-bearing because the values are authenticated. `OpenedMessage` table keyed by BLAKE2s-128 of `(contact_id_hash || dh_public || n)`. Decrement strategy and replay window are explicit (see Plan 2).
- **Skipped key handling.** Up to 1000 skipped message keys per chain step are persisted (capped per contact) so out-of-order messages still decrypt within a small window without breaking forward secrecy.
- **Forward secrecy.** Master keys are **never** persisted. The receiving chain key is overwritten as it advances. Identity keys persist; chain keys do not.

### 3.3 Detection / overlay flow
- `MessageDetectionService` matches `~BCEv5~[A-Za-z0-9+/=_-]+` and (for the BC break grace period that we are *not* taking) ignores legacy prefixes.
- The Accessibility service does **not** hand `AccessibilityNodeInfo` to other components. The text and bounds are extracted, the node is recycled immediately, and the bounds+text travel to `OverlayService` via the Intent. A new `OverlayCommand` data class is the only thing exchanged.
- `OverlayService` calls `startForeground` with a `NotificationChannel` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- `MessageDetectionService` also calls `startForeground` for the same reason (and to avoid Android 14 crashes).
- Plaintext-bearing Activities and overlay windows set `WindowManager.LayoutParams.FLAG_SECURE`.

### 3.4 Autofill
- `BarcodeAutofillService` declared in manifest with `BIND_AUTOFILL_SERVICE` permission and the autofill metadata resource.
- `onFillRequest` produces a `Dataset` whose presentation is a remote-views row "Scan with BarcodeNcrypt", with a `PendingIntent` to a `PasswordScannerTrampolineActivity` which is registered in the manifest, runs the scanner, derives the password from the barcode (no password mixing for this flow), and returns the dataset via `EXTRA_AUTHENTICATION_RESULT`.

### 3.5 Data backup
- `android:allowBackup="false"` until rules are tested.
- `backup_rules.xml` and `data_extraction_rules.xml` exclude: `database/barcodencrypt_database*`, `sharedpref/barcodencrypt_prefs.xml`, `sharedpref/password_prefs.xml` (during transition), `files/`.

## 4. Five-plan decomposition

Each plan produces a green build and a logical unit of value, with its own write → review → execute cycle.

### Plan 1 — Foundation (`2026-05-21-plan-1-foundation.md`)
**Goal:** Repository builds cleanly. Hilt is on. There is one `AuthManager`, one preferences file (during Plan 1; the unification with `PasswordViewModel`'s third pref file happens here). The Material3 NavigationRail shell, `ContactsViewModel`/`TutorialViewModel` ghosts, and the conflicting Try-It code paths are gone. A CI workflow runs `assembleDebug testDebugUnitTest lintDebug` on every PR. The `EncryptionManager` API is reconciled to a single instance-based interface with stubs for the V3 surface that will be filled in by Plan 2.
**Why it's first:** Nothing else can be safely tested until the build is green. Hilt activation removes the duplicate `AuthManager` problem that otherwise compounds with every subsequent change.
**Acceptance:** `./gradlew assembleDebug testDebugUnitTest lintDebug` exits 0 in CI on a fresh clone.

### Plan 2 — Crypto rewrite (`2026-05-21-plan-2-crypto.md`)
**Goal:** Replace the v4 symmetric chain with the v5 Double Ratchet described in §3.2. AAD-authenticated binary header. Argon2id KDF. Skipped-key storage. No master persisted. `MessageParser` matches what `EncryptionManager` emits. Comprehensive unit tests covering: round-trip encrypt/decrypt, tamper detection (every header field), TTL expiry (boundary), open-count exhaustion, out-of-order delivery within skip window, replay rejection, password-derived key separation.
**Acceptance:** `crypto/`, `crypto/model/`, `data/EncryptedScriptLog*` are gone or replaced; `data/Contact` carries ratchet state; unit tests pass; a manual instrumented round-trip on emulator works end-to-end.

### Plan 3 — Service & overlay hardening (`2026-05-21-plan-3-services.md`)
**Goal:** Both services call `startForeground` with notification channels and the `specialUse` justification property. `AccessibilityNodeHolder` is deleted; the Accessibility service only exchanges value types with `OverlayService`. `FLAG_SECURE` is set on every Activity and overlay window that can show plaintext. `BarcodeAutofillService` is registered in the manifest and produces a real `FillResponse`. `backup_rules.xml` and `data_extraction_rules.xml` exclude key material. `accessibility_service_config.xml` adds `packageNames` filter (configurable, defaults to allowlist of common messaging apps).
**Acceptance:** Manual emulator run on Android 14 shows: enabling the service does not crash, a foreground notification appears, screenshots of decrypted Activities are blank, AutofillManager lists BarcodeNcrypt as a provider.

### Plan 4 — AzNavRail consolidation (`2026-05-21-plan-4-aznavrail.md`)
**Goal:** Read the AzNavRail repository's complete usage guide (`https://github.com/HereLiesAz/AzNavRail`). Use AzNavRail as the chrome around the existing `AppNavigation` graph in `ui/Navigation.kt` (now the *only* graph after Plan 1 deletes the duplicates). Settings, Onboarding, Compose, Scanner, ContactDetail with typed args are all reachable via the rail. The duplicate `ui/composable/AppNavRail.kt` is removed in favor of one source of truth. AzNavRail dependency is pinned to a specific commit SHA in `libs.versions.toml`.
**Acceptance:** Every nav target reachable from the rail; no dead Composables; `ui/App.kt` is the only top-level shell.

### Plan 5 — Cleanup (`2026-05-21-plan-5-cleanup.md`)
**Goal:** Toolchain pinned to stable (AGP 8.7.x, Kotlin 2.1.x, Gradle 8.10.x, build-tools 35, NDK stable). Migrate `net.zetetic:android-database-sqlcipher:4.5.4` → `net.zetetic:sqlcipher-android:4.6.x`. Drop unused `androidx.appcompat`. Pin AzNavRail to a SHA. Enable R8 minification for release with keep rules for Room entities, Hilt, Gson model classes, and Tink. Rewrite `README.md`, `CHANGELOG.md` (truthful Unreleased), `docs/performance.md` (`~BCEv5~`), `docs/data_layer.md` (v5 protocol), `docs/auth.md` (Firebase + local password), `docs/try_it_mode.md` (actual flow), `docs/FILE_DESCRIPTIONS.md` (real source coverage), `docs/INDEX.md` (no broken/duplicate links). Delete placeholder docs (`conduct.md`, `fauxpas.md`, `testing.md` as one-liners) or replace with real content.
**Acceptance:** A reader of `README.md` + `docs/data_layer.md` can correctly predict what the code does. CHANGELOG matches what the binary actually delivers.

## 5. Sequencing & parallelism

```
Plan 1 (Foundation)
   │
   ├──> Plan 2 (Crypto)        ──┐
   ├──> Plan 3 (Services)      ──┤
   └──> Plan 4 (AzNavRail)     ──┤
                                 │
                            Plan 5 (Cleanup)
```

Plans 2/3/4 are independent after 1 and can run in parallel. Plan 5 should be the final pass because it consolidates the toolchain and rewrites docs to reflect what was just built.

## 6. Out of scope (deliberately)

- Real-time messaging transport. The app remains text-in / ciphertext-out.
- Multi-device session sync.
- Backup / restore UX for ratchet state (would require deriving a recoverable seed, which conflicts with the forward-secrecy goal).
- Replacing SQLCipher with field-level Tink (rejected in D4).
- Migrating off `androidx.security:security-crypto` (Plan 5 may flag; not required).
- ML Kit replacement for barcode scanning.
- Internationalization beyond what exists.

## 7. Glossary

- **AAD** — Additional Authenticated Data; the input to AES-GCM that is authenticated but not encrypted. Tampering with AAD fails the tag check.
- **Double Ratchet** — Signal-style protocol that combines a DH ratchet (forward secrecy per chain step) with a symmetric chain (one key per message). The reference is Marlinspike & Perrin, "The Double Ratchet Algorithm" (2016).
- **Forward secrecy** — A property where past message keys cannot be recovered from the current persistent state.
- **HKDF** — HMAC-based Key Derivation Function (RFC 5869).
- **Argon2id** — Password-hashing function, winner of the 2015 PHC. The id variant is the recommended default.
