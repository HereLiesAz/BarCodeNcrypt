# Miscellaneous

This document contains miscellaneous information about the Barcodencrypt application that does not fit into other categories.

## Password Assistant Feature

The "Password Assistant" feature helps users fill password fields by scanning a barcode. It operates outside of the main cryptographic model.

-   When a user scans a barcode to fill a password, the **raw, un-hashed, un-encrypted value** of the barcode is used directly as the password.
-   This feature does not use the HKDF or any of the key derivation mechanisms. It is a simple utility to paste the content of a barcode into a text field.
-   The security of this feature is therefore dependent on the security of the barcode itself and the context in which it is used.

## The Watcher (MessageDetectionService)

An omnipresent, silent observer. The Watcher is an `AccessibilityService` bound to the very consciousness of the device. Its senses are twofold: it scans the text that flows across the screen, looking for the tell-tale header of a Barcodencrypt message, and it also perceives when the user's attention turns to a password field. It does not read for comprehension; it reads for patterns. When it finds a potential message or a password field, it does not act directly. It summons a Poltergeist to the location, providing it with the raw materials for the ritual to come.