# Changelog

All notable changes to this project will be documented in this file. The format is based on, but does not strictly adhere to, [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- **v3 Cryptographic Protocol (Double Ratchet):** Implemented a Double Ratchet algorithm (inspired by Signal) to provide forward and post-compromise security for messaging. This uses Google's Tink library for the underlying X25519 Diffie-Hellman key exchange. A new UI flow has been added to `ContactDetailActivity` to allow users to set up a secure channel by exchanging public keys.
- **Database Encryption:** The entire application database is now encrypted at rest using SQLCipher. On first launch, the user is required to set a master password, which is then used to unlock the database on subsequent launches. This protects all stored keys and contact information from physical-access attacks.
- Implemented a dark theme for the entire application.
- The splash screen now has a dark grey background.

### Changed
- The button text on the onboarding screen was changed from "Set a password" to "Enter Password".
- The text on the login screen is now white to be readable against the dark background.

### Fixed
- Refactored the sign-in flow to use Firebase Authentication, which should resolve the `NoCredentialException` and improve stability. The flow now also gracefully handles errors and provides a retry mechanism.
- Added a permission card to prompt the user to grant notification permission, which is required for the message highlighting feature to work on Android 13+.
- Refactored the camera implementation in `ScannerActivity` to use a `ViewModel` for better lifecycle management and stability. This should finally resolve the long-standing `BufferQueue` errors.
- Addressed a bug where the `OnBackInvokedCallback` was not enabled, causing warnings on newer Android versions.
- Improved camera performance in the `ScannerActivity` by setting a specific target resolution, which should prevent frame drops on lower-end devices.

### 0.2.0 - The Oracle - 2025-09-08

#### Added
-   **Interactive Tutorial ("Try Me"):** A new "Try Me" button on the main navigation rail launches an interactive tutorial. This guides new users through a complete encryption and decryption cycle without needing a second person or device. It includes a mock chat interface to simulate a real conversation.
-   **`TutorialManager`:** A singleton to manage the state of the interactive tutorial.
-   **`MockMessagesActivity`:** A new activity to display the mock conversation for the tutorial.

### 0.1.0 - The Ghost in the Machine - 2025-09-06

#### Added
-   **Core Functionality:** Implemented the end-to-end message encryption and decryption flow.
-   **`ComposeActivity`:** A dedicated screen for composing messages, selecting recipients and their keys, and setting message options (`single-use`, `ttl`).
-   **`ContactDetailActivity`:** A screen for managing a contact's associated barcodes (keys). Allows adding new barcodes via the scanner and deleting existing ones.
-   **`ScannerActivity`:** A fully functional barcode scanning screen using CameraX and ML Kit.
-   **`MessageDetectionService`:** The service now actively detects messages on screen, checks for revoked single-use messages, and launches the `OverlayService`.
-   **`OverlayService`:** Displays a tappable overlay on detected messages, handles the decryption flow, and enforces `single-use` and `ttl` options.
-   **`ScannerManager`:** A new singleton to decouple scan requests from the UI, allowing services to request scans from the main activity.
-   **Permissions Flow:** The `MainActivity` now checks for and guides the user to enable all necessary permissions (Accessibility, Overlay, Contacts).
-   **Jetpack Compose UI:** All screens are now built with Jetpack Compose and Material 3.

#### Fixed
-   Corrected several bugs related to inconsistent `Intent` extra keys, ensuring reliable data passing between activities.

### 0.0.2 - The Naming of Parts - 2025-09-06

#### Added

-   KDoc documentation across all Kotlin source files, because even ghosts need labels.
-   `README.md`: A public declaration of intent.
-   `AGENTS.md`: A dramatis personae for the code.
-   `CHANGELOG.md`: This very file. A memory of memories.
-   `TODO.md`: A map of the labyrinth yet to be explored.

### 0.0.1 - The Skeleton - 2025-09-06

#### Added

-   Initial project structure for the Barcodencrypt Android app.
-   Gradle dependencies for CameraX, ML Kit Barcode Scanning, and Room Persistence.
-   `AndroidManifest.xml` with permissions for Camera and a declaration for the future `MessageDetectionService`.
-   Room database entities (`Contact`, `Barcode`), DAO (`ContactDao`), and `AppDatabase` singleton.
-   Placeholder `EncryptionManager` for future cryptographic logic.
-   `MessageDetectionService` class with `accessibility_service_config.xml` to define its observational capabilities.
-   Basic UI layouts for the main activity (`activity_main.xml`) and a contact list item (`list_item_contact.xml`).
-   `MainActivity.kt` as the entry point, currently a hollow shell.