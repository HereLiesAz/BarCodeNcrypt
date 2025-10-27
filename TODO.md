# Todo

A list of sorrows now embraced.

## Core Functionality

-   [x] Implement the camera and barcode scanning flow. This involves:
    -   [x] Creating a dedicated scanner activity (`ScannerActivity`).
    -   [x] Requesting camera permissions at runtime.
    -   [x] Configuring CameraX and the ML Kit barcode analyzer.
    -   [x] Passing a scanned barcode back to the calling process.
-   [x] Build out the `MessageDetectionService` to actually parse screen content.
    -   [x] Settle on a definitive message header format (`BCE::v1::{opts}::{id}::`).
    -   [x] Develop a robust method to identify and extract the encrypted payload from a `AccessibilityNodeInfo` tree.
    -   [x] Implement text highlighting on the detected message using an `OverlayService`.
-   [x] Flesh out the `EncryptionManager`. The current AES/GCM implementation is solid, but advanced features are pending.
    -   [x] Design the rolling key system.
    -   [x] Implement the v3 Double Ratchet protocol for temporary key exchange.
-   [x] Create the UI for adding/managing contacts and their associated barcodes (`ContactDetailActivity`).
-   [x] Create the UI for composing and encrypting a new message (`ComposeActivity`).

## User Experience

-   [x] Add an interactive "Try Me" tutorial to guide new users through the core encryption/decryption workflow.

## Refinements & Fixes

-   [x] The app does not yet ask for Accessibility Service permissions. The user must enable it manually. This needs a proper onboarding flow. (MainActivity now guides the user to the settings).
-   [x] The UI is a barren wasteland. It needs a soul. Or at least a coherent design. (The UI is now built with Jetpack Compose and Material 3).
-   [x] Error handling. What happens when a barcode is scanned that matches no contact? What happens when decryption fails? (The overlay now shows an "Incorrect Key" state).
-   [x] Harden security by encrypting the database at rest with SQLCipher.