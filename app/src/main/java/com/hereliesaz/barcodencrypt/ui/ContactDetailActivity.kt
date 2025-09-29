package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModelFactory
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail

sealed class KeyCreationState {
    object Idle : KeyCreationState()
    data class AwaitingPassword(val barcodeValue: String) : KeyCreationState()
    data class AwaitingPasswordInput(val barcodeValue: String) : KeyCreationState()
    data class AwaitingSequenceScan(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePassword(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePasswordInput(val sequence: List<String>) : KeyCreationState()
    object AwaitingPasswordScan : KeyCreationState()
}

class ContactDetailActivity : ComponentActivity() {

    private lateinit var viewModel: ContactDetailViewModel
    private var contactLookupKey: String? = null
    private var contactName: String? = null
    private var keyCreationState by mutableStateOf<KeyCreationState>(KeyCreationState.Idle)
    private var refreshTrigger by mutableStateOf(false) // Added for refreshing associations

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (!barcodeValue.isNullOrBlank()) {
                    when (keyCreationState) {
                        is KeyCreationState.AwaitingPasswordScan -> {
                            viewModel.createAndInsertBarcode(barcodeValue, keyType = com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD)
                            keyCreationState = KeyCreationState.Idle
                            Toast.makeText(this, getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            keyCreationState = KeyCreationState.AwaitingPassword(barcodeValue)
                        }
                    }
                }
            }
        }

    private val scanSequenceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (!barcodeValue.isNullOrBlank()) {
                    val currentState = keyCreationState
                    if (currentState is KeyCreationState.AwaitingSequenceScan) {
                        val newSequence = currentState.sequence + barcodeValue
                        keyCreationState = KeyCreationState.AwaitingSequenceScan(newSequence)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactLookupKey = intent.getStringExtra(Constants.IntentKeys.CONTACT_LOOKUP_KEY)
        contactName = intent.getStringExtra(Constants.IntentKeys.CONTACT_NAME)

        if (contactLookupKey == null || contactName == null) {
            finish()
            return
        }

        val factory = ContactDetailViewModelFactory(application, contactLookupKey!!)
        viewModel = ViewModelProvider(this, factory)[ContactDetailViewModel::class.java]

        setContent {
            BarcodencryptTheme {
                var showAssociationDialog by remember { mutableStateOf(false) }
                val currentContext = LocalContext.current // Get context for use in lambdas

                AppScaffoldWithNavRail(
                    screenTitle = contactName!!,
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_content_description))
                        }
                    },
                    onNavigateToManageKeys = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    onNavigateToTryMe = {
                        com.hereliesaz.barcodencrypt.util.TutorialManager.startTutorial()
                        startActivity(Intent(this, ScannerActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    onNavigateToCompose = {
                        startActivity(Intent(this, ComposeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            keyCreationState = KeyCreationState.Idle
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_barcode_content_description))
                        }
                    },
                    screenContent = {
                        if (keyCreationState !is KeyCreationState.Idle) {
                            KeyCreationDialog(
                                keyCreationState = keyCreationState,
                                onKeyCreationStateChange = { keyCreationState = it },
                                viewModel = viewModel,
                                scanResultLauncher = scanResultLauncher,
                                scanSequenceLauncher = scanSequenceLauncher
                            )
                        }

                        if (showAssociationDialog) {
                            AddAssociationDialog(
                                onDismiss = { showAssociationDialog = false },
                                onConfirm = { packageName ->
                                    SettingsActivity.Companion.addAssociatedApp(currentContext, packageName) // MODIFIED
                                    refreshTrigger = !refreshTrigger
                                    showAssociationDialog = false
                                },
                                installedApps = getInstalledApps(currentContext)
                            )
                        }

                        ContactDetailScreen(
                            viewModel = viewModel,
                            onAddAssociation = { showAssociationDialog = true },
                            refreshTrigger = refreshTrigger,
                            onDeleteAssociation = { packageName ->
                                SettingsActivity.Companion.removeAssociatedApp(currentContext, packageName) // MODIFIED
                                refreshTrigger = !refreshTrigger
                            },
                            onSetupSecureChannel = {
                                val intent = Intent(this, KeyExchangeActivity::class.java)
                                intent.putExtra("contact_lookup_key", contactLookupKey)
                                startActivity(intent)
                            }
                        )
                    }
                )
            }
        }
    }

    private fun getInstalledApps(context: Context): List<String> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        return packages.map { it.packageName }.sorted()
    }
}

@Composable
fun KeyCreationDialog(
    keyCreationState: KeyCreationState,
    onKeyCreationStateChange: (KeyCreationState) -> Unit,
    viewModel: ContactDetailViewModel,
    scanResultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    scanSequenceLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) }) {
        when (val state = keyCreationState) {
            is KeyCreationState.Idle -> {
                KeyTypeSelectionDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onKeyTypeSelected = { keyType ->
                        when (keyType) {
                            com.hereliesaz.barcodencrypt.data.KeyType.SINGLE_BARCODE -> {
                                val intent = Intent(context, ScannerActivity::class.java)
                                scanResultLauncher.launch(intent)
                            }
                            com.hereliesaz.barcodencrypt.data.KeyType.BARCODE_SEQUENCE -> {
                                onKeyCreationStateChange(KeyCreationState.AwaitingSequenceScan(emptyList()))
                            }
                            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD -> {
                                val intent = Intent(context, ScannerActivity::class.java)
                                onKeyCreationStateChange(KeyCreationState.AwaitingPasswordScan)
                                scanResultLauncher.launch(intent)
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
                            val intent = Intent(context, ScannerActivity::class.java)
                            scanSequenceLauncher.launch(intent)
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
    onKeyTypeSelected: (com.hereliesaz.barcodencrypt.data.KeyType) -> Unit
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
                        .clickable { onKeyTypeSelected(com.hereliesaz.barcodencrypt.data.KeyType.SINGLE_BARCODE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Barcode Sequence (then optionally add password)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(com.hereliesaz.barcodencrypt.data.KeyType.BARCODE_SEQUENCE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Password (scanned from a QR code)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD) }
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

@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel,
    onAddAssociation: () -> Unit,
    refreshTrigger: Boolean,
    onDeleteAssociation: (String) -> Unit,
    onSetupSecureChannel: () -> Unit
) {
    val barcodes by viewModel.barcodes.observeAsState(emptyList())
    var associations by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    LaunchedEffect(key1 = refreshTrigger, key2 = Unit) {
        associations = SettingsActivity.Companion.loadAssociatedApps(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("V3 Secure Messaging", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Establish a secure channel for advanced, end-to-end encrypted messaging with post-compromise security.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSetupSecureChannel) {
            Text("Setup Secure Channel")
        }
        Spacer(modifier = Modifier.height(24.dp))


        Text(
            text = "Add keys for this contact by scanning barcodes. Then, associate messaging apps globally. Barcodencrypt will use this contact\'s keys if a global association matches and you are interacting with this contact.",
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

        Text("Associated Apps (Global)", style = MaterialTheme.typography.headlineMedium)
        Text("When you are in one of these apps, Barcodencrypt may use this contact\'s keys.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (associations.isEmpty()) {
            Text("No apps associated globally.")
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(associations.toList()) { packageName ->
                    ListItem(
                        headlineContent = { Text(packageName) },
                        trailingContent = {
                            IconButton(onClick = { onDeleteAssociation(packageName) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Association")
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onAddAssociation) {
            Text("Add App Association (Global)")
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
