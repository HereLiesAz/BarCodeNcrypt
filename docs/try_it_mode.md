# Try It Mode

The "Try It" mode is a feature that allows users to test the decryption functionality of the app in a safe and controlled environment. This provides a way for users to verify that the app's core functionality is working correctly.

## How it Works

The "Try It" mode is implemented as a composable on the main screen. When the user taps the "Try It Out" button, a new UI card appears, guiding the user through the following steps:

1.  **Enter password to generate a message:** The user is prompted to enter their password to generate a sample encrypted message.
2.  **Decrypt message:** The app displays the encrypted message and a "Decrypt" button. When the user taps the button, they are prompted to enter their password again.
3.  **View result:** The app attempts to decrypt the message using the user's password and shows a success or error message.

## Implementation Details

The "Try It" mode is implemented using a state machine in the `MainViewModel`. The state machine is managed by a `TryItState` sealed class, which defines the following states:

*   `Idle`: The initial state, where the "Try It" mode is not active.
*   `AwaitingPassword`: The state where the app is waiting for the user to enter their password to generate a message.
*   `MessageGenerated`: The state where a sample encrypted message has been generated.
*   `AwaitingDecryptionPassword`: The state where the app is waiting for the user to enter their password to decrypt the message.
*   `Decrypted`: The state where the message has been successfully decrypted.
*   `Error`: The state where an error has occurred.
