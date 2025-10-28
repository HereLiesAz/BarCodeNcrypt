package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import com.hereliesaz.barcodencrypt.viewmodel.TryItState
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.ui.theme.EnabledGreen

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onManageContactKeys: () -> Unit,
    onNavigateToCompose: () -> Unit
) {
    val context = LocalContext.current
    val serviceEnabled by viewModel.serviceStatus.collectAsState()
    val notificationPermissionGranted by viewModel.notificationPermissionStatus.collectAsState()
    val contactsPermissionGranted by viewModel.contactsPermissionStatus.collectAsState()
    val overlayPermissionGranted by viewModel.overlayPermissionStatus.collectAsState()
    val tryItState by viewModel.tryItState.collectAsState()
    var showDialog by remember { mutableStateOf(!serviceEnabled) }

    if (tryItState !is TryItState.Idle) {
        TryItCard(
            state = tryItState,
            onAction = { action ->
                when (action) {
                    is TryItAction.GenerateMessage -> viewModel.generateTryItMessage(action.password)
                    is TryItAction.DecryptMessage -> {
                        if (action.password.isNotEmpty()) {
                            viewModel.decryptTryItMessage(action.message, action.password)
                        } else {
                            viewModel.awaitDecryptionPassword(action.message)
                        }
                    }
                    is TryItAction.Reset -> viewModel.resetTryItMode()
                }
            }
        )
    } else if (showDialog && !serviceEnabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enable Service") },
            text = { Text("To detect encrypted messages, you need to enable the Barcodencrypt accessibility service. This service reads the text on your screen to find messages to decrypt.") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    showDialog = false
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Barcodencrypt. Enable the services below to start.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ServiceStatusCard(serviceEnabled = serviceEnabled)
        if (serviceEnabled) {
            OverlayPermissionCard(
                isGranted = overlayPermissionGranted,
                onRequest = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )
            NotificationPermissionCard(
                isGranted = notificationPermissionGranted,
                onRequest = onRequestNotificationPermission
            )
        }
        Spacer(Modifier.height(16.dp))

        ContactsPermissionCard(
            isGranted = contactsPermissionGranted,
            onRequest = onManageContactKeys
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "Create a new encrypted message from scratch.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onNavigateToCompose) {
            Text("Compose Message")
        }

        Spacer(Modifier.height(16.dp))

        Text("Manage the keys for your contacts.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onManageContactKeys) {
            Text("Manage Contact Keys")
        }

        Spacer(Modifier.height(16.dp))

        Text("Try out the decryption process in a safe environment.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { viewModel.startTryItMode() }) {
            Text("Try It Out")
        }
    }
}


@Composable
fun ServiceStatusCard(serviceEnabled: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (serviceEnabled) EnabledGreen else DisabledRed)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (serviceEnabled) "Watcher Service: Enabled" else "Watcher Service: Disabled",
                modifier = Modifier.weight(1f)
            )
            if (!serviceEnabled) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Enable Service")
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Overlay Permission Required",
            description = "Permission is needed to highlight messages on screen.",
            onRequest = onRequest
        )
    }
}

@Composable
fun NotificationPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Notification Permission Required",
            description = "Permission is needed to show a notification when an encrypted message is found.",
            onRequest = onRequest
        )
    }
}

@Composable
fun ContactsPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Contacts Permission Required",
            description = "Permission is needed to assign keys to your contacts.",
            onRequest = onRequest
        )
    }
}

@Composable
private fun PermissionRequestRow(title: String, description: String, onRequest: () -> Unit) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRequest) { Text("Grant") }
    }
}

sealed class TryItAction {
    data class GenerateMessage(val password: String) : TryItAction()
    data class DecryptMessage(val message: String, val password: String) : TryItAction()
    object Reset : TryItAction()
}

@Composable
fun TryItCard(state: TryItState, onAction: (TryItAction) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is TryItState.Idle -> {
                    // This state is handled by the visibility of the card
                }
                is TryItState.AwaitingPassword -> {
                    var password by remember { mutableStateOf("") }
                    Text("Please enter your password to generate a test message.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") }
                    )
                    Button(onClick = { onAction(TryItAction.GenerateMessage(password)) }) {
                        Text("Generate")
                    }
                }
                is TryItState.MessageGenerated -> {
                    Text("Here is your encrypted message:", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.encryptedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onAction(TryItAction.DecryptMessage(state.encryptedMessage, "")) }) {
                        Text("Decrypt")
                    }
                }
                is TryItState.AwaitingDecryptionPassword -> {
                    var password by remember { mutableStateOf("") }
                    Text("Please enter your password to decrypt the message.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") }
                    )
                    Button(onClick = { onAction(TryItAction.DecryptMessage(state.encryptedMessage, password)) }) {
                        Text("Decrypt")
                    }
                }
                is TryItState.Decrypted -> {
                    Text("Success! The message has been decrypted:", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.decryptedMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { onAction(TryItAction.Reset) }) {
                        Text("Try Again")
                    }
                }
                is TryItState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { onAction(TryItAction.Reset) }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}