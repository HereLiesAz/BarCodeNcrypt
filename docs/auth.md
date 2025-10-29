# Security Model

This document describes the security features of BarcodeNcrypt, including sender-controlled security and password protection.

## Sender-Controlled Security

BarcodeNcrypt empowers the sender of a message to control its security parameters. When sending a message, the sender can define:

*   **Time-to-Live (TTL):** The sender can set a time limit for how long a message can be decrypted. After the time limit has expired, the message can no longer be decrypted.
*   **Opening Count:** The sender can specify how many times a message can be decrypted. Once the message has been decrypted the specified number of times, it can no longer be decrypted.

These security parameters are embedded within the encrypted message and are automatically enforced by the app.

## Password Protection

For an additional layer of security, users can combine a barcode with a password. When this feature is enabled, the decryption key is derived from both the barcode and the password. This means that an attacker would need to have both the physical barcode and the password to decrypt a message.

This feature is also used by the app's password assistant, which allows users to fill in password fields by scanning a barcode. In the future, this functionality will be extended to augment existing password providers with barcode-based authentication.
