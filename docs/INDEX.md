# BarcodeNcrypt Documentation

Welcome to the official documentation for BarcodeNcrypt, an Android application for secure, barcode-based messaging. This documentation provides a comprehensive overview of the app's architecture, features, and usage.

## Core Concepts

BarcodeNcrypt allows you to send encrypted messages where the decryption key is a physical barcode. The app is built on the following core principles:

*   **Barcode-Based Decryption:** Every message is locked with a specific barcode. To unlock and read the message, the recipient must scan the correct barcode.
*   **On-Screen Detection:** The app features a background service that watches the screen for encrypted messages. When a message is detected, it can be decrypted in-place by tapping on it and scanning the corresponding barcode.
*   **Sender-Controlled Security:** The sender of a message can define security parameters, such as how long a message is visible and how many times it can be decrypted.
*   **Enhanced Security:** For increased security, users can combine a barcode with a password, creating a two-factor authentication system for their messages.
*   **Tutorial Mode:** A built-in tutorial mode provides a mock chat interface to guide users through the process of sending and receiving encrypted messages, while also testing the app's core functionality.

## Table of Contents

*   **Getting Started**
    *   [File Descriptions](./FILE_DESCRIPTIONS.md): An overview of the project's file structure.
    *   [UI/UX](./UI_UX.md): A guide to the app's user interface and experience, including the on-screen detection and decryption process.
*   **Security**
    *   [Authentication](./auth.md): An in-depth look at the app's security model, including barcode and password-based keys.
    *   [Data Layer](./data_layer.md): A detailed description of the app's cryptographic model, data storage, and key generation.
*   **Development**
    *   [Screens](./screens.md): An overview of the app's screens and UI components.
    *   [Workflow](./workflow.md): A step-by-step explanation of the encryption and decryption process.
    *   [Task Flow](./task_flow.md): A guide to the user flows for key management, encryption, and decryption.
    *   [Navigation](./misc.md): Information on the app's navigation, which is handled by the AzNavRail component.
*   **Community**
    *   [Code of Conduct](./conduct.md): Guidelines for contributing to the project.
    *   [Faux Pas](./fauxpas.md): A list of common mistakes and anti-patterns to avoid.
*   **Additional Information**
    *   [Performance](./performance.md): A discussion of the app's performance considerations, particularly related to the background services.
    *   [Testing](./testing.md): An overview of the app's testing strategy.
    *   [Miscellaneous](./misc.md): Any other information that doesn't fit into the other categories.
