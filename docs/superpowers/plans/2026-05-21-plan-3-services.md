# Plan 3 — Service & Overlay Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Both background services run as compliant foreground services with notification channels and `specialUse` justification. Plaintext-bearing windows are screenshot-blocked. The encryption overlay is reintroduced without the unsound `AccessibilityNodeInfo` global. `BarcodeAutofillService` is declared and produces a real `FillResponse` via a registered trampoline. Backup rules exclude key material. The accessibility service has a `packageNames` allowlist (configurable).

**Architecture:**
- Both `MessageDetectionService` and `OverlayService` call `startForeground(notifId, notification)` immediately in `onStartCommand` / `onCreate`, attached to a `NotificationChannel` of `IMPORTANCE_LOW`. The manifest gets a `<property name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="watch_for_encrypted_messages_on_screen">` (and similar for the overlay) per Android 14 policy.
- `AccessibilityNodeHolder` stays deleted (Plan 1). For the encryption overlay path, the Accessibility service captures the *value semantics* of the focused node — its `Rect`, its current text (truncated for logging), and an opaque `EditableTargetToken` (a one-shot, ID-only handle the service knows how to resolve back to a fresh node when paste happens). The token, bounds, and text travel through the Intent. `OverlayService` never receives an `AccessibilityNodeInfo`.
- Every Activity that can show plaintext (`ScannerActivity`, `ComposeScreen`'s host, `OnboardingScreen`'s host, anything that displays a decrypted message) sets `WindowManager.LayoutParams.FLAG_SECURE`. Overlay windows that display plaintext (decrypted suggestion overlay; new encryption overlay) get `FLAG_SECURE` on the `WindowManager.LayoutParams`.
- `BarcodeAutofillService` is declared in the manifest with `BIND_AUTOFILL_SERVICE` and an `autofill_service` metadata XML. `onFillRequest` finds password fields and returns a `Dataset` whose presentation is a `RemoteViews` "Scan with BarcodeNcrypt", with a `PendingIntent` to `PasswordScannerTrampolineActivity` (reintroduced, declared in manifest, `theme=Theme.Transparent`). The trampoline runs the scanner, derives the password, and returns the dataset via `EXTRA_AUTHENTICATION_RESULT`.
- `backup_rules.xml` and `data_extraction_rules.xml` explicitly include only neutral metadata and exclude the database, `barcodencrypt_prefs`, and the Tink keyset directory. `allowBackup` is flipped back to `true` since the rules are now defined.
- `accessibility_service_config.xml` gains `android:packageNames`. The default value comes from a string resource the user can edit; first ship with a representative messaging-app allowlist.

**Tech Stack additions:** none — this plan uses only platform APIs (NotificationManager, WindowManager flags, AutofillService, AccessibilityNodeInfo).

**Reading dependencies:** Plan 1 must be complete (Hilt active, no `AccessibilityNodeHolder`). Plan 2 may run in parallel; Plan 3 doesn't depend on the v5 protocol existing yet — it operates on whichever wire format is current.

---

## File structure

### Created
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/ForegroundNotifications.kt` — helper that creates the channel(s) and emits the persistent notifications.
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/EditableTargetRegistry.kt` — replaces `AccessibilityNodeHolder`. Owns a short-lived map of `tokenId → AccessibilityNodeInfo`, mutated only by `MessageDetectionService` on the main thread, cleared on overlay dismissal, and `recycle()`d on eviction.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordScannerTrampolineActivity.kt` — re-created. Transparent activity that bridges the autofill `PendingIntent` to `ScannerActivity` and returns a `Dataset`.
- `app/src/main/res/xml/autofill_service.xml` — describes the autofill service.
- `app/src/main/res/layout/autofill_suggestion.xml` — `RemoteViews` row for the autofill suggestion.
- `app/src/main/java/com/hereliesaz/barcodencrypt/util/SecureWindow.kt` — `fun ComponentActivity.markSecure()` extension that sets `FLAG_SECURE`; analogous helper for `WindowManager.LayoutParams`.

### Modified
- `app/src/main/AndroidManifest.xml` — `<service>` for `BarcodeAutofillService` with `BIND_AUTOFILL_SERVICE`; `<activity>` for `PasswordScannerTrampolineActivity`; `<property name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ...>` for both foreground services; `allowBackup=true` (rules now defined).
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt` — `startForeground` in `onServiceConnected`; uses `EditableTargetRegistry` instead of `AccessibilityNodeHolder`; recycles focused nodes after copying their value semantics.
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt` — `startForeground` in `onCreate`; receives `tokenId` extra instead of relying on a global; sets `FLAG_SECURE` on overlay windows that show plaintext.
- `app/src/main/java/com/hereliesaz/barcodencrypt/services/BarcodeAutofillService.kt` — implements `onFillRequest` fully; uses `PendingIntent` + trampoline.
- `app/src/main/res/xml/accessibility_service_config.xml` — adds `packageNames` allowlist and `notificationTimeout` 250 ms.
- `app/src/main/res/xml/backup_rules.xml` — explicit excludes for `database/`, `sharedpref/barcodencrypt_prefs.xml`, `files/tink_keyset/`.
- `app/src/main/res/xml/data_extraction_rules.xml` — same excludes for `cloud-backup` and `device-transfer`.
- `app/src/main/res/values/strings.xml` — strings for the notification channel name and the autofill suggestion text.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerActivity.kt` — calls `markSecure()` in `onCreate`.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ComposeScreen.kt` / host Activity — same.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/OnboardingScreen.kt` / host — same.
- `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt` — same.
- `app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt` — same (defensive).

### Deleted
- (none)

---

## Task 1: NotificationChannel + foreground notifications helper

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/services/ForegroundNotifications.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

```xml
<!-- res/values/strings.xml -->
<string name="fg_channel_name">BarcodeNcrypt background</string>
<string name="fg_channel_description">Required while the on-screen message detector and overlay run.</string>
<string name="fg_detector_title">Watching for encrypted messages</string>
<string name="fg_detector_text">Tap to manage detection settings.</string>
<string name="fg_overlay_title">BarcodeNcrypt overlay active</string>
<string name="fg_overlay_text">Highlighting an encrypted message.</string>
<string name="autofill_suggestion_text">Scan with BarcodeNcrypt</string>
```

- [ ] **Step 2: ForegroundNotifications.kt**

```kotlin
package com.hereliesaz.barcodencrypt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.R

internal object ForegroundNotifications {
    private const val CHANNEL_ID = "bce_fg"
    const val NOTIF_ID_DETECTOR = 0xBCE1
    const val NOTIF_ID_OVERLAY = 0xBCE2

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.fg_channel_description) }
        nm.createNotificationChannel(channel)
    }

    fun detector(context: Context): Notification = build(
        context,
        titleRes = R.string.fg_detector_title,
        textRes = R.string.fg_detector_text,
    )

    fun overlay(context: Context): Notification = build(
        context,
        titleRes = R.string.fg_overlay_title,
        textRes = R.string.fg_overlay_text,
    )

    private fun build(context: Context, titleRes: Int, textRes: Int): Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/java/com/hereliesaz/barcodencrypt/services/ForegroundNotifications.kt
git commit -m "services: add ForegroundNotifications helper + strings"
```

---

## Task 2: MessageDetectionService — foreground + EditableTargetRegistry

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/services/EditableTargetRegistry.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt`

- [ ] **Step 1: EditableTargetRegistry**

```kotlin
package com.hereliesaz.barcodencrypt.services

import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.MainThread
import java.util.UUID

/**
 * Replaces AccessibilityNodeHolder. Stores at most one editable target node and
 * exposes a one-shot, opaque token. Plan 3 contract:
 *
 *   - register() called only by MessageDetectionService, on the main thread,
 *     when an editable field gains focus.
 *   - take(token) returns the node and clears the registry — the caller owns
 *     recycling.
 *   - clear() recycles and drops any stale node when a new one is registered
 *     or when the detector signals the editable focus is gone.
 */
@MainThread
object EditableTargetRegistry {
    private var token: String? = null
    private var node: AccessibilityNodeInfo? = null

    fun register(target: AccessibilityNodeInfo): String {
        clear()
        val t = UUID.randomUUID().toString()
        token = t
        node = target
        return t
    }

    fun take(t: String): AccessibilityNodeInfo? {
        if (t != token) return null
        val n = node
        token = null
        node = null
        return n
    }

    fun clear() {
        node?.recycle()
        node = null
        token = null
    }
}
```

- [ ] **Step 2: Patch MessageDetectionService**

Rewrite for clarity (replaces the Plan 1 simplified version):

```kotlin
@AndroidEntryPoint
class MessageDetectionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_MS = 250L

    private val scanRunnable = Runnable {
        val root = rootInActiveWindow ?: return@Runnable
        try { scan(root) } finally { root.recycle() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundNotifications.ensureChannel(this)
        startForeground(
            ForegroundNotifications.NOTIF_ID_DETECTOR,
            ForegroundNotifications.detector(this),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.removeCallbacksAndMessages(null)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                handler.postDelayed(scanRunnable, DEBOUNCE_MS)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                EditableTargetRegistry.clear()
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun scan(root: AccessibilityNodeInfo) {
        val hits = MessageParser.findAllV5Tokens(root)
        if (hits.isNotEmpty()) {
            val (message, rect) = hits.first()
            startOverlay(message = message, bounds = rect, editableToken = null)
            return
        }
        val editable = findFocusedEditable(root)
        if (editable != null) {
            val rect = Rect().also { editable.getBoundsInScreen(it) }
            val token = EditableTargetRegistry.register(editable)
            startOverlay(message = null, bounds = rect, editableToken = token)
        } else {
            EditableTargetRegistry.clear()
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable && root.isFocused) return AccessibilityNodeInfo.obtain(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            try {
                val found = findFocusedEditable(child)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun startOverlay(message: String?, bounds: Rect, editableToken: String?) {
        stopService(Intent(this, OverlayService::class.java))
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_BOUNDS, bounds)
            editableToken?.let { putExtra(OverlayService.EXTRA_EDITABLE_TOKEN, it) }
            message?.let { putExtra(OverlayService.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startForegroundService(intent)
    }

    override fun onInterrupt() {}
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/services/EditableTargetRegistry.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/services/MessageDetectionService.kt
git commit -m "services: detector startForeground + EditableTargetRegistry replaces global holder"
```

---

## Task 3: OverlayService — foreground + FLAG_SECURE + token-based encryption overlay

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt`
- Restore (created): `app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/EncryptionOverlay.kt`
- Restore (created): `app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/EncryptionOverlayViewModel.kt`

(These two files were deleted in Plan 1. Plan 3 reintroduces them with proper node lifecycle.)

- [ ] **Step 1: Patch OverlayService**

```kotlin
@AndroidEntryPoint
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_BOUNDS = "extra_bounds"
        const val EXTRA_EDITABLE_TOKEN = "extra_editable_token"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ForegroundNotifications.ensureChannel(this)
        startForeground(
            ForegroundNotifications.NOTIF_ID_OVERLAY,
            ForegroundNotifications.overlay(this),
        )
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeOverlay()
        if (intent == null) return START_NOT_STICKY
        val bounds = intent.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java) ?: return START_NOT_STICKY
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val editableToken = intent.getStringExtra(EXTRA_EDITABLE_TOKEN)
        showOverlay(message = message, bounds = bounds, editableToken = editableToken)
        return START_NOT_STICKY
    }

    private fun showOverlay(message: String?, bounds: Rect, editableToken: String?) {
        overlayView = ComposeView(this).apply {
            setContent {
                BarcodencryptTheme {
                    when {
                        editableToken != null -> EncryptionOverlay(
                            onEncrypted = { ciphertext ->
                                val node = EditableTargetRegistry.take(editableToken)
                                try {
                                    node?.let {
                                        val args = Bundle().apply {
                                            putCharSequence(
                                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                                ciphertext,
                                            )
                                        }
                                        it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                                    }
                                } finally {
                                    node?.recycle()
                                    removeOverlay()
                                }
                            },
                            onCancel = { EditableTargetRegistry.clear(); removeOverlay() },
                        )
                        message != null -> SuggestionOverlay(
                            message = message,
                            onDecryptClick = {
                                val scanner = Intent(context, ScannerActivity::class.java).apply {
                                    action = Constants.ACTION_DECRYPT_MESSAGE
                                    putExtra(EXTRA_MESSAGE, message)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(scanner)
                                removeOverlay()
                            },
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (editableToken != null) bounds.right else bounds.left
            y = bounds.top
        }
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "addView failed", e)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                runCatching { windowManager.removeView(it) }
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        EditableTargetRegistry.clear()
    }
}
```

- [ ] **Step 2: Reintroduce EncryptionOverlay composable**

A new file `ui/composable/EncryptionOverlay.kt` that takes only callbacks (no `AccessibilityNodeInfo`):
```kotlin
@Composable
fun EncryptionOverlay(
    viewModel: EncryptionOverlayViewModel = hiltViewModel(),
    onEncrypted: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val contacts by viewModel.contacts.collectAsState()
    val barcodes by viewModel.barcodes.collectAsState()
    var text by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var ttl by remember { mutableStateOf("") }
    var openCount by remember { mutableStateOf("") }

    Column {
        when {
            selectedContact == null -> ContactPicker(contacts) { selectedContact = it; viewModel.loadBarcodes(it) }
            selectedBarcode == null -> BarcodePicker(barcodes) { selectedBarcode = it }
            else -> EncryptForm(
                text = text, onText = { text = it },
                ttl = ttl, onTtl = { ttl = it },
                openCount = openCount, onOpenCount = { openCount = it },
                onCancel = onCancel,
                onEncrypt = {
                    viewModel.encrypt(
                        plaintext = text,
                        contact = selectedContact!!,
                        barcode = selectedBarcode!!,
                        ttlMs = ttl.toLongOrNull(),
                        openMax = openCount.toIntOrNull(),
                    ) { ciphertext ->
                        if (ciphertext != null) onEncrypted(ciphertext) else onCancel()
                    }
                },
            )
        }
    }
}
```

(Implement `ContactPicker`/`BarcodePicker`/`EncryptForm` as small private composables — they were spread inline before; pull them out so the file is readable.)

- [ ] **Step 3: EncryptionOverlayViewModel**

```kotlin
@HiltViewModel
class EncryptionOverlayViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val barcodeRepository: BarcodeRepository,
    private val encryptionManager: EncryptionManager,
) : ViewModel() {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()
    private val _barcodes = MutableStateFlow<List<Barcode>>(emptyList())
    val barcodes = _barcodes.asStateFlow()

    init {
        viewModelScope.launch {
            contactRepository.getAll().collect { _contacts.value = it }
        }
    }

    fun loadBarcodes(contact: Contact) {
        viewModelScope.launch {
            barcodeRepository.getBarcodesForContactFlow(contact.lookupKey).collect {
                _barcodes.value = it
            }
        }
    }

    fun encrypt(
        plaintext: String, contact: Contact, barcode: Barcode,
        ttlMs: Long?, openMax: Int?, callback: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            barcode.decryptValue()
            callback(
                encryptionManager.encrypt(
                    plaintext = plaintext,
                    contactLookupKey = contact.lookupKey,
                    barcode = barcode,
                    password = null,
                    ttlMs = ttlMs,
                    openMax = openMax,
                )
            )
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hereliesaz/barcodencrypt/services/OverlayService.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/ui/composable/EncryptionOverlay.kt \
        app/src/main/java/com/hereliesaz/barcodencrypt/viewmodel/EncryptionOverlayViewModel.kt
git commit -m "services: overlay startForeground + FLAG_SECURE; re-add EncryptionOverlay via token"
```

---

## Task 4: Manifest — declare FGS subtype, autofill service, trampoline

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Patch manifest**

```xml
<application
    android:name=".BarcodeApplication"
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Barcodencrypt"
    android:enableOnBackInvokedCallback="true">

    <activity android:name=".MainActivity" android:exported="true" android:theme="@style/Theme.App.Starting">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <activity
        android:name=".ui.PasswordScannerTrampolineActivity"
        android:exported="false"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:excludeFromRecents="true"
        android:taskAffinity=""
        android:noHistory="true" />

    <service
        android:name=".services.MessageDetectionService"
        android:exported="false"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:foregroundServiceType="specialUse"
        tools:ignore="AccessibilityPolicy">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="watch_for_encrypted_messages_on_screen" />
    </service>

    <service
        android:name=".services.OverlayService"
        android:exported="false"
        android:foregroundServiceType="specialUse">
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="render_decryption_or_encryption_overlay" />
    </service>

    <service
        android:name=".services.BarcodeAutofillService"
        android:exported="true"
        android:permission="android.permission.BIND_AUTOFILL_SERVICE">
        <intent-filter>
            <action android:name="android.service.autofill.AutofillService" />
        </intent-filter>
        <meta-data
            android:name="android.autofill"
            android:resource="@xml/autofill_service" />
    </service>
</application>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "manifest: declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE, autofill service, trampoline"
```

---

## Task 5: BarcodeAutofillService — produce a real FillResponse

**Files:**
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/services/BarcodeAutofillService.kt`
- Create: `app/src/main/res/xml/autofill_service.xml`
- Create: `app/src/main/res/layout/autofill_suggestion.xml`
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordScannerTrampolineActivity.kt`

- [ ] **Step 1: autofill_service.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<autofill-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.hereliesaz.barcodencrypt.MainActivity"
    android:supportsInlineSuggestions="false" />
```

- [ ] **Step 2: autofill_suggestion.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/suggestion_text"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="8dp"
    android:text="@string/autofill_suggestion_text"
    android:textColor="?android:attr/textColorPrimary" />
```

- [ ] **Step 3: BarcodeAutofillService**

```kotlin
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.O)
class BarcodeAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run { callback.onSuccess(null); return }
        val target = findFirstPasswordField(structure) ?: run { callback.onSuccess(null); return }

        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion)
        presentation.setTextViewText(R.id.suggestion_text, getString(R.string.autofill_suggestion_text))

        val trampoline = Intent(this, PasswordScannerTrampolineActivity::class.java).apply {
            putExtra(PasswordScannerTrampolineActivity.EXTRA_AUTOFILL_ID, target)
        }
        val pending = PendingIntent.getActivity(
            this, 0, trampoline,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val authPresentation = RemoteViews(packageName, R.layout.autofill_suggestion).also {
            it.setTextViewText(R.id.suggestion_text, getString(R.string.autofill_suggestion_text))
        }
        val response = FillResponse.Builder()
            .setAuthentication(arrayOf(target), pending.intentSender, authPresentation)
            .build()
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) { callback.onSuccess() }

    private fun findFirstPasswordField(structure: AssistStructure): AutofillId? {
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            findPasswordRec(root)?.let { return it }
        }
        return null
    }

    private fun findPasswordRec(node: AssistStructure.ViewNode): AutofillId? {
        val looksLikePassword = (node.className?.contains("EditText", true) == true) &&
            (node.hint?.contains("password", true) == true || node.inputType and 0x80 == 0x80)
        if (looksLikePassword) return node.autofillId
        for (i in 0 until node.childCount) {
            findPasswordRec(node.getChildAt(i))?.let { return it }
        }
        return null
    }
}
```

- [ ] **Step 4: PasswordScannerTrampolineActivity**

```kotlin
@AndroidEntryPoint
class PasswordScannerTrampolineActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTOFILL_ID = "extra_autofill_id"
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val barcode = result.data?.getStringExtra(ScannerActivity.RESULT_KEY_SCANNED_VALUE)
        val autofillId: AutofillId? = intent.getParcelableExtra(EXTRA_AUTOFILL_ID, AutofillId::class.java)
        if (barcode == null || autofillId == null) {
            setResult(RESULT_CANCELED)
            finish()
            return@registerForActivityResult
        }
        val dataset = Dataset.Builder()
            .setValue(
                autofillId,
                AutofillValue.forText(barcode),
                RemoteViews(packageName, R.layout.autofill_suggestion).also {
                    it.setTextViewText(R.id.suggestion_text, getString(R.string.autofill_suggestion_text))
                },
            )
            .build()
        val data = Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset) }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanLauncher.launch(Intent(this, ScannerActivity::class.java).apply {
            action = Constants.ACTION_PICK_PASSWORD
        })
    }
}
```

(Add `Constants.ACTION_PICK_PASSWORD` and `ScannerActivity.RESULT_KEY_SCANNED_VALUE` to support this flow. The ScannerActivity must, on `ACTION_PICK_PASSWORD`, return the scanned value directly without invoking decryption.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "autofill: registered service + trampoline + real FillResponse"
```

---

## Task 6: FLAG_SECURE on plaintext-bearing windows

**Files:**
- Create: `app/src/main/java/com/hereliesaz/barcodencrypt/util/SecureWindow.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/ScannerActivity.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/MainActivity.kt`
- Modify: `app/src/main/java/com/hereliesaz/barcodencrypt/ui/PasswordActivity.kt`

- [ ] **Step 1: SecureWindow.kt**

```kotlin
package com.hereliesaz.barcodencrypt.util

import android.app.Activity
import android.view.WindowManager

fun Activity.markSecure() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
}
```

- [ ] **Step 2: Call markSecure() from each plaintext activity**

In each `onCreate`, after `super.onCreate(savedInstanceState)`:
```kotlin
markSecure()
```
Apply to: `MainActivity`, `ScannerActivity`, `PasswordActivity`, and `PasswordScannerTrampolineActivity`.

- [ ] **Step 3: Verify**

```bash
grep -rn "markSecure()" app/src/main/java/com/hereliesaz/barcodencrypt
```
Expected: 4 (or more) hits.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "security: FLAG_SECURE on activities that can show plaintext"
```

---

## Task 7: Backup rules

**Files:**
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: backup_rules.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- Exclude all sensitive material. Include only nothing-of-value defaults. -->
    <exclude domain="database" path="barcodencrypt_database" />
    <exclude domain="database" path="barcodencrypt_database-shm" />
    <exclude domain="database" path="barcodencrypt_database-wal" />
    <exclude domain="sharedpref" path="barcodencrypt_prefs.xml" />
    <exclude domain="file" path="tink_keyset" />
</full-backup-content>
```

- [ ] **Step 2: data_extraction_rules.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="barcodencrypt_database" />
        <exclude domain="database" path="barcodencrypt_database-shm" />
        <exclude domain="database" path="barcodencrypt_database-wal" />
        <exclude domain="sharedpref" path="barcodencrypt_prefs.xml" />
        <exclude domain="file" path="tink_keyset" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="barcodencrypt_database" />
        <exclude domain="database" path="barcodencrypt_database-shm" />
        <exclude domain="database" path="barcodencrypt_database-wal" />
        <exclude domain="sharedpref" path="barcodencrypt_prefs.xml" />
        <exclude domain="file" path="tink_keyset" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git commit -m "security: backup rules exclude database, prefs, and tink keyset"
```

---

## Task 8: AccessibilityService packageNames allowlist

**Files:**
- Modify: `app/src/main/res/xml/accessibility_service_config.xml`
- Add: a string-array resource for the default allowlist.

- [ ] **Step 1: strings.xml — add the allowlist**

```xml
<string-array name="accessibility_default_packages">
    <item>com.google.android.apps.messaging</item>
    <item>com.whatsapp</item>
    <item>org.thoughtcrime.securesms</item>
    <item>com.discord</item>
    <item>com.slack</item>
    <item>com.microsoft.teams</item>
    <item>org.telegram.messenger</item>
</string-array>
```

- [ ] **Step 2: accessibility_service_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewTextChanged|typeWindowStateChanged|typeWindowContentChanged|typeViewFocused|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="250"
    android:packageNames="@array/accessibility_default_packages" />
```

(If a user wants to broaden the allowlist at runtime, the Settings screen exposes an editable list and writes a `serviceInfo.packageNames` override at runtime via `setServiceInfo`. Plan 4 handles UI; this task only sets the safe default.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/xml/accessibility_service_config.xml
git commit -m "security: packageNames allowlist for accessibility service"
```

---

## Task 9: Manual emulator validation

- [ ] **Step 1: Install and launch on Android 14 emulator**

```bash
./gradlew :app:installDebug
adb shell am start -n com.hereliesaz.barcodencrypt/.MainActivity
```

- [ ] **Step 2: Verify**

1. **Foreground notifications.** Open Settings > Apps > BarcodeNcrypt > Notifications. Enable detector. Confirm a persistent low-priority notification appears for the detector.
2. **Overlay foreground.** Trigger an editable focus in another app (Messages); confirm the overlay notification appears while the overlay is visible.
3. **FLAG_SECURE.** With `ScannerActivity` open, attempt `adb shell screencap -p /sdcard/test.png` and `adb pull`. The capture must be black or contain the system "screenshot blocked" indicator.
4. **Autofill.** Settings > Passwords & accounts > Autofill service > select BarcodeNcrypt. Open a login screen in a test app. Confirm "Scan with BarcodeNcrypt" suggestion appears.
5. **Accessibility allowlist.** Confirm that opening the system Settings does NOT trigger overlay scans (system pkg not in allowlist).
6. **No crashes.** Background and foreground the app several times; no `ForegroundServiceDidNotStartInTimeException`.

- [ ] **Step 3: Document any failures as follow-up tasks**

If a step fails, capture the logcat and add a new task under this plan, then fix.

---

## Acceptance criteria

- ✅ Both services declare `<property name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` with a meaningful value.
- ✅ Both services call `startForeground(...)` before any other significant work.
- ✅ `AccessibilityNodeHolder` is gone; `EditableTargetRegistry` is the only state-sharing point and is `@MainThread`-annotated.
- ✅ The detector recycles every `AccessibilityNodeInfo` it obtains (verified by reading the code; no static-analysis tool covers this perfectly).
- ✅ `FLAG_SECURE` set on `MainActivity`, `ScannerActivity`, `PasswordActivity`, `PasswordScannerTrampolineActivity`; set on overlay `WindowManager.LayoutParams` (verified by `grep -rn "FLAG_SECURE" app/src/main`).
- ✅ `BarcodeAutofillService` is declared in the manifest, returns a real `FillResponse`, and the trampoline produces a `Dataset` with the scanned value.
- ✅ `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude the database, prefs, and tink keyset files.
- ✅ `accessibility_service_config.xml` has a `packageNames` allowlist.
- ✅ Manual emulator validation steps 1–6 all pass on Android 14.

## Risk register

- **Android 12+ `BIND_AUTOFILL_SERVICE` user-selectable settings change.** The trampoline depends on the user *first* choosing BarcodeNcrypt as the autofill provider. There is no programmatic way to set this; the Onboarding flow should link to the settings page. Plan 4 wires that link.
- **FLAG_SECURE breaks legitimate screen-sharing for accessibility users.** Users who need screen mirroring will see blank content from this app. Acceptable tradeoff; document in `docs/security.md` (Plan 5).
- **Accessibility allowlist may exclude an app the user actually uses.** Settings screen (Plan 4) provides a UI to add packages. Default allowlist covers the top messaging apps.
- **`startForegroundService` from the accessibility service.** Android 14 allows accessibility services to start foreground services even from background; verify with the manual test step. If it fails, fall back to a regular service started from `MainActivity` lifecycle.

## What's deliberately NOT in this plan

- The Settings UI for editing the allowlist (Plan 4).
- The CHANGELOG/docs updates (Plan 5).
- Anything about Plan 2's protocol — service hardening operates on whatever wire format is current.
