# BarcodeNcrypt

An Android app called BarcodeNcrypt (com.hereliesaz.barcodencrypt). The idea is that any message you want to send encrypted, you can. And the key to unlocking the message is a predefined barcode that must be scanned to see it.

For more detailed information, please see the full [documentation](./docs/INDEX.md).

## Core Features

*   **Barcode-Based Decryption:** Encrypt messages with a barcode, and decrypt them by scanning the same barcode.
*   **On-Screen Detection:** A background service detects encrypted messages on the screen and allows for in-place decryption.
*   **Sender-Controlled Security:** Senders can set a time limit or a limit on the number of times a message can be decrypted.
*   **Password Protection:** For added security, a barcode can be combined with a password.
*   **Interactive Tutorial:** A built-in tutorial with a mock chat interface to guide users through the app's features.
*   **Customizable Navigation:** The app uses the AzNavRail component for a customizable and intuitive navigation experience.
