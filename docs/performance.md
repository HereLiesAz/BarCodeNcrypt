# Performance

This document discusses performance considerations, particularly related to the background services that power Barcodencrypt.

## Architecture and System Integration

To achieve its passive, on-screen functionality, Barcodencrypt integrates deeply with the Android OS. This is accomplished primarily through two background services that have performance implications:

*   **`MessageDetectionService`**: An `AccessibilityService` that monitors the screen's content in real-time. It is responsible for detecting specially formatted encrypted text strings and identifying when the user has focused on a password input field. While powerful, accessibility services can have a performance impact on the system, as they intercept and process screen content events. The service is optimized to scan for specific patterns (`BCE::v...`) to minimize its processing footprint.
*   **`OverlayService`**: This service requires the `SYSTEM_ALERT_WINDOW` permission to draw on top of other applications. When the `MessageDetectionService` finds a target, the `OverlayService` is responsible for rendering the decryption interface or the password assistant icon. The performance impact of this service is minimal, as it only draws small, simple overlays when triggered and is otherwise inactive.

These services require significant user permissions (`CAMERA`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `Accessibility`) to function, and their performance is a key factor in providing a smooth user experience.