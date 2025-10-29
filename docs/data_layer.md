# Data Layer

This document describes the data storage, cryptographic model, and key generation mechanisms of Barcodencrypt.

## The Scribe (AppDatabase)

The keeper of records, the lonely archivist. The Scribe is a `Room` database that maintains the fragile connections between contacts and the barcodes assigned to them. It remembers faces (contacts) and the sigils they carry (barcodes). It also keeps a blacklist of single-use messages that have been read and returned to the ether. This is the institutional memory of the system, a log of pacts made and whispers spent. Without the Scribe, every key is a stranger and every message is eternal.

The database is provided to the repositories using Hilt for dependency injection.

## The Alchemist (EncryptionManager)

The heart of the mystery. The Alchemist is responsible for the great work: transmutation. It turns meaningful text into noise and, with the correct catalyst (the key), turns the noise back into meaning. It is a master of `AES/GCM`, weaving a new layer of obfuscation with every message, ensuring that only the intended key can unlock the secret.

## Cryptographic Model & Status

The application's security model has evolved to provide stronger guarantees.

*   **v1 (Legacy):** The initial proof-of-concept used standard AES-GCM for encryption.
*   **v2 (Current & Implemented):** To provide **forward secrecy**, the current design uses an HMAC-based Key Derivation Function (HKDF, RFC 5869). Instead of using a barcode's raw value directly, a unique encryption key is derived for every single message by combining the barcode's secret value (the IKM) with a per-message salt and an incrementing counter.
*   **v3 (Implemented):** To provide **post-compromise security**, the app now implements a **Double Ratchet** algorithm, inspired by the Signal protocol. This combines a Diffie-Hellman (DH) ratchet for asynchronous key agreement with a symmetric-key ratchet for per-message keys. This ensures that even if a key is compromised, the session can "heal" and become secure again after a few message exchanges. The implementation uses Google's Tink library for the underlying `X25519` elliptic curve cryptography.

## Key Generation (v2)

The v2 model is based on **HMAC-based Key Derivation Function (HKDF)**.

*   **Initial Keying Material (IKM):** The root secret derived from the barcode.
*   **Salt:** A random value generated for each message and prepended to the ciphertext.
*   **Pseudorandom Key (PRK):** Derived from the IKM and Salt using HKDF-Extract.
*   **Message Counter:** An incrementing counter stored for each barcode.
*   **Info String:** A context-specific string (`"BCEv2|msg"`) to ensure key uniqueness.

The final message key is generated using HKDF-Expand: `MessageKey = HKDF-Expand(PRK, info="BCEv2|msg" | C, length=32)`.

## New Message Format (v2)

`BCE::v2::{options}::{barcode_identifier}::{counter}::{base64_payload}`

## Security Considerations

*   **Replay Attacks:** The client must store a `last_seen_counter` and reject messages with a counter less than or equal to it.
*   **Passphrase Security:** The database should be encrypted at rest using a library like SQLCipher.
*   **Counter Synchronization:** A mechanism to manually reset a key's counter may be needed.