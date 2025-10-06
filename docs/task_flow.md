# Task Flow

This document outlines the step-by-step user flows for key management, encryption, and decryption in Barcodencrypt.

## How It Works Step-by-Step

### Encryption
1.  **Manage Keys:** From the main screen, select "Manage Contact Keys" to choose a contact from your phone's address book.
2.  **Assign Barcode:** In the contact detail screen, tap the '+' button to open the scanner. Scan a barcode and give it a unique name to assign it to that contact.
3.  **Compose:** From the main screen, select "Compose Message". Select your recipient and the specific key you want to use.
4.  **Encrypt & Share:** Write your message, choose your options (e.g., single-use), and tap "Encrypt". The encrypted text can then be copied and pasted into any other application.

### Decryption & Viewing
1.  **Enable Service:** From the main screen, enable the "Watcher Service" and grant the necessary Accessibility and Overlay permissions.
2.  **Detect:** When an encrypted message appears on screen, the Watcher service will detect it and place a semi-transparent yellow overlay on it.
3.  **Scan & Reveal:** Tap the overlay to open the barcode scanner. Scan the correct barcode that was used to encrypt the message. The overlay will turn green and reveal the plaintext.

### Interactive Tutorial ("Try Me")
For new users, the app includes a "Try Me" button on the main navigation rail. This interactive tutorial guides you through a complete encryption and decryption cycle without needing a second person or device. It will walk you through:
1.  Scanning a barcode to act as a temporary, secret key.
2.  Showing you a mock conversation where a message is encrypted with your key.
3.  Guiding you to tap the highlighted message and scan the same barcode again to decrypt it.