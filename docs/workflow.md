# Workflow

This document explains the encryption and decryption process in Barcodencrypt.

## Encryption

1.  **Manage Keys:** From the main screen, select "Manage Contact Keys" to choose a contact from your phone's address book.
2.  **Assign Barcode:** In the contact detail screen, tap the '+' button to open the scanner. Scan a barcode and give it a unique name to assign it to that contact.
3.  **Compose:** From the main screen, select "Compose Message". Select your recipient and the specific key you want to use.
4.  **Encrypt & Share:** Write your message, choose your options (e.g., single-use), and tap "Encrypt". The encrypted text can then be copied and pasted into any other application.

## Decryption & Viewing

1.  **Enable Service:** From the main screen, enable the "Watcher Service" and grant the necessary Accessibility and Overlay permissions.
2.  **Detect:** When an encrypted message appears on screen, the Watcher service will detect it and place a semi-transparent yellow overlay on it.
3.  **Scan & Reveal:** Tap the overlay to open the barcode scanner. Scan the correct barcode that was used to encrypt the message. The overlay will turn green and reveal the plaintext.

## Key Generation and Management

### Encrypting a Message

1.  The user selects a recipient and a key (barcode).
2.  The application retrieves the `IKM` for the selected barcode.
3.  The **Message Counter** for that barcode is incremented in the database. Let the new value be `C`.
4.  The application generates a new random **Salt**.
5.  The application computes the **PRK** using HKDF-Extract: `PRK = HKDF-Extract(Salt, IKM)`.
6.  The application computes the unique message key using HKDF-Expand:
    `MessageKey = HKDF-Expand(PRK, info="BCEv2|msg" | C, length=32)`
7.  This `MessageKey` is used to encrypt the plaintext with AES-256-GCM.
8.  The `Salt` is prepended to the IV and ciphertext, and the result is Base64 encoded.
9.  The value of the counter `C` is embedded into the new v2 message header.

### Decrypting a Message

1.  The application detects a v2 message and parses the header to extract the `barcode_identifier` and the `Message Counter` (`C`).
2.  The application retrieves the `IKM` for the given `barcode_identifier`.
3.  The application Base64-decodes the payload and extracts the **Salt** from the beginning of the payload.
4.  The application re-computes the **PRK** using the extracted Salt and the stored IKM: `PRK = HKDF-Extract(Salt, IKM)`.
5.  The application re-computes the message key using the exact same HKDF-Expand function as the sender:
    `MessageKey = HKDF-Expand(PRK, info="BCEv2|msg" | C, length=32)`
6.  This `MessageKey` is used to decrypt the rest of the payload (IV + ciphertext) with AES-256-GCM.