# BarCodeNcrypt

I had this dream where, in the middle of our conversation, Dwayne "The Rock" Johnson pulled out his keys and scanned the barcode on a coupon club key fob.
"What was that?" I asked.
"Oh," The Rock showed me his fob. "This is my password."

While holes in a safe make for a way in, holes can be filled. Walls can be made stronger. The consistently most insecure part of security is how you unlock it, and people are bad at making their own keys secure. BarCodEncrypt is an attempt to increase security without demanding more effort from your brain-hole.

For more detailed information, please see the full [documentation](./docs/INDEX.md).

## Core Features

*   **Barcode-Based Decryption:** Encrypt messages with a barcode, and decrypt them by scanning the same barcode.
*   **On-Screen Detection:** A background service detects encrypted messages on the screen and allows for in-place decryption.
*   **Sender-Controlled Security:** Senders can set a time limit or a limit on the number of times a message can be decrypted.
*   **Password Protection:** For added security, a barcode can be combined with a password.
*   **Interactive Tutorial:** A built-in tutorial with a mock chat interface to guide users through the app's features.
*   **Customizable Navigation:** The app uses the AzNavRail component for a customizable and intuitive navigation experience.
