# Plan 5 — Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock the toolchain to stable versions. Migrate `net.zetetic:android-database-sqlcipher` to the current `net.zetetic:sqlcipher-android` artifact. Turn on R8 minification for release with a vetted keep-rules file. Rewrite the docs to match the code Plans 1–4 actually produced. Remove placeholder docs from `INDEX.md`.

**Architecture:** No code changes beyond gradle pins, one artifact swap, R8 rules, and the manifest tweak required by the new SQLCipher artifact. The bulk of this plan is documentation correctness.

**Tech Stack additions:** none. **Removals:** stale `androidx.appcompat` (unused). **Downgrades:** AGP 9 preview → 8.7.x stable; Kotlin 2.3.x → 2.1.x stable; Gradle 9 → 8.10.x; build-tools 36 rc → 35 stable; NDK 29 rc → 27 stable; targetSdk 36 → 35.

**Reading dependencies:**
- Plans 1–4 complete. Plan 5 is the final consolidation pass.

---

## File structure

### Created
- `app/proguard-rules.pro` — populated with rules for Hilt, Room, Tink, Gson model classes, Firebase, AzNavRail.

### Modified
- `gradle/libs.versions.toml` — toolchain pins; SQLCipher artifact swap; drop unused deps.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.10.x.
- `app/build.gradle.kts` — `compileSdk = 35`, `targetSdk = 35`, `buildToolsVersion = "35.0.1"`, `ndkVersion = "27.2.12479018"`, `isMinifyEnabled = true` for release, dependency import alias change for SQLCipher.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt` — import `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` instead of `net.sqlcipher.database.SupportFactory`.
- `README.md` — rewritten to describe the v5 protocol, the actual screens, and the actual install flow.
- `CHANGELOG.md` — purge fictional `[Unreleased]`; new `[2.0.0]` entry for the post-Plans-1–4 release.
- `docs/INDEX.md` — drop links to deleted/placeholder docs.
- `docs/performance.md` — replace `BCE::v…` with `~BCEv5~`, update permissions list.
- `docs/data_layer.md` — describe the v5 protocol (Argon2id, AAD-authenticated header, no master persisted, skipped-key window).
- `docs/auth.md` — Firebase Sign-In + local Argon2id password, unified through `AuthManager`.
- `docs/try_it_mode.md` — describe the actual flow (if Try-It exists post-Plan 2) or note removal.
- `docs/screens.md` — list the actual screens: Onboarding, Home, Compose, ContactDetail, Scanner, KeyExchange, Settings.
- `docs/FILE_DESCRIPTIONS.md` — populated with real source-file descriptions.
- `docs/workflow.md` — accurate end-to-end flows.
- `docs/UI_UX.md` — accurate rail behavior.
- `docs/task_flow.md` — accurate flows.

### Deleted
- `docs/conduct.md`, `docs/fauxpas.md`, `docs/testing.md`, `docs/misc.md` — placeholders. Remove from `INDEX.md` first.

---

## Task 1: Pin Gradle wrapper to 8.10.x

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Update**

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
```
(Keep other lines unchanged.)

- [ ] **Step 2: Verify**

```bash
./gradlew --version
```
Expected: Gradle 8.10.2 (after first wrapper bootstrap).

- [ ] **Step 3: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties
git commit -m "build: pin Gradle wrapper to 8.10.2"
```

---

## Task 2: Pin AGP, Kotlin, KSP, build-tools, NDK, SDK levels

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: libs.versions.toml — replace preview versions**

```toml
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
```

- [ ] **Step 2: app/build.gradle.kts — replace preview SDK levels and toolchain**

```kotlin
android {
    namespace = "com.hereliesaz.barcodencrypt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hereliesaz.barcodencrypt"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }
    ...
    buildToolsVersion = "35.0.1"
    ndkVersion = "27.2.12479018"
}
```

- [ ] **Step 3: Sync + assemble**

```bash
./gradlew :app:assembleDebug --stacktrace
```
Expected: green. If a transitive dep complains about minSdk/compileSdk being lower than what it requires, surface that as a follow-up task.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: pin AGP 8.7.3 + Kotlin 2.1.0 + SDK 35; drop preview toolchain"
```

---

## Task 3: Migrate SQLCipher to `net.zetetic:sqlcipher-android`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt`

- [ ] **Step 1: libs.versions.toml — swap module**

```toml
sqlcipher = "4.6.1"
…
sqlcipher-android = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher" }
```

- [ ] **Step 2: AppDatabase.kt — switch factory import**

```kotlin
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
…
fun getDatabase(context: Context, passphrase: ByteArray): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase.copyOf())
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "barcodencrypt_database")
            .openHelperFactory(factory)
            .addMigrations(/* existing migrations */)
            .build()
            .also { INSTANCE = it }
    }
}
```

- [ ] **Step 3: Add `System.loadLibrary("sqlcipher")` to BarcodeApplication.onCreate**

```kotlin
override fun onCreate() {
    super.onCreate()
    System.loadLibrary("sqlcipher")
    …
}
```

(Only needed if the new artifact requires it; verify against the upstream release notes for the version chosen.)

- [ ] **Step 4: Build + smoke test on emulator**

```bash
./gradlew :app:installDebug
```
Walk Onboarding → set password → Home. If the DB doesn't open, the keying call has changed shape; consult `sqlcipher-android` upstream docs.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/BarcodeApplication.kt
git commit -m "deps: migrate to net.zetetic:sqlcipher-android (was android-database-sqlcipher)"
```

---

## Task 4: Drop unused deps

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Scan for unused imports**

```bash
grep -rn "androidx.appcompat" app/src/main || echo "UNUSED"
```
Expected: `UNUSED`. If `appcompat` is genuinely not used, drop both the library alias and the implementation line.

Same for `androidx.test:monitor` (only test-time) and any other dep that grep can't locate in `app/src/main` or `app/src/test`.

- [ ] **Step 2: Remove from libs.versions.toml and app/build.gradle.kts**

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: green.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "deps: drop unused libraries"
```

---

## Task 5: Drop explicit pins shadowed by BOMs

`firebase-auth:24.1.0` and `material3:1.4.0` are pinned alongside their BOMs. Remove the explicit pins so the BOMs control versions.

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Remove `firebaseAuth` and pinned `material3`**

```toml
# delete:
# firebaseAuth = "24.1.0"
# material3 = "1.4.0"

# Update aliases:
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
material3 = { module = "androidx.compose.material3:material3" }
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: green. The BOMs resolve concrete versions.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "deps: defer firebase-auth and material3 to their BOMs"
```

---

## Task 6: Enable R8 minification for release with keep rules

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Enable in release build type**

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

- [ ] **Step 2: Populate keep rules**

```proguard
# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# --- Tink ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Argon2kt ---
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.libraries.identity.googleid.** { *; }

# --- ML Kit barcode scanning ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- ZXing ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Gson models (data classes serialized by Gson) ---
# RatchetStateEntity skippedJson uses Gson; data classes need fields preserved.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepnames class com.hereliesaz.barcodencrypt.data.** { *; }

# --- AzNavRail ---
-keep class com.hereliesaz.aznavrail.** { *; }
-dontwarn com.hereliesaz.aznavrail.**

# --- SQLCipher ---
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# --- Kotlin metadata ---
-keep class kotlin.Metadata { *; }
```

- [ ] **Step 3: Assemble release**

```bash
./gradlew :app:assembleRelease
```
Expected: green. (Release build will be unsigned — that's fine; signing config is out of scope.)

- [ ] **Step 4: Install and smoke-test the release build**

```bash
./gradlew :app:installRelease
```
Walk through: open the app, set password, navigate every rail item, send a Compose message, decrypt via Scanner. Any `ClassNotFoundException` / `NoSuchMethodError` in the release build but not debug → add a targeted keep rule and retry.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro
git commit -m "build: enable R8 minification for release with curated keep rules"
```

---

## Task 7: Rewrite README.md to match reality

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace the README body**

Keep the opening "Dwayne Johnson" story for tone (the owner clearly wants it), but rewrite the "How It Works" and "Technical Deep Dive" sections.

Outline of the new content:
- Concept (unchanged).
- "Status" section: this is research/hobby software, not vetted by a third-party crypto audit, threat model documented in `docs/security.md`.
- "How it works" — Onboarding (set password, optionally Google Sign-In) → Add Contact (assign barcode + optional password, exchange QR keys with peer) → Compose / Decrypt flows with real screen names.
- "Architecture": single Activity, Compose, Hilt; v5 Double Ratchet (Argon2id + HKDF + X25519 + AES-256-GCM); SQLCipher-encrypted DB; foreground services with channel notifications.
- "Build & test" — `bash setup_env.sh && ./gradlew :app:assembleDebug`; how to run tests; what CI runs.
- "Security" — link to a new top-level `docs/security.md` (Task 13) with the threat model and known limitations.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: rewrite README to match v5 reality"
```

---

## Task 8: Rewrite CHANGELOG.md

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Drop the fictional `[Unreleased]`**

Replace the `[Unreleased]` section with the actual landed changes for `[2.0.0] - YYYY-MM-DD`:
- Implemented Signal-style Double Ratchet (v5) with Argon2id + HKDF + X25519 + AES-256-GCM.
- AAD-authenticated binary message header (`~BCEv5~` envelope).
- Bounded skipped-key window for out-of-order delivery.
- SQLCipher-encrypted database (key derived from user password via Argon2id; never empty).
- Foreground service notifications for accessibility and overlay.
- `FLAG_SECURE` on plaintext-bearing windows.
- Real Autofill service with trampoline.
- Accessibility allowlist (configurable in Settings).
- Hilt-based DI throughout.
- Single AzNavRail-based app shell.

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: truthful CHANGELOG; remove [Unreleased] fiction"
```

---

## Task 9: Rewrite `docs/data_layer.md`

**Files:**
- Modify: `docs/data_layer.md`

- [ ] **Step 1: Replace body**

Sections:
1. **Storage at rest** — Room with SQLCipher, key = Argon2id(userPassword, perInstallSalt). The salt persists in `barcodencrypt_prefs`. The key never persists.
2. **Tables** — `contacts (id, lookupKey, name)`, `barcodes (id, contactLookupKey, name, encryptedValue, iv, …)`, `ratchet_state (contactLookupKey PK, salt, rk, cks, ckr, dhSelfPriv, dhSelfPub, dhRemotePub, ns, nr, pn, skippedJson)`, `opened_messages`.
3. **v5 protocol** — bootstrap via Argon2id, symmetric chain via HKDF, optional DH ratchet via X25519, AAD-authenticated binary header. Reference the layout diagram from the spec.
4. **Forward secrecy** — explain that `masterKey` is not persisted; only the current chain state lives in the DB; skipped keys are bounded.
5. **Sender-controlled security** — TTL and open-count are part of the AAD; tampering fails GCM.

- [ ] **Step 2: Commit**

```bash
git add docs/data_layer.md
git commit -m "docs: data_layer.md describes v5 protocol and current schema"
```

---

## Task 10: Rewrite `docs/auth.md`

**Files:**
- Modify: `docs/auth.md`

- [ ] **Step 1: Document the actual flow**

- App launches; if `AuthManager.isPasswordSet()` is false, OnboardingScreen prompts for a new password OR offers Google Sign-In (Credential Manager → Firebase Auth).
- Setting a password derives a `passphraseKey` via Argon2id and unlocks the DB.
- Google Sign-In adds a Firebase user but does NOT replace the password requirement (DB still needs the passphraseKey).
- Logout clears Firebase + Keystore-wrapped password.

- [ ] **Step 2: Commit**

```bash
git add docs/auth.md
git commit -m "docs: auth.md describes Firebase + Argon2id local-password flow"
```

---

## Task 11: Update `docs/performance.md`

**Files:**
- Modify: `docs/performance.md`

- [ ] **Step 1: Patch claims**

- Replace `BCE::v…` → `~BCEv5~`.
- Permissions: `CAMERA`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `BIND_ACCESSIBILITY_SERVICE`, `BIND_AUTOFILL_SERVICE`.
- AccessibilityService allowlist limits scanning to the user-configured set.

- [ ] **Step 2: Commit**

```bash
git add docs/performance.md
git commit -m "docs: performance.md uses ~BCEv5~ and the real permission list"
```

---

## Task 12: Rewrite `docs/screens.md`, `docs/UI_UX.md`, `docs/task_flow.md`, `docs/workflow.md`

**Files:**
- Modify: each file above.

- [ ] **Step 1: docs/screens.md**

List the screens: `OnboardingScreen`, `HomeScreen`, `ComposeScreen`, `ContactDetailScreen`, `ScannerScreen`, `KeyExchangeScreen`, `SettingsScreen`. One paragraph per screen describing purpose, what its ViewModel injects, and the typed-args it consumes.

- [ ] **Step 2: docs/UI_UX.md**

Describe the AzNavRail shell: docking, current-route highlighting, accessibility-allowlist editor. Remove the "mock chat" claims.

- [ ] **Step 3: docs/task_flow.md**

Concrete flows:
- Enable accessibility → see overlay on detected token → tap → ScannerActivity scans barcode → decrypted plaintext appears (with `FLAG_SECURE`).
- Compose → select contact → select barcode → set TTL/openCount → encrypt → copy `~BCEv5~…` to clipboard or paste in-place via the encryption overlay.
- Key exchange → KeyExchangeScreen shows QR, scans peer QR, ratchet advances.

- [ ] **Step 4: docs/workflow.md**

Match `task_flow.md` but from the developer-onboarding perspective (clone → setup_env.sh → assembleDebug → connect device → installDebug → walk through).

- [ ] **Step 5: Commit**

```bash
git add docs/screens.md docs/UI_UX.md docs/task_flow.md docs/workflow.md
git commit -m "docs: rewrite screens/UI/flow/workflow to match shipped app"
```

---

## Task 13: Add `docs/security.md`

**Files:**
- Create: `docs/security.md`

- [ ] **Step 1: Write the threat model**

Sections:
1. **What this protects against** — passive on-device DB dump; tampered messages in transit; replay; expired-TTL viewing; open-count exhaustion; trivially-guessable barcode-only keys (via Argon2id).
2. **What this does not protect against** — a compromised device with root, screen-recording malware (FLAG_SECURE is best-effort), accessibility-service abuse by other apps in the allowlist, the user voluntarily forwarding plaintext.
3. **Known limitations** — no peer authentication beyond shared barcode; pre-Plan-2 messages cannot be decrypted; backup is disabled; Google Sign-In does not back up keys (intentional).
4. **Audit status** — not externally audited.

- [ ] **Step 2: Link from README**

Add a "Security" section to `README.md` linking to `docs/security.md`.

- [ ] **Step 3: Commit**

```bash
git add docs/security.md README.md
git commit -m "docs: add threat-model security.md and link from README"
```

---

## Task 14: Rewrite `docs/FILE_DESCRIPTIONS.md`

**Files:**
- Modify: `docs/FILE_DESCRIPTIONS.md`

- [ ] **Step 1: Populate with real coverage**

Tree-walk `app/src/main/java/com/hereliesaz/barcodencrypt/` and produce a one-line description per file, grouped by package: `crypto/`, `data/`, `di/`, `services/`, `ui/`, `ui/composable/`, `util/`, `viewmodel/`.

A representative shape:
```
crypto/EncryptionManager.kt      — façade over RatchetEngine; loads/saves ratchet state per contact.
crypto/RatchetEngine.kt          — pure double-ratchet primitives (bootstrap, encrypt, decrypt, DH step).
crypto/MessageHeader.kt          — binary header encode/decode.
crypto/MessageEnvelope.kt        — wire-format `~BCEv5~<base64>` encode/decode.
crypto/Argon2.kt                 — Argon2id wrapper with fixed params.
crypto/RatchetState.kt           — immutable state value type.
data/AppDatabase.kt              — Room with SQLCipher; v11 schema.
data/Contact.kt / Barcode.kt …   — entities.
data/RatchetStateEntity.kt + …Dao + …Repository — persists per-contact ratchet.
di/CryptoModule.kt               — Hilt module attachment point.
di/DatabaseModule.kt             — provides DB + DAOs + AuthManager + prefs.
services/MessageDetectionService.kt — accessibility-based on-screen detection.
services/OverlayService.kt       — foreground overlay window.
services/BarcodeAutofillService.kt — autofill provider; trampoline for scan.
services/EditableTargetRegistry.kt — replaces the AccessibilityNodeHolder global.
ui/App.kt                        — top-level AzNavRail shell.
ui/AppNavHost.kt                 — typed-arg NavHost.
ui/{Onboarding,Home,Compose,ContactDetail,Scanner,KeyExchange,Settings}Screen.kt — screens.
viewmodel/*ViewModel.kt          — @HiltViewModels for each screen.
util/AuthManager.kt              — Firebase Auth + Keystore-wrapped local password.
util/Hashing.kt                  — sha256.
util/DecryptionAttemptManager.kt — failed-attempt rate-limit per ciphertext.
util/SecureWindow.kt             — markSecure() extension.
…
```

- [ ] **Step 2: Commit**

```bash
git add docs/FILE_DESCRIPTIONS.md
git commit -m "docs: FILE_DESCRIPTIONS covers actual sources"
```

---

## Task 15: Delete placeholder docs + clean up `docs/INDEX.md`

**Files:**
- Delete: `docs/conduct.md`, `docs/fauxpas.md`, `docs/testing.md`, `docs/misc.md`
- Modify: `docs/INDEX.md`

- [ ] **Step 1: Delete files**

```bash
git rm docs/conduct.md docs/fauxpas.md docs/testing.md docs/misc.md
```

- [ ] **Step 2: Patch `docs/INDEX.md`**

Replace with a curated index that links only to existing, real docs:
```markdown
# BarCodeNcrypt Documentation Index

- [README](../README.md) — start here
- [CHANGELOG](../CHANGELOG.md) — release history
- [Security model](security.md) — threat model and limitations
- [Data layer](data_layer.md) — DB schema and v5 protocol
- [Auth flow](auth.md) — Firebase + local password
- [Screens](screens.md) — UI inventory
- [User flows](task_flow.md) — end-to-end user journeys
- [Developer workflow](workflow.md) — clone, build, run
- [UI/UX](UI_UX.md) — AzNavRail shell behavior
- [Performance notes](performance.md) — services & permissions
- [File descriptions](FILE_DESCRIPTIONS.md) — per-file map of the codebase

Remediation plans (history): [docs/superpowers/](superpowers/)
```

- [ ] **Step 3: Commit**

```bash
git add docs/INDEX.md
git commit -m "docs: prune placeholder docs; INDEX.md links only to real ones"
```

---

## Task 16: Final CI run + verification

- [ ] **Step 1: Run the full CI workflow locally**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
Expected: all green.

- [ ] **Step 2: Sanity check for the audit's old findings**

```bash
grep -rn "BarcodeApplication).database\|ViewModelProvider.Factory\|EncryptedScriptLog\|encryptV3\|decryptV3\|BCE::v" app/src
```
Expected: no hits.

```bash
grep -rn "AccessibilityNodeHolder\|password_prefs" app/src/main
```
Expected: no hits.

- [ ] **Step 3: Open the final PR**

```bash
gh pr create --title "Plan 5: Cleanup — toolchain, deps, R8, docs" \
             --body "Final consolidation pass. See docs/superpowers/plans/2026-05-21-plan-5-cleanup.md."
```

---

## Acceptance criteria

- ✅ Gradle wrapper at 8.10.x. AGP at 8.7.x. Kotlin at 2.1.x. No `rc` suffixes anywhere.
- ✅ compileSdk/targetSdk = 35.
- ✅ `net.zetetic:sqlcipher-android:4.6.x` in use; `android-database-sqlcipher` gone.
- ✅ `appcompat` and other unused deps removed.
- ✅ `firebase-auth` and `material3` versions come from their BOMs (no explicit pin).
- ✅ `isMinifyEnabled = true` for release; `:app:assembleRelease` produces an installable APK; the installed APK passes the smoke test.
- ✅ `proguard-rules.pro` contains keep rules for Hilt, Room, Tink, Argon2kt, Firebase, ML Kit, ZXing, AzNavRail, SQLCipher, and Gson-serialized model classes.
- ✅ README, CHANGELOG, and all `docs/*.md` match the shipped app. The placeholder docs are gone. `docs/INDEX.md` links only to existing docs.
- ✅ `docs/security.md` exists and is linked from README.
- ✅ `docs/FILE_DESCRIPTIONS.md` covers actual source files.

## Risk register

- **AGP 8.7 / Kotlin 2.1 may require small Compose Compiler plugin adjustments.** If the `org.jetbrains.kotlin.plugin.compose` plugin version doesn't match Kotlin 2.1.x, bump it.
- **R8 may produce a runtime crash that debug doesn't.** Iterate keep rules per Task 6 Step 4.
- **`sqlcipher-android` artifact may have different init requirements.** Documented Step 3 of Task 3; verify against upstream release notes for the exact version chosen.
- **Doc rewrites are easy to under-do.** If a doc is rewritten but still misleading, fix it. Better to ship fewer accurate docs than many wrong ones.

## What's deliberately NOT in this plan

- Signing configuration for release builds (out of scope; will require a per-developer keystore).
- Migration off `androidx.security:security-crypto` (it was already deleted in Plan 1 with the `PasswordViewModel` rewrite; no further migration needed).
- Translations / i18n.
- ML Kit replacement.
- A formal third-party crypto audit (would be a Plan 6, not in scope here).
