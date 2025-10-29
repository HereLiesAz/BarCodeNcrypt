# Data Layer

This document describes the data storage, cryptographic model, and key generation mechanisms of BarcodeNcrypt.

## Encrypted Script/Log

The core of BarcodeNcrypt's security model is the `EncryptedScriptLogManager`, which manages the rolling encryption keys for each barcode. When a barcode is used for the first time, an `EncryptedScriptLog` entity is created and stored in the database. This entity contains the master key (derived from the barcode value), the current chain key, and the message number.

For each new message, the `EncryptedScriptLogManager` uses a key derivation function (KDF) to generate a new message key from the current chain key. It then updates the chain key and increments the message number, ensuring that each message is encrypted with a unique key.

## Rolling Encryption Key

The rolling encryption key is implemented using a key derivation function (HKDF) based on HMAC-SHA256. The process is as follows:

1.  **Master Key:** The initial master key is the raw value of the barcode.
2.  **Chain Key:** For each message, a new chain key is derived from the previous chain key.
3.  **Message Key:** The message key is derived from the current chain key.

This approach provides forward secrecy, meaning that if a single message key is compromised, it cannot be used to decrypt past or future messages.

## Message Header

All encrypted messages in BarcodeNcrypt are identified by a `MessageHeader` data class. This header contains the following information:

*   **App Identifier:** A unique string that identifies the message as a BarcodeNcrypt message (e.g., "BCE").
*   **Message Count:** The number of messages included in the encrypted batch.
*   **Time-to-Live (TTL):** The time in milliseconds for which the message is valid.
*   **Open Count:** The maximum number of times the message can be opened.
*   **Timestamp:** The time at which the message was encrypted.
