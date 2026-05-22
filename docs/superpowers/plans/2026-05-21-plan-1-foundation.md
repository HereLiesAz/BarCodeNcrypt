# Plan 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `./gradlew assembleDebug testDebugUnitTest lintDebug` exit 0 on a fresh clone, with Hilt actually enabled, a single `AuthManager`, a single app shell, no ghost ViewModels, a single `EncryptionManager` API, and a CI workflow that runs the build on every PR.

**Architecture:** Strip the broken duplicate stacks. Activate Hilt end-to-end. Stub or delete the dead V3 surface (Plan 2 will replace it for real). Unify the three SharedPreferences files into one. Replace the Material3 NavigationRail shell with the AzNavRail shell (Plan 4 will then wire it to the real graph). Add a CI workflow.

**Tech Stack:** Hilt 2.59.2 (already declared), Kotlin 2.3.21 (already declared), Android Gradle Plugin 9.2.1 (kept as-is; Plan 5 pins to stable), Compose BOM 2026.05.01, Firebase BOM 34.13.0.

**Reading dependencies:** This plan assumes the reader has read `docs/superpowers/specs/2026-05-21-remediation-design.md`.

---

## File structure (what this plan touches)

### Created
- `.github/workflows/ci.yml` — runs build + tests + lint on PRs.
- `app/google-services.template.json` is already present; this plan adds a *generator* and a `.gitignore` rule so a real `google-services.json` is created by the CI workflow from a template + secret.
- `app/src/main/java/com/hereliesaz/barcodencrypt/di/CryptoModule.kt` — Hilt module that provides crypto-layer singletons including a one-time Tink `AeadConfig.register()` initializer.
- `app/src/main/java/com/hereliesaz/barcodencrypt/util/Hashing.kt` — extracts `sha256` so test code stops importing it through `EncryptionManager`.

### Modified
- `app/src/main/AndroidManifest.xml` — `allowBackup=false`; no other manifest changes in Plan 1 (services & autofill in Plan 3).
- `app/src/main/java/com/hereliesaz/barcodencrypt/BarcodeApplication.kt` — `@HiltAndroidApp`; drop `database` and `authManager` fields; move Firebase init into Hilt-initialized helpers.
- `app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt` — `@AndroidEntryPoint`; switch to `by viewModels()` without a Factory.
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt` — `@AndroidEntryPoint` (foreground notification work happens in Plan 3).
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt` — `@AndroidEntryPoint`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/BarcodeAutofillService.kt` — `@AndroidEntryPoint`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManager.kt` — drop the ghost static API; expose a single instance-based interface; expose stubs (throwing `NotYetImplementedError`) for the v5 ratchet methods that Plan 2 will implement.
- `app/src/main/java/com/hereliesaz/barcodencrypt/util/AuthManager.kt` — fold `db_password_hash` + Argon2id derivation in; `getPassphraseKey()` returns the real SQLCipher key (Argon2id-derived) or throws when not unlocked — never `""`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/di/DatabaseModule.kt` — provides a `SqlCipherKey` value object instead of routing through a raw `CharSequence`; the database is built lazily after unlock.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/AppDatabase.kt` — `getDatabase` takes `ByteArray`, not `CharSequence`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/BarcodeRepository.kt` — `@Singleton class @Inject constructor`; remove static `EncryptionManager.sha256` calls (use `Hashing.sha256`); convert LiveData to Flow.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/ContactRepository.kt` — `@Singleton class @Inject constructor`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/data/OpenedMessageRepository.kt` — `@Singleton class @Inject constructor`.
- All ViewModels under `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/` — `@HiltViewModel` + constructor injection.
- All Composable call sites using `viewModel()` / `ViewModelProvider.Factory` — switch to `hiltViewModel()` from `androidx.hilt.navigation.compose`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/util/DecryptionAttemptManager.kt` — replace `EncryptionManager.sha256` with `Hashing.sha256`.
- `app/src/test/java/com/hereliesaz/barcodencrypt/DecryptionAttemptManagerTest.kt` — update import to `Hashing` (call sites unchanged).
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/App.kt` — delete the broken `ContactsScreen`/`ContactDetailScreen`/`TutorialScreen`/`SettingsScreen` defined here; pivot to the real screens; rail stays as a placeholder (Plan 4 rewires it properly).
- `gradle.properties` — `android.useAndroidX=true` is presumed; this plan doesn't touch toolchain pins (Plan 5).

### Deleted
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/Navigation.kt` — duplicate shell with Material3 NavigationRail.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/MainScreen.kt` — only consumed by the dead shell.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppScaffold.kt` — Material3 NavigationRail wrapper, only consumed by `Navigation.kt`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordScannerTrampolineActivity.kt` — unregistered in manifest, unreachable; Plan 3 will reintroduce a proper trampoline.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/KeyExchangeActivity.kt` — unregistered; Plan 2 reimplements the real DH key exchange.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/KeyExchangeViewModel.kt` — references ghost static API; Plan 2 reimplements.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/MainViewModelFactory.kt` — replaced by `@HiltViewModel`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/OnboardingViewModelFactory.kt` — same.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/SettingsViewModelFactory.kt` — same.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/PasswordViewModel.kt` — its `EncryptedSharedPreferences` flow merges into `AuthManager`; if the screen is needed for first-launch password setup, it gets re-added in Plan 2 with `@HiltViewModel`. For Plan 1, the screen+VM are removed and the `OnboardingScreen` (which already exists) handles password setup. (If `OnboardingScreen` doesn't cover password setup, add a minimal `PasswordViewModel` *constructor-injected* with `AuthManager` — see Task 12.)
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/V3Message.kt` — Plan 2 reimplements as `V5Message` with binary layout.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/TinkMessage.kt` — Plan 2 obsoletes.
- `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/DecryptedMessage.kt` — Plan 2 obsoletes (or restores with new fields).

---

## Pre-flight: read these before starting

- `docs/superpowers/specs/2026-05-21-remediation-design.md`
- `crypto/EncryptionManager.kt` and `crypto/EncryptedScriptLogManager.kt` (current state)
- `BarcodeApplication.kt`, `MainActivity.kt`, `di/DatabaseModule.kt`, `util/AuthManager.kt`
- The full file list under `app/src/main/java/com/hereliesaz/barcodencrypt/`
- `gradle/libs.versions.toml`

---

## Task 1: Add CI workflow that runs build + tests + lint

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflow file**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [master, main]
  push:
    branches: [master, main]

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/master' && github.ref != 'refs/heads/main' }}

      - name: Materialize google-services.json from secret
        env:
          GOOGLE_SERVICES_JSON_B64: ${{ secrets.GOOGLE_SERVICES_JSON_B64 }}
        run: |
          if [ -z "$GOOGLE_SERVICES_JSON_B64" ]; then
            echo "GOOGLE_SERVICES_JSON_B64 secret missing; using template."
            cp app/google-services.template.json app/google-services.json
          else
            echo "$GOOGLE_SERVICES_JSON_B64" | base64 -d > app/google-services.json
          fi

      - name: Assemble debug
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest --stacktrace

      - name: Lint
        run: ./gradlew :app:lintDebug --stacktrace
```

- [ ] **Step 2: Verify workflow syntax locally**

Run:
```bash
test -f .github/workflows/ci.yml && python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"
```
Expected: exits 0, no output.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add build + test + lint workflow on PRs"
```

---

## Task 2: google-services.json local strategy

The repo only commits a template. CI materializes the real file from a base64 secret (see Task 1). Locally, contributors copy the template and either fill it in or accept that Firebase calls will no-op at runtime in debug.

**Files:**
- Create: `app/google-services.template.json` already exists — confirm; if not, create it.
- Modify: `.gitignore` — ensure `app/google-services.json` is ignored.
- Create: `setup_env.sh` is present; verify it bootstraps `google-services.json` from the template.

- [ ] **Step 1: Confirm template exists and ignore real file**

```bash
ls app/google-services.template.json || echo "TEMPLATE MISSING"
grep -q "^app/google-services.json$" .gitignore || echo "GITIGNORE MISSING"
```
Expected: both lines silent. If either fails, fix per next two steps.

- [ ] **Step 2: Add the gitignore entry if absent**

Append to `.gitignore`:
```
# Firebase config (real file is per-developer; template is committed)
app/google-services.json
```

- [ ] **Step 3: Update setup_env.sh to bootstrap the local file**

```bash
#!/usr/bin/env bash
set -euo pipefail
if [ ! -f app/google-services.json ]; then
    cp app/google-services.template.json app/google-services.json
    echo "Bootstrapped app/google-services.json from template."
fi
```

- [ ] **Step 4: Test bootstrap locally**

```bash
rm -f app/google-services.json
bash setup_env.sh
test -f app/google-services.json && echo OK
```
Expected: `OK`.

- [ ] **Step 5: Commit**

```bash
git add .gitignore setup_env.sh
git commit -m "build: gitignore real google-services.json; bootstrap from template"
```

---

## Task 3: Add `@HiltAndroidApp` to BarcodeApplication and remove its private fields

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/BarcodeApplication.kt`

- [ ] **Step 1: Replace BarcodeApplication.kt with the Hilt-enabled version**

```kotlin
package com.hereliesaz.barcodencrypt

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.hereliesaz.barcodencrypt.util.LogConfig
import dagger.hilt.android.HiltAndroidApp
import android.util.Log

@HiltAndroidApp
class BarcodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (LogConfig.APPLICATION_START) Log.d(TAG, "onCreate")

        // One-time Tink registration. Idempotent; safe to call once per process.
        AeadConfig.register()

        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        // Debug token is intentionally only logged in BuildConfig.DEBUG builds.
        if (BuildConfig.DEBUG) {
            appCheck.getAppCheckToken(true).addOnSuccessListener { token ->
                token?.token?.let { Log.d("AppCheckDebug", "App Check Debug Token: $it") }
            }
        }
    }

    companion object {
        private const val TAG = "BarcodeApplication"
    }
}
```

- [ ] **Step 2: Search for remaining references to the removed fields**

Run:
```bash
grep -rn "BarcodeApplication).database\|BarcodeApplication).authManager\|EncryptionManager.init()" app/src/main/java
```
Expected: any hit is a follow-up task (Task 4 fixes call sites). The references will exist in `ComposeViewModel.kt`, `ScannerViewModel.kt`, `ContactDetailViewModel.kt`, `OnboardingViewModel.kt`, `SettingsViewModel.kt`, `CameraViewModel.kt`, `EncryptionOverlayViewModel.kt`, `ContactViewModel.kt`. They are removed in Task 7.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/BarcodeApplication.kt
git commit -m "di: annotate BarcodeApplication with @HiltAndroidApp; drop singleton fields"
```

(Note: the project will not compile until Task 7 lands. That's fine — these tasks are designed to land as a sequence; the green-build acceptance gate is Task 17.)

---

## Task 4: Annotate Activities and Services with `@AndroidEntryPoint`

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/BarcodeAutofillService.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerActivity.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt`

- [ ] **Step 1: Add `@AndroidEntryPoint` annotations**

For each file listed, add `import dagger.hilt.android.AndroidEntryPoint` and `@AndroidEntryPoint` on the class declaration. Example for `MainActivity.kt`:

```kotlin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

- [ ] **Step 2: Change MainActivity's viewmodel binding**

In `MainActivity.kt`, replace:
```kotlin
private val viewModel: MainViewModel by viewModels {
    MainViewModelFactory(application)
}
```
with:
```kotlin
private val viewModel: MainViewModel by viewModels()
```
and remove the unused `MainViewModelFactory` import.

- [ ] **Step 3: Verify all activity/service classes are annotated**

```bash
grep -L "@AndroidEntryPoint" \
    app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt \
    app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt \
    app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt \
    app/src/main/java/com/hereliesaz/barcodencrypt/services/BarcodeAutofillService.kt \
    app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerActivity.kt \
    app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt
```
Expected: no output (all files contain the annotation).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/services/ \
        app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerActivity.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt
git commit -m "di: annotate activities and services with @AndroidEntryPoint"
```

---

## Task 5: Extract `Hashing.sha256` from EncryptionManager

`DecryptionAttemptManager`, `BarcodeRepository`, and `MainViewModel` reach into `EncryptionManager.sha256(...)`. Plan 2 replaces `EncryptionManager` entirely; moving `sha256` out now decouples those callers.

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/util/Hashing.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/util/DecryptionAttemptManager.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/BarcodeRepository.kt`
- Modify: `app/src/test/java/com/hereliesaz/barcodencrypt/DecryptionAttemptManagerTest.kt`

- [ ] **Step 1: Write the failing test first**

Create `app/src/test/java/com/hereliesaz/barcodencrypt/util/HashingTest.kt`:
```kotlin
package com.hereliesaz.barcodencrypt.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HashingTest {
    @Test
    fun `sha256 of empty string matches known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hashing.sha256("")
        )
    }

    @Test
    fun `sha256 of abc matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256("abc")
        )
    }
}
```

- [ ] **Step 2: Run it; expect compile failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*HashingTest*"
```
Expected: compile error "Unresolved reference: Hashing".

- [ ] **Step 3: Create Hashing.kt**

```kotlin
package com.hereliesaz.barcodencrypt.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object Hashing {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: Re-run the test**

```bash
./gradlew :app:testDebugUnitTest --tests "*HashingTest*"
```
Expected: PASS.

- [ ] **Step 5: Replace `EncryptionManager.sha256` with `Hashing.sha256` in DecryptionAttemptManager**

Edit `util/DecryptionAttemptManager.kt:5,36`:
- Remove `import com.hereliesaz.barcodencrypt.crypto.EncryptionManager`.
- Change `return EncryptionManager.sha256(ciphertext)` to `return Hashing.sha256(ciphertext)`.

- [ ] **Step 6: Replace in BarcodeRepository**

Edit `data/BarcodeRepository.kt:4,57,67,90,96`:
- Remove `import com.hereliesaz.barcodencrypt.crypto.EncryptionManager`.
- Change each `EncryptionManager.sha256(x)` to `Hashing.sha256(x)`.
- Add `import com.hereliesaz.barcodencrypt.util.Hashing` at the top.

- [ ] **Step 7: Run existing DecryptionAttemptManagerTest**

```bash
./gradlew :app:testDebugUnitTest --tests "*DecryptionAttemptManagerTest*"
```
Expected: PASS (the file's body should be unchanged — only `EncryptionManager.sha256` calls in production code moved; the test doesn't import EncryptionManager).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/util/Hashing.kt \
        app/src/test/java/com/hereliesaz/barcodencrypt/util/HashingTest.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/util/DecryptionAttemptManager.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/data/BarcodeRepository.kt
git commit -m "refactor: extract Hashing.sha256 from EncryptionManager"
```

---

## Task 6: Add CryptoModule (Hilt) and reconcile EncryptionManager API

The current `EncryptionManager` is a `@Singleton class` with `encrypt(plaintext, barcode, password, ttl, openCount)` / `decrypt(ciphertext, barcode, password)`. Callers use a ghost static API. We resolve this by:
- Keeping `EncryptionManager` as an instance, Hilt-injected.
- Adding clearly-marked "Plan 2 will implement" stubs for the v5 methods that the rewritten call sites will use.
- Removing all references to `encryptV3`, `decryptV3`, `getIkm`, `kdfRk`, `generateV5KeyPair`, etc., from the rest of the codebase by **deleting** the call-site code that uses them (see Tasks 7, 8). Plan 2 reintroduces a clean implementation.

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManager.kt`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/di/CryptoModule.kt`

- [ ] **Step 1: Slim down EncryptionManager.kt to the current real instance API**

Replace the file body with:
```kotlin
package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.gson.Gson
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.OpenedMessageRepository
import com.hereliesaz.barcodencrypt.util.Hashing
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v4 transitional API. Plan 2 will replace this entirely with v5 Double Ratchet.
 * In Plan 1, this class compiles, the build is green, and the call sites that
 * referenced the ghost static API are deleted (see Tasks 7, 8).
 */
data class MessageHeader(
    val appIdentifier: String = "BCE",
    val messageCount: Int,
    val ttl: Long?,
    val openCount: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val messageNumber: Int,
)

@Singleton
class EncryptionManager @Inject constructor(
    private val encryptedScriptLogManager: EncryptedScriptLogManager,
    private val openedMessageRepository: OpenedMessageRepository,
) {

    private fun barcodeIdentifier(barcode: Barcode, password: String?): String {
        val seed = if (password.isNullOrEmpty()) barcode.value else barcode.value + password
        return Hashing.sha256(seed)
    }

    suspend fun encrypt(
        plaintext: String,
        barcode: Barcode,
        password: String? = null,
        ttl: Long? = null,
        openCount: Int? = null,
    ): String? {
        val identifier = barcodeIdentifier(barcode, password)
        val (key, messageNumber) =
            encryptedScriptLogManager.getTemporaryKey(identifier, barcode.value) ?: return null
        val aead: Aead = AesGcmJce(key)
        val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), null)
        val header = MessageHeader(
            messageCount = 1,
            ttl = ttl,
            openCount = openCount,
            messageNumber = messageNumber,
        )
        val headerJson = Gson().toJson(header)
        val headerB64 = Base64.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$headerB64.$ctB64"
    }

    suspend fun decrypt(
        ciphertext: String,
        barcode: Barcode,
        password: String? = null,
    ): String? {
        val parts = ciphertext.split(".")
        if (parts.size != 2) return null
        val header = runCatching {
            val json = String(Base64.decode(parts[0], Base64.NO_WRAP), StandardCharsets.UTF_8)
            Gson().fromJson(json, MessageHeader::class.java)
        }.getOrNull() ?: return null

        if (header.ttl != null && System.currentTimeMillis() - header.timestamp > header.ttl) return null

        val messageId = Hashing.sha256(ciphertext)
        if (header.openCount != null) {
            val opened = openedMessageRepository.getById(messageId)
            if (opened != null && opened.openCount >= header.openCount) return null
        }

        val identifier = barcodeIdentifier(barcode, password)
        val key = encryptedScriptLogManager.getKeyForMessageNumber(identifier, barcode.value, header.messageNumber)
            ?: return null
        val aead: Aead = AesGcmJce(key)
        return runCatching {
            val plain = aead.decrypt(Base64.decode(parts[1], Base64.NO_WRAP), null)
            if (header.openCount != null) openedMessageRepository.incrementOpenCount(messageId)
            String(plain, StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
```

- [ ] **Step 2: Create CryptoModule**

`app/src/main/java/com/hereliesaz/barcodencrypt/di/CryptoModule.kt`:
```kotlin
package com.hereliesaz.barcodencrypt.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for crypto-layer singletons.
 *
 * Currently empty: EncryptionManager and EncryptedScriptLogManager are already
 * @Singleton @Inject constructor and bind automatically. This module exists as
 * an attachment point for Plan 2 (v5 ratchet, Argon2id KDF, key-store wrappers).
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/crypto/EncryptionManager.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/di/CryptoModule.kt
git commit -m "crypto: collapse EncryptionManager to single instance API (Plan 2 replaces)"
```

---

## Task 7: Delete the dead V3/key-exchange code path

Removes the files that reference the ghost static API and unblocks the build.

**Files:**
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/KeyExchangeActivity.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/KeyExchangeViewModel.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/V3Message.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/TinkMessage.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/DecryptedMessage.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/util/MessageParser.kt`

- [ ] **Step 1: Delete the files**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/ui/KeyExchangeActivity.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/KeyExchangeViewModel.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/V3Message.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/TinkMessage.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/crypto/model/DecryptedMessage.kt
```

- [ ] **Step 2: Slim down MessageParser to v4-only**

Replace `util/MessageParser.kt` with:
```kotlin
package com.hereliesaz.barcodencrypt.util

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Finds encrypted-message tokens on screen. Plan 2 replaces this with a v5-only
 * parser that matches `~BCEv5~...`. For Plan 1 we keep the v4 envelope shape
 * (base64(headerJson).base64(ciphertext)) but tolerate either with or without
 * a leading sentinel so the detector finds something while Plan 2 is in flight.
 */
object MessageParser {

    private val V4_REGEX = Regex("[A-Za-z0-9+/=]+\\.[A-Za-z0-9+/=]+")

    fun findAllV4Tokens(root: AccessibilityNodeInfo): List<Pair<String, Rect>> {
        val results = mutableListOf<Pair<String, Rect>>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            node.text?.let { text ->
                V4_REGEX.findAll(text).forEach { match ->
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    results += match.value to rect
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return results
    }
}

typealias Rect = android.graphics.Rect
```

- [ ] **Step 3: Patch MessageDetectionService to use the value-only API**

In `services/MessageDetectionService.kt`, replace `findAndHighlightMessage(rootNode)` body to call `MessageParser.findAllV4Tokens(rootNode)` and pass `(message, rect)` to `startOverlayService` as primitive extras (no `AccessibilityNodeInfo` stashing). Set `AccessibilityNodeHolder.node = null` (Task 8 deletes the holder entirely; for Plan 1 leave the assignment removed so the holder is unused). Plan 3 fixes the lifecycle properly. Diff-style:
```kotlin
private fun findAndHighlightMessage(rootNode: AccessibilityNodeInfo) {
    val hits = MessageParser.findAllV4Tokens(rootNode)
    if (hits.isNotEmpty()) {
        val (message, rect) = hits.first()
        startOverlayService(message = message, bounds = rect)
    } else {
        stopService(Intent(this, OverlayService::class.java))
    }
}
```
Delete `findFocusedEditableNode` and the encryption-button branch of `startOverlayService` for now (Plan 3 reintroduces with proper lifecycle).

- [ ] **Step 4: Verify no remaining references to deleted symbols**

```bash
grep -rn "V3Message\|TinkMessage\|DecryptedMessage\|KeyExchangeActivity\|KeyExchangeViewModel\|encryptV3\|decryptV3\|generateV3KeyPair\|getV3PublicKey\|kdfRk\|getIkm\|OPTION_SINGLE_USE" app/src/main app/src/test
```
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "remove: dead V3/KeyExchange surface; simplify MessageParser to value-only"
```

---

## Task 8: Delete AccessibilityNodeHolder global and the EncryptionOverlay path that used it

Plan 3 reintroduces an encryption overlay with proper node lifecycle. For Plan 1 we delete the unsound global.

**Files:**
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/util/AccessibilityNodeHolder.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/EncryptionOverlay.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/EncryptionOverlayViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt` (remove the encryption branch)

- [ ] **Step 1: Delete files**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/util/AccessibilityNodeHolder.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/EncryptionOverlay.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/EncryptionOverlayViewModel.kt
```

- [ ] **Step 2: Patch OverlayService.showOverlay**

In `services/OverlayService.kt`, drop `EXTRA_SHOW_ENCRYPT_BUTTON` and the `EncryptionOverlay` branch:
```kotlin
private fun showOverlay(message: String, bounds: Rect) {
    overlayView = ComposeView(this).apply {
        setContent {
            BarcodencryptTheme {
                SuggestionOverlay(
                    message = message,
                    onDecryptClick = {
                        val scannerIntent = Intent(context, ScannerActivity::class.java).apply {
                            action = Constants.ACTION_DECRYPT_MESSAGE
                            putExtra(EXTRA_MESSAGE, message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(scannerIntent)
                        removeOverlay()
                    }
                )
            }
        }
    }
    // ... rest unchanged, but drop `if (showEncryptButton)` width override
}
```
And update `onStartCommand` to drop the `showEncryptButton` extra read.

- [ ] **Step 3: Search for leftover references**

```bash
grep -rn "AccessibilityNodeHolder\|EncryptionOverlay\|EncryptionOverlayViewModel\|EXTRA_SHOW_ENCRYPT_BUTTON" app/src
```
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "remove: AccessibilityNodeHolder global and EncryptionOverlay (Plan 3 reintroduces)"
```

---

## Task 9: Delete the Material3 NavigationRail shell

**Files:**
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/Navigation.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/MainScreen.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppScaffold.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordScannerTrampolineActivity.kt`

- [ ] **Step 1: Delete**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/ui/Navigation.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/ui/MainScreen.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppScaffold.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordScannerTrampolineActivity.kt
```

- [ ] **Step 2: Check for orphan imports**

```bash
grep -rn "ui.Navigation\|ui.MainScreen\|AppScaffold\|PasswordScannerTrampolineActivity\|TryItCard\|TryItState\|TryItAction" app/src/main
```
Expected: hits in `MainViewModel.kt` (`TryItState`/`TryItAction` references) and possibly `ui/App.kt`. Task 12 fixes these.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "remove: Material3 NavigationRail shell (AzNavRail shell wins, Plan 4 wires it)"
```

---

## Task 10: Annotate repositories with `@Singleton @Inject constructor`

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/BarcodeRepository.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/ContactRepository.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/data/OpenedMessageRepository.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/di/DatabaseModule.kt`

- [ ] **Step 1: BarcodeRepository — change signature**

Replace the class declaration:
```kotlin
class BarcodeRepository(private val barcodeDao: BarcodeDao) {
```
with:
```kotlin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarcodeRepository @Inject constructor(
    private val barcodeDao: BarcodeDao,
) {
```

- [ ] **Step 2: Same for ContactRepository**

```kotlin
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
) { ... }
```

- [ ] **Step 3: Same for OpenedMessageRepository**

```kotlin
@Singleton
class OpenedMessageRepository @Inject constructor(
    private val openedMessageDao: OpenedMessageDao,
) { ... }
```

- [ ] **Step 4: Verify EncryptedScriptLogRepository already correct**

```bash
grep -n "@Inject" app/src/main/java/com/hereliesaz/barcodencrypt/data/EncryptedScriptLogRepository.kt
```
Expected: `@Inject` already present.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/data/
git commit -m "di: annotate repositories with @Singleton @Inject constructor"
```

---

## Task 11: Convert all ViewModels to `@HiltViewModel`, delete factories

For each ViewModel, replace its `class Foo(application: Application) : AndroidViewModel(application)` (or similar) with `@HiltViewModel class Foo @Inject constructor(...)`, inject the dependencies it actually needs, and delete the corresponding `*Factory.kt`.

**Files (one task per ViewModel — group commits at the end):**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/MainViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/ComposeViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/ContactDetailViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/ScannerViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/ContactViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/CameraViewModel.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/MainViewModelFactory.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/OnboardingViewModelFactory.kt`
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/SettingsViewModelFactory.kt`
- Delete (if exist): `viewmodel/ContactDetailViewModelFactory.kt`, `viewmodel/ScannerViewModelFactory.kt`, `viewmodel/CameraViewModelFactory.kt`

- [ ] **Step 1: MainViewModel**

Rewrite as:
```kotlin
package com.hereliesaz.barcodencrypt.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    private val tag = "MainViewModel"

    private val _serviceStatus = MutableStateFlow(false)
    val serviceStatus = _serviceStatus.asStateFlow()
    private val _notificationPermissionStatus = MutableStateFlow(false)
    val notificationPermissionStatus = _notificationPermissionStatus.asStateFlow()
    private val _contactsPermissionStatus = MutableStateFlow(false)
    val contactsPermissionStatus = _contactsPermissionStatus.asStateFlow()
    private val _overlayPermissionStatus = MutableStateFlow(false)
    val overlayPermissionStatus = _overlayPermissionStatus.asStateFlow()
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.user.collect { firebaseUser: FirebaseUser? ->
                val loggedIn = firebaseUser != null || authManager.hasLocalPassword()
                if (_isLoggedIn.value != loggedIn) _isLoggedIn.value = loggedIn
            }
        }
    }

    fun setServiceStatus(enabled: Boolean) { _serviceStatus.value = enabled }
    fun setNotificationPermissionStatus(granted: Boolean) { _notificationPermissionStatus.value = granted }
    fun setContactsPermissionStatus(granted: Boolean) { _contactsPermissionStatus.value = granted }
    fun setOverlayPermissionStatus(granted: Boolean) { _overlayPermissionStatus.value = granted }
}
```

Note the `TryItState` / `generateTryItMessage` / `decryptTryItMessage` flow is **deleted** here. It was broken (ghost API). Plan 2 reintroduces a working Try-It mode against the v5 API.

- [ ] **Step 2: ComposeViewModel**

Rewrite to use injected repositories and the instance `EncryptionManager`:
```kotlin
@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val contactRepository: ContactRepository,
    private val encryptionManager: EncryptionManager,
) : ViewModel() {

    private var contactBarcodeJob: Job? = null
    private val _barcodesForSelectedContact = MutableStateFlow<List<Barcode>>(emptyList())
    val barcodesForSelectedContact = _barcodesForSelectedContact.asStateFlow()
    private var selectedContact: Contact? = null

    fun selectContact(contactLookupKey: String) {
        contactBarcodeJob?.cancel()
        contactBarcodeJob = viewModelScope.launch {
            selectedContact = contactRepository.getContactByLookupKey(contactLookupKey).firstOrNull()
            barcodeRepository.getBarcodesForContactFlow(contactLookupKey).collect {
                _barcodesForSelectedContact.value = it
            }
        }
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode,
        password: String? = null,
        ttl: Long? = null,
        openCount: Int? = null,
    ): String? {
        val fresh = barcodeRepository.getBarcode(barcode.id) ?: return null
        return encryptionManager.encrypt(plaintext, fresh, password, ttl, openCount)
    }

    override fun onCleared() { super.onCleared(); contactBarcodeJob?.cancel() }
}
```

- [ ] **Step 3: ContactDetailViewModel — same pattern**

Replace `(application as BarcodeApplication).database…` with `@Inject barcodeRepository: BarcodeRepository` / `contactRepository: ContactRepository`. Convert any `LiveData<List<Barcode>>` exposure to `StateFlow<List<Barcode>>` using `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())`.

- [ ] **Step 4: ScannerViewModel**

Rewrite to:
```kotlin
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val encryptionManager: EncryptionManager,
) : ViewModel() {

    private val _decryptionResult = MutableStateFlow<DecryptionResult?>(null)
    val decryptionResult = _decryptionResult.asStateFlow()

    fun decryptMessage(scannedValue: String, encryptedMessage: String) {
        viewModelScope.launch {
            // Plan 2 will wire a v5-aware lookup; for Plan 1 we just decrypt with the
            // first matching barcode owned by this user (acceptable because the build
            // is the goal, not feature completeness).
            val barcode = barcodeRepository.findFirstWithValue(scannedValue) ?: run {
                _decryptionResult.value = DecryptionResult.Error("No matching key.")
                return@launch
            }
            val plaintext = encryptionManager.decrypt(encryptedMessage, barcode)
            _decryptionResult.value =
                if (plaintext != null) DecryptionResult.Success(plaintext)
                else DecryptionResult.Error("Decryption failed.")
        }
    }

    fun reset() { _decryptionResult.value = null }
}

sealed class DecryptionResult {
    data class Success(val plaintext: String) : DecryptionResult()
    data class Error(val message: String) : DecryptionResult()
}
```

Add `suspend fun findFirstWithValue(value: String): Barcode?` to `BarcodeRepository` that iterates the DAO results and returns the first one whose decrypted value matches. (Acceptable O(n) given small N; Plan 2 will index by `Hashing.sha256(value)`.)

- [ ] **Step 5: SettingsViewModel, OnboardingViewModel, ContactViewModel, CameraViewModel**

Same mechanical pattern: `@HiltViewModel`, `@Inject`, replace `BarcodeApplication`-fetched singletons.

- [ ] **Step 6: Delete all `*ViewModelFactory.kt`**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/MainViewModelFactory.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/OnboardingViewModelFactory.kt \
       app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/SettingsViewModelFactory.kt
# delete any other *Factory.kt that exists in viewmodel/
for f in app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/*Factory.kt; do
    [ -e "$f" ] && git rm "$f"
done
```

- [ ] **Step 7: Verify**

```bash
grep -rn "ViewModelProvider.Factory\|BarcodeApplication).database\|BarcodeApplication).authManager" app/src/main
```
Expected: no output.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "di: convert all ViewModels to @HiltViewModel; delete hand-rolled factories"
```

---

## Task 12: Replace `PasswordViewModel` and unify the password path through `AuthManager`

The current `PasswordViewModel` uses its own `EncryptedSharedPreferences("password_prefs")` and stores `db_password_hash` as raw SHA-256. Plan 2 will replace SHA-256 with Argon2id. For Plan 1, we only need to:
1. Stop using `password_prefs`.
2. Fold the "set / check / has" password operations into `AuthManager`, persisted in the single `barcodencrypt_prefs` file.
3. Provide a Hilt-injected `PasswordViewModel` if the Onboarding flow needs one.

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/util/AuthManager.kt`
- Replace: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/PasswordViewModel.kt`
- Modify call sites: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt` (or wherever PasswordViewModel is consumed)

- [ ] **Step 1: Add unified password methods to AuthManager**

Add inside `AuthManager` (alongside existing `setPassword`/`checkPassword`):
```kotlin
fun isPasswordSet(): Boolean = sharedPreferences.contains(ENCRYPTED_PASSWORD_KEY)
```
And **remove** `getPassword(): CharSequence`. Replace it with:
```kotlin
/**
 * Returns the SQLCipher passphrase bytes for an unlocked session, or throws.
 *
 * Plan 1: returns the raw user password bytes (legacy behavior, fixed). Plan 2
 * replaces this with Argon2id(userPassword, perInstallSalt).
 */
fun requirePassphrase(): ByteArray {
    val encrypted = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        ?: error("Password not set")
    val ivStr = sharedPreferences.getString(IV_KEY, null) ?: error("IV missing")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, Base64.decode(ivStr, Base64.DEFAULT)))
    return cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))
}
```

- [ ] **Step 2: Replace PasswordViewModel**

```kotlin
package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    sealed class State {
        object Loading : State()
        object Unset : State()
        object Set : State()
        object Invalid : State()
        object Success : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    init {
        _state.value = if (authManager.isPasswordSet()) State.Set else State.Unset
    }

    fun submit(password: String) {
        viewModelScope.launch {
            _state.value = if (authManager.checkPassword(password)) State.Success else State.Invalid
        }
    }

    fun create(password: String) {
        viewModelScope.launch {
            authManager.setPassword(password)
            _state.value = State.Success
        }
    }
}
```

- [ ] **Step 3: Patch DatabaseModule.provideAppDatabase**

```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context, authManager: AuthManager): AppDatabase {
    val passphrase = authManager.requirePassphrase()
    try {
        return AppDatabase.getDatabase(context, passphrase)
    } finally {
        java.util.Arrays.fill(passphrase, 0.toByte())
    }
}
```

- [ ] **Step 4: Patch AppDatabase.getDatabase signature**

In `data/AppDatabase.kt`, change:
```kotlin
fun getDatabase(context: Context, passphrase: CharSequence): AppDatabase { ... }
```
to:
```kotlin
fun getDatabase(context: Context, passphrase: ByteArray): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        val factory = net.sqlcipher.database.SupportFactory(passphrase.copyOf())
        Room.databaseBuilder(...).openHelperFactory(factory).addMigrations(...).build().also { INSTANCE = it }
    }
}
```

- [ ] **Step 5: Update PasswordActivity / OnboardingScreen consumers**

Wherever the old `PasswordViewModel` is consumed via `viewModel()`, switch to `hiltViewModel()` and use the new state enum.

- [ ] **Step 6: Verify**

```bash
grep -rn "password_prefs\|EncryptedSharedPreferences\|MasterKeys" app/src/main
```
Expected: no hits (we eliminated the dependency on the Jetpack Security library for `password_prefs`).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "auth: unify password path through AuthManager; delete password_prefs / EncryptedSharedPreferences"
```

---

## Task 13: Rewrite `ui/App.kt` so the AzNavRail shell only references real symbols

Plan 4 will fully wire the rail. For Plan 1 we keep the AzNavRail composable but route every item to a screen that exists. Delete the in-file `ContactsScreen` / `TutorialScreen` / `ContactItem` / `BarcodeItem` / inlined `ContactDetailScreen` and the broken `ContactsViewModel`/`TutorialViewModel` imports.

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/App.kt`

- [ ] **Step 1: Rewrite App.kt to a minimal but compiling shell**

```kotlin
package com.hereliesaz.barcodencrypt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Compose : Screen("compose")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
}

@Composable
fun App(viewModel: MainViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        AzNavRail {
            azRailItem(id = "home", text = "Home", onClick = { navController.navigate(Screen.Home.route) })
            azRailItem(id = "compose", text = "Compose", onClick = { navController.navigate(Screen.Compose.route) })
            azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate(Screen.Settings.route) })
        }
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn == true) Screen.Home.route else Screen.Onboarding.route,
        ) {
            composable(Screen.Home.route) { Text("Home (Plan 4 wires this).") }
            composable(Screen.Compose.route) { ComposeScreen(navController = navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
            composable(Screen.Onboarding.route) { OnboardingScreen(navController = navController) }
        }
    }
}
```

- [ ] **Step 2: Patch ComposeScreen / SettingsScreen / OnboardingScreen signatures**

Each of `ui/ComposeScreen.kt`, `ui/SettingsScreen.kt`, `ui/OnboardingScreen.kt` may previously have taken explicit `(navController, viewModel = viewModel())` parameters with manual factories. Switch the parameter default to `hiltViewModel()`:
```kotlin
import androidx.hilt.navigation.compose.hiltViewModel
…
@Composable
fun ComposeScreen(
    navController: NavController,
    viewModel: ComposeViewModel = hiltViewModel(),
) { ... }
```

- [ ] **Step 3: Verify**

```bash
grep -rn "ContactsViewModel\|TutorialViewModel\|TryItState\|TryItAction\|TryItCard" app/src/main
```
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ui: shrink ui/App.kt to a compiling minimal shell (Plan 4 wires real graph)"
```

---

## Task 14: Disable backup pending Plan 3

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Flip allowBackup off**

In `AndroidManifest.xml:22`, change `android:allowBackup="true"` to `android:allowBackup="false"`. Plan 3 will set proper rules and may flip it back on.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "security: allowBackup=false until Plan 3 sets exclusion rules"
```

---

## Task 15: Run `./gradlew assembleDebug` and fix any remaining call-site breakage

This is the truth-check. Expect a handful of imports to be wrong, parameter names to have drifted, or Composable defaults to need `hiltViewModel()`.

- [ ] **Step 1: Bootstrap google-services**

```bash
bash setup_env.sh
```

- [ ] **Step 2: Run assemble**

```bash
./gradlew :app:assembleDebug --stacktrace 2>&1 | tee /tmp/assemble.log
```

- [ ] **Step 3: For each compile error in `/tmp/assemble.log`, write a single-line fix and re-run**

Common shapes:
- `Unresolved reference: viewModel()` → switch to `hiltViewModel()`.
- `Unresolved reference: BarcodeApplication.database` → already replaced; missing import means an old file slipped through; fix.
- `Cannot find a constructor matching` on a deleted Factory → switch call site to `by viewModels()` (Activity) or `hiltViewModel()` (Composable).
- Room compile error in a DAO that exposed `LiveData` → leave existing LiveData DAO methods (Compose can `observeAsState()`); the repository wrapper is the only piece we deflowed.

- [ ] **Step 4: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```
Expected: PASS (DecryptionAttemptManagerTest, HashingTest).

- [ ] **Step 5: Run lint**

```bash
./gradlew :app:lintDebug --stacktrace
```
Expected: completes (warnings are OK; errors are not).

- [ ] **Step 6: Commit any compile-fix patches as one commit**

```bash
git add -A
git commit -m "fix: post-Hilt compile errors uncovered by ./gradlew assembleDebug"
```

---

## Task 16: Smoke-test on emulator

- [ ] **Step 1: Install on emulator**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Launch and walk through:**

1. App opens to Onboarding (when no password is set).
2. Setting a password lands you on the Home screen (placeholder text).
3. Compose / Settings routes navigate.
4. No crash on screen rotation or backgrounding.

If any of these fail, file the issue against Plan 4 (UI wiring) rather than Plan 1 — but a crash on launch must be fixed here.

- [ ] **Step 3: (No commit; documentation step.)**

---

## Task 17: Run the CI workflow

- [ ] **Step 1: Push the branch**

```bash
git push -u origin <branch>
```

- [ ] **Step 2: Watch the workflow**

```bash
gh run watch
```
Expected: `success`. If the `Materialize google-services.json from secret` step warns and uses the template, the build can still complete because Firebase init at runtime is robust to a placeholder file (Firebase calls just no-op).

- [ ] **Step 3: Open PR**

```bash
gh pr create --title "Plan 1: Foundation — Hilt cutover, shell consolidation, green build" \
             --body "$(cat docs/superpowers/plans/2026-05-21-plan-1-foundation.md | head -40)"
```

---

## Acceptance criteria

- ✅ `./gradlew :app:assembleDebug` exits 0 on a fresh clone after `setup_env.sh`.
- ✅ `./gradlew :app:testDebugUnitTest` exits 0; `HashingTest` and `DecryptionAttemptManagerTest` pass.
- ✅ `./gradlew :app:lintDebug` exits 0 (warnings OK).
- ✅ The CI workflow runs on every PR and passes on this branch.
- ✅ `grep -rn "BarcodeApplication).database\|ViewModelProvider.Factory\|EncryptionManager.init()\|password_prefs\|ContactsViewModel\|TutorialViewModel\|encryptV3\|decryptV3" app/src/main app/src/test` returns no matches.
- ✅ Only one app shell exists (`ui/App.kt`).
- ✅ Only one SharedPreferences file is used (`barcodencrypt_prefs`).
- ✅ All ViewModels are `@HiltViewModel`. Zero hand-rolled `*ViewModelFactory.kt` files remain.
- ✅ `MainActivity`, all three Services, and any other Activity are `@AndroidEntryPoint`.
- ✅ Emulator smoke test: app launches, onboarding sets a password, home screen appears, no crashes from rotation.

## Risk register

- **Tink `AeadConfig.register()` failure on Android 7.** Documented to be safe on API 26+ (our `minSdk`).
- **SQLCipher passphrase as raw user-password bytes.** This is transitional. Plan 2 replaces with Argon2id-derived bytes. The current path is still strictly better than the empty-string-on-failure status quo.
- **`AppDatabase.getDatabase(passphrase: ByteArray)` migration.** Old call sites that passed `CharSequence` will fail to compile, surfacing all consumers — that's the goal.
- **Backup is now off.** Existing users (if any) lose backed-up app data. Acceptable per D3.
- **`PasswordScannerTrampolineActivity` deletion may break a flow.** It was never registered in the manifest, so the deletion is provably safe. Plan 3 reintroduces a registered trampoline.

## What's deliberately NOT in this plan

- Implementing the v5 Double Ratchet (Plan 2).
- Foreground notifications for services (Plan 3).
- `FLAG_SECURE` on plaintext-bearing windows (Plan 3).
- Registering `BarcodeAutofillService` (Plan 3).
- Rewriting `AppNavigation` with typed args (Plan 4).
- Toolchain pin-down or doc rewrites (Plan 5).
