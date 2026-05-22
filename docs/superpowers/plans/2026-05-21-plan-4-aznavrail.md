# Plan 4 — AzNavRail Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the app's chrome around `AzHostActivityLayout` (the v9 recommended entry point) with a single, typed-argument `AppNavigation` graph. Every nav destination is reachable from the rail and the rail observes the current route for selection state. Delete the duplicate `ui/composable/AppNavRail.kt`. Pin AzNavRail to a specific commit SHA so JitPack drift can't break the build.

**Architecture:**
- The top-level Composable is `App()` in `ui/App.kt`. It calls `AzHostActivityLayout(navController, currentDestination, …) { … }`.
- Inside the DSL block: `azConfig` (LEFT docking, packed), `azTheme` (defaults from `BarcodencryptTheme`), then `azRailItem`s with **`route` arguments** so AzNavRail handles navigation and selection state automatically. Items with side effects (logout, "Enable accessibility service" link) use `onClick`.
- `onscreen { AppNavHost(...) }` hosts the typed-argument `NavHost`.
- Screens are `OnboardingScreen`, `HomeScreen` (new — was the broken `MainScreen` text), `ComposeScreen`, `ContactDetailScreen`, `ScannerScreen`, `SettingsScreen`. `KeyExchangeScreen` is reintroduced (Plan 2 created the crypto for it, Plan 4 wires the UI).
- `Screen` is a sealed class with route patterns and a `createRoute(...)` factory for arguments.
- `ContactDetail` takes typed args `contactLookupKey: String` and `contactName: String`.
- Login gate: if `MainViewModel.isLoggedIn == false`, start destination is `Onboarding`; otherwise `Home`.
- `AppNavigation` watches the back stack via `navController.currentBackStackEntryAsState()` and feeds the route to `AzHostActivityLayout(currentDestination = …)` for highlight.

**Tech Stack additions:** none (AzNavRail and navigation-compose already pinned by Plan 1).

**Reading dependencies:**
- Plan 1 must be complete (`ui/App.kt` is a minimal shell; the Material3 NavigationRail shell is deleted).
- Plan 3 must be complete (`SettingsScreen` includes the accessibility-allowlist editor; `KeyExchangeScreen` requires the v5 crypto).
- The AzNavRail Complete Guide at `https://github.com/HereLiesAz/AzNavRail/blob/master/docs/AZNAVRAIL_COMPLETE_GUIDE.md` is the authoritative API reference for this plan.

---

## File structure

### Created
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/Screen.kt` — sealed nav-graph definition.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/AppNavHost.kt` — the `NavHost` factored out of `ui/App.kt`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/HomeScreen.kt` — landing screen post-login; shows service-status, permission cards, "Manage Contacts" entry point.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/KeyExchangeScreen.kt` — UI for the Plan 2 X25519 QR exchange.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/KeyExchangeViewModel.kt` — re-created (deleted in Plan 1); now uses Plan 2's `RatchetEngine.startSession`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/HomeViewModel.kt` — collects permission flags and service status.

### Modified
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/App.kt` — rewritten to use `AzHostActivityLayout`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ComposeScreen.kt` — accepts `navController: NavController`; uses `hiltViewModel()`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ContactDetailScreen.kt` — accepts typed args from `Screen.ContactDetail`; uses `hiltViewModel()`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerScreen.kt` — accepts `navController`; uses `hiltViewModel()`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/SettingsScreen.kt` — accepts `navController`; adds an editor for the `accessibility_service_config.xml` `packageNames` allowlist (writes to `serviceInfo.packageNames` at runtime).
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/OnboardingScreen.kt` — `hiltViewModel()`; on completion, `navController.navigate(Screen.Home.route) { popUpTo(0) }`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/MainViewModel.kt` — adds `setNavReady()` if not already; keeps `isLoggedIn`.
- `gradle/libs.versions.toml` — pin AzNavRail to a commit SHA (see Task 1).

### Deleted
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppNavRail.kt` — duplicate of what's now in `ui/App.kt`.

---

## Task 1: Pin AzNavRail to a SHA

JitPack accepts `commitSha-SNAPSHOT` strings. This makes the build reproducible against an exact version.

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Find a recent stable commit**

```bash
gh api repos/HereLiesAz/AzNavRail/commits?per_page=1 | jq -r '.[0].sha'
```
Take the first 10 characters of the SHA. Call this `AZ_SHA`.

- [ ] **Step 2: Update version**

In `libs.versions.toml`, replace:
```toml
aznavrail = "9.0"
```
with:
```toml
# pinned: HereLiesAz/AzNavRail@<full SHA URL>
aznavrail = "<AZ_SHA>"
```

- [ ] **Step 3: Verify resolution**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i AzNavRail
```
Expected: one line containing the SHA.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "deps: pin AzNavRail to commit SHA to prevent JitPack drift"
```

---

## Task 2: Sealed Screen graph

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/Screen.kt`

- [ ] **Step 1: Define**

```kotlin
package com.hereliesaz.barcodencrypt.ui

import android.net.Uri

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Compose : Screen("compose")
    data object Settings : Screen("settings")
    data object Scanner : Screen("scanner")

    data object ContactDetail : Screen("contact/{contactLookupKey}/{contactName}") {
        const val ARG_KEY = "contactLookupKey"
        const val ARG_NAME = "contactName"
        fun createRoute(contactLookupKey: String, contactName: String): String =
            "contact/$contactLookupKey/${Uri.encode(contactName)}"
    }

    data object KeyExchange : Screen("keyexchange/{contactLookupKey}") {
        const val ARG_KEY = "contactLookupKey"
        fun createRoute(contactLookupKey: String): String = "keyexchange/$contactLookupKey"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/ui/Screen.kt
git commit -m "ui: sealed Screen graph with typed-arg routes"
```

---

## Task 3: AppNavHost

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/AppNavHost.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hereliesaz.barcodencrypt.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) { OnboardingScreen(navController) }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Compose.route) { ComposeScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.Scanner.route) { ScannerScreen(navController) }
        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(
                navArgument(Screen.ContactDetail.ARG_KEY) { type = NavType.StringType },
                navArgument(Screen.ContactDetail.ARG_NAME) { type = NavType.StringType },
            ),
        ) { entry ->
            val key = entry.arguments?.getString(Screen.ContactDetail.ARG_KEY).orEmpty()
            val name = entry.arguments?.getString(Screen.ContactDetail.ARG_NAME).orEmpty()
            ContactDetailScreen(
                navController = navController,
                contactLookupKey = key,
                contactName = Uri.decode(name),
            )
        }
        composable(
            route = Screen.KeyExchange.route,
            arguments = listOf(navArgument(Screen.KeyExchange.ARG_KEY) { type = NavType.StringType }),
        ) { entry ->
            val key = entry.arguments?.getString(Screen.KeyExchange.ARG_KEY).orEmpty()
            KeyExchangeScreen(navController = navController, contactLookupKey = key)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/ui/AppNavHost.kt
git commit -m "ui: AppNavHost with typed args for ContactDetail and KeyExchange"
```

---

## Task 4: Rewrite `ui/App.kt` to use AzHostActivityLayout

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/App.kt`

- [ ] **Step 1: Verify `AzHostActivityLayout` exists in the pinned AzNavRail version**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath > /tmp/deps.txt
unzip -l "$(./gradlew :app:dependencies --configuration debugRuntimeClasspath -q | grep AzNavRail | head -1 | awk '{print $1}')" 2>/dev/null | grep AzHostActivityLayout || echo "ABSENT"
```
If `ABSENT`, fall back to `AzNavRail { ... }` (the v9 lower-level API) for this task. The same `azConfig`/`azRailItem` DSL works inside it; only the outer wrapper differs. Document the fallback in the commit message.

- [ ] **Step 2: Implement the recommended path**

```kotlin
package com.hereliesaz.barcodencrypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.AzDockingSide
import com.hereliesaz.aznavrail.AzHostActivityLayout
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel

@Composable
fun App(viewModel: MainViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState(initial = null)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Until login state resolves we render nothing (the splash theme covers the gap).
    val ready = isLoggedIn ?: return
    val startDestination = if (ready) Screen.Home.route else Screen.Onboarding.route

    AzHostActivityLayout(
        navController = navController,
        modifier = Modifier,
        currentDestination = currentRoute,
        isLandscape = landscape,
        initiallyExpanded = false,
    ) {
        azConfig(dockingSide = AzDockingSide.LEFT, packButtons = true)

        // Top-level destinations
        azRailItem(id = "home", text = "Home", route = Screen.Home.route)
        azRailItem(id = "compose", text = "Compose", route = Screen.Compose.route)
        azRailItem(id = "settings", text = "Settings", route = Screen.Settings.route)
        azDivider()
        // Onboarding is reachable as a destination but doesn't show in the rail
        // (when logged out the rail collapses; when logged in we don't want it).

        onscreen {
            AppNavHost(navController = navController, startDestination = startDestination)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/ui/App.kt
git commit -m "ui: App() uses AzHostActivityLayout with route-based rail items"
```

---

## Task 5: Delete the duplicate AzNavRail wrapper

**Files:**
- Delete: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppNavRail.kt`

- [ ] **Step 1: Verify no callers**

```bash
grep -rn "AppNavRail\b" app/src/main
```
Expected: no output (Plan 1 already removed the other shell).

- [ ] **Step 2: Delete**

```bash
git rm app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/AppNavRail.kt
git commit -m "ui: remove duplicate AppNavRail wrapper"
```

---

## Task 6: HomeScreen

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/HomeScreen.kt`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: HomeViewModel**

```kotlin
package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @Suppress("unused") private val authManager: AuthManager,
) : ViewModel() {
    // Pass-through reads come from MainViewModel; HomeViewModel exists so HomeScreen
    // can have its own UI state in the future without coupling to MainViewModel.
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
}
```

- [ ] **Step 2: HomeScreen**

Pulls the meaningful chunks of the deleted `MainScreen.kt` (service status card, permission cards, "Manage Contacts" entry) without the Try-It card or the dialog soup.

```kotlin
package com.hereliesaz.barcodencrypt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val serviceEnabled by mainViewModel.serviceStatus.collectAsState()
    val notificationGranted by mainViewModel.notificationPermissionStatus.collectAsState()
    val overlayGranted by mainViewModel.overlayPermissionStatus.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BarcodeNcrypt", style = MaterialTheme.typography.headlineSmall)
        StatusRow("Watcher service", serviceEnabled)
        StatusRow("Notification permission", notificationGranted)
        StatusRow("Overlay permission", overlayGranted)
        Button(onClick = { navController.navigate(Screen.Compose.route) }) { Text("Compose message") }
        OutlinedButton(onClick = { navController.navigate(Screen.Settings.route) }) { Text("Settings") }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Text("$label: ${if (ok) "✓" else "✗"}")
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/ui/HomeScreen.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/HomeViewModel.kt
git commit -m "ui: HomeScreen + HomeViewModel landing destination"
```

---

## Task 7: KeyExchangeScreen + KeyExchangeViewModel (uses Plan 2 crypto)

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/KeyExchangeScreen.kt`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/KeyExchangeViewModel.kt`

- [ ] **Step 1: KeyExchangeViewModel**

```kotlin
package com.hereliesaz.barcodencrypt.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.crypto.tink.subtle.X25519
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hereliesaz.barcodencrypt.crypto.RatchetEngine
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import com.hereliesaz.barcodencrypt.ui.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyExchangeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val barcodeRepository: BarcodeRepository,
    private val ratchetStateRepository: RatchetStateRepository,
) : ViewModel() {

    private val contactLookupKey: String =
        requireNotNull(savedStateHandle[Screen.KeyExchange.ARG_KEY])

    private val _qr = MutableStateFlow<Bitmap?>(null)
    val qr = _qr.asStateFlow()

    init {
        viewModelScope.launch {
            // For first-time exchange, bootstrap if we don't have a session yet:
            // the user must already have scanned a barcode for this contact.
            val barcodes = barcodeRepository.getBarcodesForContactFlow(contactLookupKey).first()
            val barcode = barcodes.firstOrNull() ?: return@launch
            barcode.decryptValue()
            ensureSession(barcode)
        }
    }

    fun consumeRemotePublicKey(remotePublicKeyBytes: ByteArray) {
        viewModelScope.launch {
            val (salt, state) = ratchetStateRepository.load(contactLookupKey) ?: return@launch
            val advanced = RatchetEngine.startSession(state, remotePublicKeyBytes)
            ratchetStateRepository.save(contactLookupKey, salt, advanced)
            advanced.dhSelfPub?.let { _qr.value = qrOf(it) }
        }
    }

    private suspend fun ensureSession(barcode: Barcode) {
        val existing = ratchetStateRepository.load(contactLookupKey)
        val state = existing?.second ?: run {
            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val boot = RatchetEngine.bootstrap(
                barcode = barcode.value.toByteArray(),
                password = null,
                contactSalt = salt,
            )
            // Generate identity keypair and persist
            val priv = X25519.generatePrivateKey()
            val pub = X25519.publicFromPrivate(priv)
            val withKeys = boot.copy(dhSelfPriv = priv, dhSelfPub = pub)
            ratchetStateRepository.save(contactLookupKey, salt, withKeys)
            withKeys
        }
        state.dhSelfPub?.let { _qr.value = qrOf(it) }
    }

    private fun qrOf(publicKey: ByteArray): Bitmap {
        val writer = QRCodeWriter()
        val payload = "bce5://k/${android.util.Base64.encodeToString(publicKey, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)}"
        val matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) {
            bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        return bmp
    }
}
```

- [ ] **Step 2: KeyExchangeScreen**

```kotlin
package com.hereliesaz.barcodencrypt.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.viewmodel.KeyExchangeViewModel

@Composable
fun KeyExchangeScreen(
    navController: NavController,
    contactLookupKey: String,
    viewModel: KeyExchangeViewModel = hiltViewModel(),
) {
    val qr by viewModel.qr.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Show this QR to your contact; scan their QR to complete the exchange.")
        qr?.let { Image(it.asImageBitmap(), contentDescription = "Your public key") }
        Button(onClick = { navController.navigate(Screen.Scanner.route) }) { Text("Scan their QR") }
    }
}
```

(`Scanner.route` must accept the convention `bce5://k/<base64>` and route the bytes to `KeyExchangeViewModel.consumeRemotePublicKey`. The `ScannerActivity` already handles raw bytes; an `EXTRA_KEY_EXCHANGE_FOR` mode dispatches results back to the previous back-stack entry via `savedStateHandle`. Wire that in Step 3.)

- [ ] **Step 3: Wire Scanner result to KeyExchange**

In `ScannerScreen` / `ScannerActivity`, when the parent back-stack entry's route is `Screen.KeyExchange.route`, parse `bce5://k/<base64>`, decode, and call:
```kotlin
navController.previousBackStackEntry?.savedStateHandle?.set("remote_pubkey", decoded)
navController.popBackStack()
```
In `KeyExchangeScreen`, observe `navController.currentBackStackEntry?.savedStateHandle?.getLiveData<ByteArray>("remote_pubkey")` and forward to `viewModel.consumeRemotePublicKey(...)`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ui: KeyExchangeScreen + KeyExchangeViewModel over v5 RatchetEngine"
```

---

## Task 8: ContactDetailScreen — add "Exchange keys" button

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ContactDetailScreen.kt`

- [ ] **Step 1: Add navigation action**

In the screen body, add:
```kotlin
OutlinedButton(onClick = {
    navController.navigate(Screen.KeyExchange.createRoute(contactLookupKey))
}) {
    Text("Exchange keys with this contact")
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/ui/ContactDetailScreen.kt
git commit -m "ui: ContactDetail surfaces Key Exchange entry point"
```

---

## Task 9: SettingsScreen — accessibility allowlist editor

Plan 3 ships a default allowlist baked into `accessibility_service_config.xml`. This task lets the user broaden it at runtime via `AccessibilityServiceInfo.setServiceInfo(...)`.

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt`

- [ ] **Step 1: Persist user override**

Add to `SettingsManager.kt` (util/) — getters/setters for a `Set<String>` of allowed packages, stored in `barcodencrypt_prefs`.

- [ ] **Step 2: Apply override on service connect**

In `MessageDetectionService.onServiceConnected`, after `super.onServiceConnected()` and `startForeground`:
```kotlin
val override = SettingsManager(applicationContext).accessibilityAllowlist().toTypedArray()
if (override.isNotEmpty()) {
    val info = serviceInfo
    info.packageNames = override
    serviceInfo = info
}
```

- [ ] **Step 3: UI**

In `SettingsScreen`, add a `LazyColumn` of currently-allowed packages with an "Add package" button (text field). On change, write to `SettingsManager`. Show a snackbar that says "Restart the accessibility service for changes to apply" — or better, call `disableSelf()` then prompt the user to re-enable.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ui: Settings exposes accessibility allowlist editor"
```

---

## Task 10: Re-enable typed-arg navigation across the app

- [ ] **Step 1: Sweep for `viewModel()` and old-style nav strings**

```bash
grep -rn "androidx.lifecycle.viewmodel.compose.viewModel\b\|navigate(\"contact/\"\|navigate(\"compose\")" app/src/main
```
Each hit gets reviewed: switch `viewModel()` → `hiltViewModel()`, and switch literal string navigations → `Screen.X.route` / `Screen.X.createRoute(...)`.

- [ ] **Step 2: Compile + run**

```bash
./gradlew :app:assembleDebug --stacktrace
```
Fix any breakage. Commit.

```bash
git add -A
git commit -m "ui: sweep call sites to hiltViewModel() + Screen.route"
```

---

## Task 11: Manual emulator validation

- [ ] **Step 1: Install**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Walk the rail**

1. App starts → Onboarding (if no password).
2. Set password → Home appears with the rail.
3. Tap `Compose` → ComposeScreen; rail highlights "compose".
4. Tap `Settings` → SettingsScreen.
5. Open a contact via Home → "Manage Contacts" → ContactDetail → "Exchange keys" → KeyExchange.
6. Rotate device; rail repositions; back stack preserved.
7. Tap back from a deep screen returns through the typed-arg chain without losing state.

- [ ] **Step 3: Document any AzNavRail edge cases**

If `currentDestination` highlighting misbehaves with nested routes (e.g. `contact/X/Y`), strip arg portion before passing to AzHostActivityLayout: `currentRoute?.substringBefore('/')`. Adjust as needed and commit.

---

## Acceptance criteria

- ✅ `ui/App.kt` is the only top-level shell; `ui/composable/AppNavRail.kt` is deleted.
- ✅ Every nav target is reachable from the rail (where appropriate) and via typed `Screen.X.createRoute(...)` calls.
- ✅ AzNavRail is pinned to a specific commit SHA in `libs.versions.toml`.
- ✅ Login gate works: logged-out users land on Onboarding, logged-in users on Home; navigating Onboarding → Home pops Onboarding off the back stack.
- ✅ Rail item selection state tracks the current route (verified by visual inspection on emulator).
- ✅ ContactDetail receives typed `contactLookupKey` and `contactName` args.
- ✅ KeyExchange flow: QR shows, scanning a peer's QR triggers `RatchetEngine.startSession`, the persisted state changes (`SELECT * FROM ratchet_state` shows updated `dhRemotePub`).
- ✅ Settings exposes an accessibility-allowlist editor; adding a package updates `serviceInfo.packageNames`.
- ✅ `./gradlew :app:assembleDebug testDebugUnitTest lintDebug` passes.

## Risk register

- **`AzHostActivityLayout` may not exist in the v9.0 artifact.** Task 4 includes a verification + fallback to `AzNavRail { ... }`. If even that doesn't ship the route-aware DSL, upgrade the version pin to a newer SHA.
- **AzNavRail's selection highlight may not match nested routes.** Mitigation in Task 11.
- **KeyExchange UX is intentionally minimal.** Two-step QR-scan flow with no error states beyond a snackbar. Acceptable for an MVP; Plan 5 may flag for future polish.
- **Accessibility allowlist override only takes effect on next connect.** Honest documentation in the Settings UI ("Restart the accessibility service for changes to apply").

## What's deliberately NOT in this plan

- AzNavRail tutorials/help system. Useful but out of scope.
- Tablet-optimized layouts (the rail handles orientation; no two-pane).
- Doc rewrites (Plan 5).
- Pinning the toolchain (Plan 5).
