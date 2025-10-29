# Workflow

This document explains the encryption and decryption process in BarcodeNcrypt.

## Encryption

1.  **Assign Barcode:** Assign a barcode to a contact. When the barcode is first used for encryption, the `EncryptedScriptLogManager` will create a new `EncryptedScriptLog` entity in the database.
2.  **In-Place Encryption:** When you are in any app and want to send an encrypted message, the BarcodeNcrypt overlay will appear.
3.  **Get Temporary Key:** The `EncryptionManager` requests a new temporary key from the `EncryptedScriptLogManager`.
4.  **Encrypt:** The `EncryptionManager` uses the temporary key to encrypt the message and prepends the `MessageHeader`.

## Decryption

1.  **On-Screen Detection:** The app's background service detects a BarcodeNcrypt message on the screen and highlights it.
2.  **Scan Barcode:** The user taps the highlighted message, which opens the barcode scanner.
3.  **Get Temporary Key:** The `EncryptionManager` requests a new temporary key from the `EncryptedScriptLogManager`.
4.  **Decrypt Message:** The `EncryptionManager` uses the temporary key to decrypt the message. If the key is correct, the message is displayed in plaintext. If not, the user sees gibberish.
