# BarcodeNcrypt

I had this dream where, in the middle of our conversation, Dwayne "The Rock" Johnson pulled out his keys and scanned the barcode on a coupon club key fob.
"What was that?" I asked.
"Oh," The Rock showed me his fob. "This is my password."

While holes in a safe make for a way in, holes can be filled. Walls can be made stronger. The consistently most insecure part of security is how you unlock it, and people are bad at making their own keys secure. BarcodeNcrypt is an attempt to increase security without demanding more effort from your brain-hole.

## Concept & Core Features

BarcodeNcrypt is an Android application where physical barcodes serve as the keys for decrypting messages. The core idea is that any message can be encrypted, and to see the plaintext, the recipient must scan a predefined barcode. This introduces a tangible, physical barrier to digital security.

The app operates on these main principles:

1.  **Barcode-Based Decryption:** The key to unlocking a message is a physical barcode. This makes security tangible and less reliant on memorable (and often weak) passwords.
2.  **On-Screen Message Detection:** A background service watches for encrypted message headers on the screen. When a header is found, the app highlights the message, making it interactive.
3.  **Sender-Controlled Security:** The sender can define how long a message is visible (time-to-live) and how many times it can be decrypted. These "time bomb" parameters are embedded securely within the encrypted message itself.
4.  **Password Augmentation:** Users can combine a barcode with a password for two-factor security. This functionality will also be used to augment existing password managers with barcode-based authentication.
5.  **Interactive Tutorial:** A "Try It" mode provides a mock chat interface to walk users through the send/receive process, which also serves to test the app's core on-screen functionality.

For more detailed documentation, please see the full [documentation index](./docs/INDEX.md).

## How It Works Step-by-Step

### Encryption
1.  **Assign Barcode:** From the main screen, navigate to "Contacts". Select a contact and scan a barcode to assign it a unique identifier. This action creates an entry in a secure, encrypted log that manages the rolling keys for that contact.
2.  **Compose:** Go to "Compose". Select your recipient and the specific barcode you want to use.
3.  **Set Security:** Write your message and set your desired security options, such as how many times the message can be opened or for how long it will be valid.
4.  **Encrypt & Share:** Tap "Encrypt". The app generates the encrypted text, which you can then copy and paste into any other application to send.

### Decryption & Viewing
1.  **Enable Service:** From "Settings", enable the "On-Screen Detection Service" and grant the necessary Accessibility permissions.
2.  **Detect:** When an encrypted message appears on screen, the service will detect its header and highlight the ciphertext.
3.  **Scan & Reveal:** Tap the highlighted text. This will open the barcode scanner. Scan the correct barcode associated with the message.
4.  **View:** If the barcode is correct, the temporary key is retrieved, the message is decrypted, and the plaintext appears in place of the ciphertext. If the key is incorrect, the highlight will show gibberish.

## Technical Deep Dive

### Architecture
BarcodeNcrypt is built with a modern, single-activity architecture using Jetpack Compose and is navigated with **AzNavRail**.

*   **Single-Activity Architecture:** The app uses a single `MainActivity` to host all composable screens, with navigation handled by a `NavHost`.
*   **Background Services:** The core functionality relies on two services:
    *   `MessageDetectionService`: An `AccessibilityService` that scans on-screen text for the app's message headers.
    *   `OverlayService`: Draws the highlight over detected text and handles the tap interaction to initiate scanning.
*   **Dependency Injection:** Hilt is used to manage dependencies throughout the app.

### Security
The security model is designed around a rolling-key system managed by a central, encrypted script or log.

*   **Encrypted Log:** This is the core of the security model. It associates a contact's barcode identifier with their rolling encryption key. When a message is sent or received, this log provides a temporary, single-use key.
*   **Rolling Keys:** To ensure forward secrecy, a new key is used for each message, derived from a master key managed by the encrypted log.
*   **Message Header:** A simple header identifies encrypted messages and contains metadata such as the number of texts in a batch and the security parameters (time-to-live, open count).
