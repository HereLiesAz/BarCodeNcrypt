# BarCodeNcrypt

An Android app where physical barcodes are decryption keys. Scan a barcode to decrypt
messages on-screen; nothing leaves the device without a barcode the sender has agreed
on out-of-band.

## What's in v1.0

- **v5 Double Ratchet** crypto (Argon2id bootstrap + Tink X25519 + HKDF + AES-256-GCM).
  Wire format `~BCEv5~<base64>`; the binary header is GCM-authenticated. Out-of-order
  delivery within a 1000-message skip window. See
  [`docs/crypto/wire-format.md`](docs/crypto/wire-format.md).
- **Accessibility-service-driven overlay** — `MessageDetectionService` watches active
  windows for `~BCEv5~` tokens, hands them to an `OverlayService` that renders an
  in-place decrypt / encrypt UI. The plaintext window is `FLAG_SECURE` so it can't be
  screenshotted.
- **SQLCipher**-encrypted Room database (`barcodencrypt_database`). Key material is
  excluded from auto-backup and from device-transfer.
- **Hilt** end-to-end: `@HiltAndroidApp` on the Application, `@AndroidEntryPoint` on
  every Activity / Service, `@HiltViewModel` for every ViewModel.
- **AzNavRail 9.x** chrome via `AzHostActivityLayout` + `AzNavHost`. Rail items: Home,
  Compose, Scan. Menu: Settings. Onboarding stays outside the rail.
- **Firebase Auth** (Google sign-in via Credential Manager, plus a local master
  password fallback). **App Check** uses Play Integrity in release builds and the
  debug provider in debug builds.
- **R8 minification** with concrete keep-rules for every reflective library; the
  release APK builds cleanly.

## Privacy

BarCodeNcrypt stores barcode-derived ratchet state locally only. There is no
server-side message store and no telemetry. The accessibility service runs only when
the user enables it in system settings; it does not exfiltrate node content. Firebase
Auth identifiers (uid / display name / email) are persisted by the SDK for sign-in
state restoration. The full privacy policy is published at the URL declared in the
Play Store listing.
