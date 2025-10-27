# Performance

This document discusses performance considerations, particularly related to the background services that power Barcodencrypt.

## Architecture and System Integration

To achieve its passive, on-screen functionality, Barcodencrypt integrates deeply with the Android OS. This is accomplished primarily through two background services that have performance implications:

*   **`MessageDetectionService`**: An `AccessibilityService` that monitors the screen for encrypted messages. The service has been optimized to handle `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_SCROLLED`, and `TYPE_WINDOW_STATE_CHANGED` events, ensuring that the UI remains responsive. A debouncing mechanism prevents excessive rescans of the view hierarchy, and the service now clears highlights when the window state changes.
*   **`OverlayService`**: This service requires the `SYSTEM_ALERT_WINDOW` permission to draw on top of other applications. When the `MessageDetectionService` finds a target, the `OverlayService` is responsible for rendering the decryption interface or the password assistant icon. The performance impact of this service is minimal, as it only draws small, simple overlays when triggered and is otherwise inactive.

These services require significant user permissions (`CAMERA`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `Accessibility`) to function, and their performance is a key factor in providing a smooth user experience.