# Authentication

This document describes the key types and password protection mechanisms in Barcodencrypt.

## Advanced Key Types

BarCodEncrypt supports two advanced key types for enhanced security:

*   **Password-Protected Keys:** When creating a key, you can choose to protect it with a password. When encrypting or decrypting with this key, you will be prompted to enter the password. The key is derived from both the barcode and the password, so an attacker would need both to compromise your messages.
*   **Barcode Sequence Keys:** You can use a sequence of barcodes as a single key. When creating the key, you can scan multiple barcodes in a specific order. To encrypt or decrypt, you will need to scan the same barcodes in the same order. This adds another layer of security, as an attacker would need to know the correct sequence of barcodes.

You can also combine these two features to create a password-protected barcode sequence key.