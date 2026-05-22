# Release process — v1.0 → Play Store

This document is the runbook for producing a signed `.aab` and pushing it through the
Play Console internal track.

## 1. Generate the upload keystore (one-time, kept out of the repo)

```sh
keytool -genkey -v \
  -keystore barcodencrypt-upload.jks \
  -alias barcodencrypt-upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

Keep `barcodencrypt-upload.jks` in a password manager / hardware key, **not in the
repo**. `*.jks`, `*.keystore`, and `keystore.properties` are all in `.gitignore`.

## 2. Local signed builds

Drop a `keystore.properties` next to `settings.gradle.kts` (it's git-ignored):

```properties
storeFile=/absolute/path/to/barcodencrypt-upload.jks
storePassword=…
keyAlias=barcodencrypt-upload
keyPassword=…
```

Then:

```sh
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

The `app/build.gradle.kts` signing config also reads `KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` environment variables when set, so CI
doesn't need a file.

## 3. CI release workflow

`.github/workflows/release.yml` runs on `workflow_dispatch`. Required secrets:

| Secret                   | Contents                                                     |
| ------------------------ | ------------------------------------------------------------ |
| `GOOGLE_SERVICES_JSON_B64` | `base64 -w0 app/google-services.json` of the production JSON |
| `KEYSTORE_B64`           | `base64 -w0 barcodencrypt-upload.jks`                        |
| `KEYSTORE_PASSWORD`      | matches the `keytool -genkey` store password                 |
| `KEY_ALIAS`              | `barcodencrypt-upload`                                       |
| `KEY_PASSWORD`           | matches the `-keypass` value                                 |

Trigger: GitHub → Actions → "Release AAB" → Run workflow. The signed AAB is uploaded
as the `barcodencrypt-<versionName>-release` artifact; on the default branch the
workflow also tags the commit `v<versionName>`.

## 4. Play Console — first submission

These are the bits that can't be automated; do them once per app.

1. **Create app** in the Play Console. Bundle ID `com.hereliesaz.barcodencrypt`.
2. **Data Safety form** — disclose:
   - **Encrypted contact data** stored on-device (Room + SQLCipher).
   - **Encrypted message metadata** stored on-device (ratchet state, opened-message
     counters).
   - **ML Kit barcode** — runs on-device, no payload leaves the phone.
   - **Firebase Auth identifiers** (uid / email / display name) persisted by the SDK
     for sign-in state.
   - **No telemetry, no analytics, no ads.**
3. **Content rating** — fill the questionnaire honestly; the app has no UGC visible
   to other users.
4. **Target audience** — adults (13+ in the US, equivalent elsewhere).
5. **Accessibility service declared use** — **start this early.** Play has a separate
   review track for apps that use `BIND_ACCESSIBILITY_SERVICE`. Justify the use by
   pointing at the
   [`accessibility_service_config.xml`](../app/src/main/res/xml/accessibility_service_config.xml)
   declaration and the `MessageDetectionService` source: the service only reads
   on-screen text to detect `~BCEv5~` tokens; it does not exfiltrate node content;
   it has no analytics. Submit a screen recording showing detect → overlay → decrypt
   on a real device.
6. **Privacy policy URL.** Required field. Publish via GitHub Pages under the
   `HereLiesAz` org. The policy must cover: local-only data storage, Firebase Auth
   identifiers, accessibility-service scope, contact-permission scope, no analytics,
   no third-party sharing.
7. **Store listing assets:**
   - App icon (already in `app/src/main/res/mipmap-*/ic_launcher.{webp,png}`).
   - Feature graphic: 1024×500 PNG.
   - At least 2 phone screenshots (16:9 or 9:16, 320–3840 px on the long edge).
   - Short description (≤ 80 chars) and full description (≤ 4000 chars).

## 5. Rollout

1. **Internal testing track first** — invite 1–3 testers (yourself, ideally a second
   device). Verify:
   - Play Integrity App Check passes (no `appCheck failed` in Logcat).
   - SQLCipher database opens (the first run after install should set a master
     password before any DB read).
   - The accessibility service starts as a foreground service without
     `ForegroundServiceDidNotStartInTimeException`.
   - The encrypt → decrypt round trip works between two devices that share a barcode.
2. **Closed testing** — invite ~10 testers via email or a closed-list link.
3. **Open testing** — opt-in URL on the GitHub repo.
4. **Production** — promote from open testing once App Check pass rate is healthy and
   no crash spikes appear in Play Console.

## 6. Future toolchain notes

- The `net.zetetic:android-database-sqlcipher:4.5.4` artifact is the legacy distribution.
  The new `net.zetetic:sqlcipher-android:4.6.x` lives at a different package and rewrites
  the `SupportFactory` API. Migrate in a follow-up commit so the ratchet bootstrap stays
  bisectable.
- AzNavRail 9.x ships an `AzNavRailOverlayService` and a 4-detent `azBottomSheet`. If
  the bespoke `OverlayService` becomes unwieldy, replace it with the library's overlay
  shell; the API differences are documented in the AzNavRail `MIGRATION_GUIDE.md`.
