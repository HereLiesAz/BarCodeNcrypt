# UI/UX

This document covers the user interaction and experience aspects of Barcodencrypt.

## The Poltergeist (OverlayService)

A summoned spirit, a localized disturbance. The Poltergeist is called forth by the Watcher to haunt a specific location on the screen. It can manifest in two forms:

*   **Decryption Overlay:** A shimmering, semi-transparent yellow overlay that appears over encrypted text. Tapping this overlay initiates the decryption process, prompting the user to scan the correct barcode. Once the correct key is presented, the overlay turns green, revealing the plaintext. It can be configured to linger for a set time or to vanish after a single viewing.
*   **Password Assistant:** A small, discreet icon that appears next to a password field. Tapping this icon opens the barcode scanner, allowing the user to fill the password field with the raw content of any barcode.

## Jetpack Compose UI

The app's UI is built entirely with Jetpack Compose, a modern toolkit for building native Android UI. This provides a more declarative and reactive UI, which is easier to develop and maintain. The app uses a single-activity architecture, with a `MainActivity` that hosts all the composable screens. Navigation is handled by a `NavHost` in `Navigation.kt`, which defines the different screens and their routes.

## Interactive Tutorial ("Try Me")

For new users, the app includes a "Try Me" button on the main navigation rail. This interactive tutorial provides a hands-on experience of the core functionality without requiring a second person or device. The tutorial guides the user through:
1.  Scanning a barcode to act as a temporary, secret key.
2.  Displaying a mock conversation where a message is encrypted with the user's key.
3.  Prompting the user to tap the highlighted message and scan the same barcode again to decrypt it, demonstrating the end-to-end workflow in a controlled environment.
