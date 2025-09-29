# BarCodeNcrypt

I had this dream where, in the middle of our conversation, Dwayne "The Rock" Johnson pulled out his keys and scanned the barcode on a coupon club key fob.    
"What was that?" I asked.     
"Oh," The Rock showed me his fob. "This is my password."    

While holes in a safe make for a way in, holes can be filled. Walls can be made stronger. The consistently most insecure part of security is how you unlock it, and people are bad at making their own keys secure. BarCodEncrypt is an attempt to increase security without demanding more effort from your brain-hole.

## Concept & Core Features

BarCodEncrypt is an Android application that uses physical barcodes and QR codes as keys for encryption and password management. The core idea is to introduce a tangible, physical barrier to digital security operations. The application is built with Jetpack Compose and Material 3 and operates on three main principles:

1.  **Physical Keys for Digital Security:** Instead of easily guessed or stolen digital passwords, the app uses physical barcodes as the root secret for cryptographic operations.
2.  **Ephemeral & Secure Messaging:** A user can link a barcode to a contact, encrypt a message, and share it through any platform. The app's background service detects this message on the recipient's screen and prompts them to scan the corresponding physical barcode to decrypt it. Messages can be configured to be single-use or to expire after a set time.
3.  **Passive Password Assistance:** The same background service can detect when a user selects a password field in any application. It provides a simple overlay to scan any barcode, instantly pasting its raw content into the field, acting as a simple, physically-keyed password manager.

## How It Works Step-by-Step

### Advanced Key Types

BarCodEncrypt supports two advanced key types for enhanced security:

*   **Password-Protected Keys:** When creating a key, you can choose to protect it with a password. When encrypting or decrypting with this key, you will be prompted to enter the password. The key is derived from both the barcode and the password, so an attacker would need both to compromise your messages.
*   **Barcode Sequence Keys:** You can use a sequence of barcodes as a single key. When creating the key, you can scan multiple barcodes in a specific order. To encrypt or decrypt, you will need to scan the same barcodes in the same order. This adds another layer of security, as an attacker would need to know the correct sequence of barcodes.

You can also combine these two features to create a password-protected barcode sequence key.

### Interactive Tutorial ("Try Me")

For new users, the app includes a "Try Me" button on the main navigation rail. This interactive tutorial guides you through a complete encryption and decryption cycle without needing a second person or device. It will walk you through:
1.  Scanning a barcode to act as a temporary, secret key.
2.  Showing you a mock conversation where a message is encrypted with your key.
3.  Guiding you to tap the highlighted message and scan the same barcode again to decrypt it.

### Encryption
1.  **Manage Keys:** From the main screen, select "Manage Contact Keys" to choose a contact from your phone's address book.
2.  **Assign Barcode:** In the contact detail screen, tap the '+' button to open the scanner. Scan a barcode and give it a unique name to assign it to that contact.
3.  **Compose:** From the main screen, select "Compose Message". Select your recipient and the specific key you want to use.
4.  **Encrypt & Share:** Write your message, choose your options (e.g., single-use), and tap "Encrypt". The encrypted text can then be copied and pasted into any other application.

### Decryption & Viewing
1.  **Enable Service:** From the main screen, enable the "Watcher Service" and grant the necessary Accessibility and Overlay permissions.
2.  **Detect:** When an encrypted message appears on screen, the Watcher service will detect it and place a semi-transparent yellow overlay on it.
3.  **Scan & Reveal:** Tap the overlay to open the barcode scanner. Scan the correct barcode that was used to encrypt the message. The overlay will turn green and reveal the plaintext.

## Technical Deep Dive

### Architecture
To achieve its passive, on-screen functionality, BarCodEncrypt integrates deeply with the Android OS. This is accomplished primarily through two background services:
* **`MessageDetectionService`**: An `AccessibilityService` that monitors the screen's content in real-time. It is responsible for detecting specially formatted encrypted text strings and identifying when the user has focused on a password input field.
* **`OverlayService`**: This service has `SYSTEM_ALERT_WINDOW` permission, allowing it to draw on top of other applications. When the `MessageDetectionService` finds a target, the `OverlayService` is responsible for rendering the decryption interface or the password assistant icon.

This architecture requires significant user permissions (`CAMERA`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`) to function.

### Security
To protect against physical-access attacks, the entire database is **encrypted at rest** using SQLCipher. On first launch, the user is required to create a master password. This password is used to derive an encryption key that locks the database. The app cannot be accessed, and no keys can be read, without first providing this master password.

### Cryptographic Model & Status
The application's security model has evolved to provide stronger guarantees. The core concept is **feature-complete** based on the v2 cryptographic design.

* **v1 (Legacy):** The initial proof-of-concept used standard AES-GCM for encryption.
* **v2 (Current & Implemented):** To provide **forward secrecy**, the current design uses an HMAC-based Key Derivation Function (HKDF, RFC 5869). Instead of using a barcode's raw value directly, a unique encryption key is derived for every single message by combining the barcode's secret value (the IKM) with a per-message salt and an incrementing counter. This ensures that the compromise of a single message key does not reveal the master key or any other message keys.
* **v3 (Implemented):** To provide **post-compromise security**, the app now implements a **Double Ratchet** algorithm, inspired by the Signal protocol. This combines a Diffie-Hellman (DH) ratchet for asynchronous key agreement with a symmetric-key ratchet for per-message keys. This ensures that even if a key is compromised, the session can "heal" and become secure again after a few message exchanges. The implementation uses Google's Tink library for the underlying `X25519` elliptic curve cryptography.
