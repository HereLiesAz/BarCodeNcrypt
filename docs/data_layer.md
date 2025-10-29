# Data Layer

This document describes the data storage, cryptographic model, and key generation mechanisms of BarcodeNcrypt.

## Encrypted Script/Log

The core of BarcodeNcrypt's security model is an encrypted script/log that manages the rolling encryption keys. When a user assigns a barcode to a contact, the app generates a unique identifier for that barcode. This identifier is then used to create an encrypted file that is managed by the encrypted script/log.

The script passes a temporary encrypted key back to the app with a rolling key of its own. This rolling key is used to encrypt and decrypt messages, ensuring that each message is encrypted with a unique key.

## Rolling Encryption Key

The rolling encryption key is a key that changes with each message. This is achieved by using a key derivation function (KDF) to derive a new key for each message from a master key. The master key is stored in the encrypted script/log and is never directly exposed to the app.

This approach provides forward secrecy, meaning that if a single message key is compromised, it cannot be used to decrypt past or future messages.

## Message Header

All encrypted messages in BarcodeNcrypt are identified by a simple header. This header contains the following information:

*   **App Identifier:** A unique string that identifies the message as a BarcodeNcrypt message.
*   **Message Count:** The number of messages included in the encrypted batch.
*   **Security Parameters:** The security parameters for the message, such as the time-to-live and the opening count.

When the app detects a message with this header, it initiates the decryption process.
