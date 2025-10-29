# UI/UX

This document covers the user interaction and experience aspects of BarcodeNcrypt.

## On-Screen Interaction

BarcodeNcrypt uses an overlay system to interact with encrypted messages directly on the screen. This system has two primary functions:

*   **Decryption Overlay:** When an encrypted message is detected, a semi-transparent yellow overlay appears over it. Tapping this overlay opens the barcode scanner. If the correct barcode is scanned, the overlay turns green and reveals the decrypted message. If the incorrect barcode is scanned, the overlay will display gibberish, indicating a failed decryption attempt.
*   **Password Assistant:** A small icon appears next to password fields, allowing the user to scan a barcode to fill in the password. This feature is a convenience and does not use the app's encryption model.

## Navigation

The app's navigation is handled by the AzNavRail component, a customizable navigation rail that provides a consistent and intuitive user experience. The navigation rail allows users to quickly access the app's main features, such as managing contacts, composing messages, and accessing the interactive tutorial.

## Interactive Tutorial

The interactive tutorial is a key feature of the app, providing a mock chat interface to guide users through the process of sending and receiving encrypted messages. The tutorial also serves as a testing tool, verifying that the on-screen detection and decryption functionality is working correctly.
