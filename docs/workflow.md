# Workflow

This document explains the encryption and decryption process in BarcodeNcrypt.

## Encryption

1.  **Assign Barcode:** Assign a barcode to a contact. This creates an encrypted file in the app's script/log, which manages the rolling encryption key for that contact.
2.  **Compose Message:** When you compose a message, the app requests a temporary encrypted key from the script/log.
3.  **Encrypt and Send:** The app uses the temporary key to encrypt the message. The encrypted message, along with its header, can then be sent through any messaging app.

## Decryption

1.  **On-Screen Detection:** The app's background service detects a BarcodeNcrypt message on the screen and highlights it.
2.  **Scan Barcode:** The user taps the highlighted message, which opens the barcode scanner.
3.  **Decrypt Message:** The app uses the scanned barcode to retrieve the correct rolling key from the script/log and decrypt the message. If the key is correct, the message is displayed in plaintext. If not, the user sees gibberish.
