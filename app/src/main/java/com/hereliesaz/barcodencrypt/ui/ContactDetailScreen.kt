package com.hereliesaz.barcodencrypt.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.util.SettingsManager
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModelFactory
import kotlinx.coroutines.launch

sealed class KeyCreationState {
    object Idle : KeyCreationState()
    data class AwaitingPassword(val barcodeValue: String) : KeyCreationState()
    data class AwaitingPasswordInput(val barcodeValue: String) : KeyCreationState()
    data class AwaitingSequenceScan(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePassword(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePasswordInput(val sequence: List<String>) : KeyCreationState()
    object AwaitingPasswordScan : KeyCreationState()
}


@Composable
fun KeyCreationDialog(
    keyCreationState: KeyCreationState,
    onKeyCreationStateChange: (KeyCreationState) -> Unit,
    viewModel: ContactDetailViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) }) {
        when (val state = keyCreationState) {
            is KeyCreationState.Idle -> {
                KeyTypeSelectionDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onKeyTypeSelected = { keyType ->
                        when (keyType) {
                            KeyType.SINGLE_BARCODE -> {
                                coroutineScope.launch {
                                    ScannerManager.requestScan { barcodeValue ->
                                        if (!barcodeValue.isNullOrBlank()) {
                                            onKeyCreationStateChange(KeyCreationState.AwaitingPassword(barcodeValue))
                                        }
                                    }
                                }
                            }
                            KeyType.BARCODE_SEQUENCE -> {
                                onKeyCreationStateChange(KeyCreationState.AwaitingSequenceScan(emptyList()))
                            }
                            KeyType.PASSWORD -> {
                                coroutineScope.launch {
                                    ScannerManager.requestScan { barcodeValue ->
                                        if (!barcodeValue.isNullOrBlank()) {
                                            viewModel.createAndInsertBarcode(barcodeValue, keyType = KeyType.PASSWORD)
                                            onKeyCreationStateChange(KeyCreationState.Idle)
                                            Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            else -> {
                                onKeyCreationStateChange(KeyCreationState.Idle)
                            }
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingPasswordInput -> {
                PasswordDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onConfirm = { password ->
                        viewModel.createAndInsertBarcode(state.barcodeValue, password)
                        onKeyCreationStateChange(KeyCreationState.Idle)
                        Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            is KeyCreationState.AwaitingPassword -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Password Protection") },
                    text = { Text("Do you want to protect this key with a password?") },
                    confirmButton = {
                        Button(onClick = {
                            onKeyCreationStateChange(KeyCreationState.AwaitingPasswordInput(state.barcodeValue))
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.createAndInsertBarcode(state.barcodeValue)
                            onKeyCreationStateChange(KeyCreationState.Idle)
                            Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                        }) {
                            Text("No")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequenceScan -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Scan Barcode Sequence") },
                    text = { Text("You have scanned ${state.sequence.size} barcodes. Do you want to scan another one?") },
                    confirmButton = {
                        Button(onClick = {
                            coroutineScope.launch {
                                ScannerManager.requestScan { barcodeValue ->
                                    if (!barcodeValue.isNullOrBlank()) {
                                        val newSequence = state.sequence + barcodeValue
                                        onKeyCreationStateChange(KeyCreationState.AwaitingSequenceScan(newSequence))
                                    }
                                }
                            }
                        }) {
                            Text("Scan Next")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            if (state.sequence.isNotEmpty()) {
                                onKeyCreationStateChange(KeyCreationState.AwaitingSequencePassword(state.sequence))
                            } else {
                                onKeyCreationStateChange(KeyCreationState.Idle)
                                Toast.makeText(context, "No barcodes scanned for sequence.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Finish")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequencePassword -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Password Protection") },
                    text = { Text("Do you want to protect this key sequence with a password?") },
                    confirmButton = {
                        Button(onClick = {
                            onKeyCreationStateChange(KeyCreationState.AwaitingSequencePasswordInput(state.sequence))
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.createAndInsertBarcodeSequence(state.sequence)
                            onKeyCreationStateChange(KeyCreationState.Idle)
                            Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                        }) {
                            Text("No")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequencePasswordInput -> {
                PasswordDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onConfirm = { password ->
                        viewModel.createAndInsertBarcodeSequence(state.sequence, password)
                        onKeyCreationStateChange(KeyCreationState.Idle)
                        Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            is KeyCreationState.AwaitingPasswordScan -> {
                 Text("Awaiting password scan via QR code scanner...")
            }
        }
    }
}

@Composable
fun AddAssociationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    installedApps: List<String>
) {
    var selectedApp by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Associate App") },
        text = {
            if (installedApps.isEmpty()) {
                Text("No installable apps found to associate.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedApp = app }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedApp == app,
                                onClick = { selectedApp = app }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(app)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedApp.isNotEmpty()) onConfirm(selectedApp) },
                enabled = selectedApp.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun KeyTypeSelectionDialog(
    onDismiss: () -> Unit,
    onKeyTypeSelected: (KeyType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Key Type") },
        text = {
            Column {
                Text(
                    text = "Single Barcode (then optionally add password)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.SINGLE_BARCODE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Barcode Sequence (then optionally add password)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.BARCODE_SEQUENCE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Password (scanned from a QR code)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.PASSWORD) }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    navController: NavController,
    contactLookupKey: String?,
    contactName: String?
) {
    val context = LocalContext.current
    val viewModel: ContactDetailViewModel = viewModel(
        factory = ContactDetailViewModelFactory(context.applicationContext as Application, contactLookupKey!!)
    )
    val barcodes by viewModel.barcodes.observeAsState(initial = emptyList<Barcode>()) // Explicitly set the type here
    var associations by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshTrigger by remember { mutableStateOf(false) }
    var keyCreationState by remember { mutableStateOf<KeyCreationState>(KeyCreationState.Idle) }
    var showAssociationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = refreshTrigger, key2 = Unit) {
        associations = SettingsManager.loadAssociatedApps(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactName ?: "") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_content_description))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                keyCreationState = KeyCreationState.AwaitingPasswordScan
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_barcode_content_description))
            }
        }
    ) { paddingValues ->

        if (keyCreationState !is KeyCreationState.Idle) {
            KeyCreationDialog(
                keyCreationState = keyCreationState,
                onKeyCreationStateChange = { keyCreationState = it },
                viewModel = viewModel
            )
        }

        if (showAssociationDialog) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            val installedApps = packages.map { it.packageName }.sorted()
            AddAssociationDialog(
                onDismiss = { showAssociationDialog = false },
                onConfirm = { packageName ->
                    SettingsManager.addAssociatedApp(context, packageName)
                    refreshTrigger = !refreshTrigger
                    showAssociationDialog = false
                },
                installedApps = installedApps
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                text = "Add keys for this contact by scanning barcodes. Then, associate messaging apps globally. Barcodencrypt will use this contact's keys if a global association matches and you are interacting with this contact.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text("Keys", style = MaterialTheme.typography.headlineMedium)
            Text("These are the keys you can use to send encrypted messages to this contact.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (barcodes.isEmpty()) {
                Text(stringResource(id = R.string.no_barcodes_assigned))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(barcodes) { barcode ->
                        BarcodeItem(barcode = barcode)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Associated Apps (Global)", style = MaterialTheme.typography.headlineMedium) // Clarified title
            Text("When you are in one of these apps, Barcodencrypt may use this contact's keys.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (associations.isEmpty()) {
                Text("No apps associated globally.") // MODIFIED text
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(associations.toList()) { packageName -> // Convert Set to List for items
                        ListItem(
                            headlineContent = { Text(packageName) },
                            trailingContent = {
                                IconButton(onClick = {
                                    SettingsManager.removeAssociatedApp(context, packageName)
                                    refreshTrigger = !refreshTrigger
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Association")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showAssociationDialog = true }) {
                Text("Add App Association (Global)")
            }
        }
    }
}

@Composable
fun BarcodeItem(
    barcode: Barcode
) {
    ListItem(
        headlineContent = { Text(barcode.name) },
        supportingContent = {
            Column {
                Text(
                    "Counter: ${barcode.counter}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Type: ${barcode.keyType}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
