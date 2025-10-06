# UI/UX

This document covers the user interaction and experience aspects of Barcodencrypt.

## The Poltergeist (OverlayService)

A summoned spirit, a localized disturbance. The Poltergeist is called forth by the Watcher to haunt a specific location on the screen. It can manifest in two forms:

*   **Decryption Overlay:** A shimmering, semi-transparent yellow overlay that appears over encrypted text. Tapping this overlay initiates the decryption process, prompting the user to scan the correct barcode. Once the correct key is presented, the overlay turns green, revealing the plaintext. It can be configured to linger for a set time or to vanish after a single viewing.
*   **Password Assistant:** A small, discreet icon that appears next to a password field. Tapping this icon opens the barcode scanner, allowing the user to fill the password field with the raw content of any barcode.

## Interactive Tutorial ("Try Me")

For new users, the app includes a "Try Me" button on the main navigation rail. This interactive tutorial provides a hands-on experience of the core functionality without requiring a second person or device. The tutorial guides the user through:
1.  Scanning a barcode to act as a temporary, secret key.
2.  Displaying a mock conversation where a message is encrypted with the user's key.
3.  Prompting the user to tap the highlighted message and scan the same barcode again to decrypt it, demonstrating the end-to-end workflow in a controlled environment.

## User Experience for Key Exchange

The biggest user experience challenge is the initial exchange of a contact's long-term DH public key for the planned v3 protocol. This needs to be a smooth and understandable process for the user. A "Create Secure Channel" flow might be needed that guides the user to scan two QR codes: one for the shared secret and one for the contact's public key. (Note: v3 is currently blocked).