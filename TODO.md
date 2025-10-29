# Development Roadmap

This document outlines the step-by-step plan to build the BarcodeNcrypt application from its current state to a production-ready release. This guide is intended to be followed by an AI developer.

## Phase 1: Core Cryptography and Data Layer

1.  **Implement the Encrypted Script/Log:**
    *   Create a secure mechanism for storing and managing rolling encryption keys for each contact. This will be the foundation of the app's security model.
    *   This should be a new class, separate from the `EncryptionManager`, that handles the creation and retrieval of temporary keys.

2.  **Implement the Rolling Encryption Key Logic:**
    *   Update the `EncryptionManager` to use the new encrypted script/log to generate a unique key for each message.
    *   Ensure that the `EncryptionManager` can request a new temporary key from the script/log for each encryption operation.

3.  **Define and Implement the Message Header:**
    *   Create a class or data structure to represent the message header, including the app identifier, message count, and security parameters.
    *   Update the `EncryptionManager` to prepend this header to all encrypted messages.

4.  **Implement Sender-Controlled Security:**
    *   Add a mechanism to the `EncryptionManager` to embed the time-to-live and opening count into the message header.
    *   Update the decryption logic to enforce these security parameters.

5.  **Implement Password-Protected Barcodes:**
    *   Update the key generation logic to incorporate a password, if one is provided by the user.
    *   The decryption key should be derived from both the barcode and the password.

## Phase 2: UI and User Experience

1.  **Integrate AzNavRail:**
    *   Replace the current navigation with the AzNavRail component.
    *   Create navigation items for "Contacts," "Compose," "Tutorial," and "Settings."

2.  **Implement the "Contacts" Screen:**
    *   Create a screen that displays a list of contacts and their assigned barcodes.
    *   Allow the user to add, remove, and manage the barcodes for each contact.

3.  **Implement the "Compose" Screen:**
    *   Create a screen for composing and encrypting messages.
    *   The user should be able to select a recipient, choose a barcode, write a message, and set the security parameters.

4.  **Implement the "Tutorial" Screen:**
    *   Create a mock chat interface that guides the user through the process of sending and receiving encrypted messages.
    *   This screen should also serve as a testbed for the on-screen detection and decryption functionality.

5.  **Implement the "Settings" Screen:**
    *   Create a screen where the user can enable and disable the on-screen detection service and grant the necessary permissions.

## Phase 3: On-Screen Interaction

1.  **Update the On-Screen Detection Service:**
    *   Update the `MessageDetectionService` to recognize the new message header.
    *   When a message is detected, the service should highlight it and prepare it for decryption.

2.  **Update the Decryption Overlay:**
    *   Update the `OverlayService` to display the decryption overlay when a message is tapped.
    *   If the correct barcode is scanned, the overlay should display the decrypted message. If not, it should display gibberish.

3.  **Implement the Password Assistant:**
    *   Update the `MessageDetectionService` to detect when a password field is focused.
    *   When a password field is detected, the `OverlayService` should display an icon that allows the user to scan a barcode to fill in the password.

## Phase 4: Production Readiness

1.  **Implement Comprehensive Testing:**
    *   Write unit tests for the `EncryptionManager`, the encrypted script/log, and the on-screen detection service.
    *   Write integration tests for the entire encryption and decryption workflow.
    *   Write end-to-end tests for the interactive tutorial.

2.  **Harden Security:**
    *   Encrypt the database at rest using SQLCipher.
    *   Perform a security audit of the entire codebase.

3.  **Refine the User Experience:**
    *   Add error handling and user feedback throughout the app.
    *   Ensure that the app is intuitive and easy to use.
