# Screens

This document describes the main screens and UI components of the Barcodencrypt application.

## The Hierophant (The UI Layer)

The Hierophant is the collection of Activities and Composable UIs through which the user interacts with the esoteric backend. It is the bridge between the user's world and the hidden machinery of the app, making the abstract tangible.

### Main Screen
The main screen of the application provides access to the core features:
*   **Manage Contact Keys:** Navigates to a screen where you can choose a contact from the address book to manage their associated barcodes.
*   **Compose Message:** Opens the message composition screen.
*   **"Try Me" Button:** An interactive tutorial that guides new users through a complete encryption and decryption cycle.
*   **Watcher Service Toggle:** Allows the user to enable or disable the `MessageDetectionService` and grant the necessary permissions.

### Key Activities
*   **`ContactDetailActivity`**: Presents the Scribe's records, showing the barcodes (keys) assigned to a specific contact.
*   **`ComposeActivity`**: Allows for the creation of new encrypted messages. The user can select a recipient, a key, write the message, and choose options like single-use.
*   **`ScannerActivity`**: Initiates the scanning ritual that awakens the other agents. This activity is used for adding new keys, decrypting messages, and using the password assistant.

### UI Managers
*   **`ScannerManager`**: A central nerve that allows other components, like the Poltergeist (OverlayService), to command the physical body to open its eye (the camera).