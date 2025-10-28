package com.hereliesaz.barcodencrypt.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.viewmodel.ComposeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    navController: NavController,
    viewModel: ComposeViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val barcodes by viewModel.barcodesForSelectedContact.collectAsState()
    var selectedContactInfo by remember { mutableStateOf<Pair<String, String>?>(null) }


    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == -1) { // RESULT_OK
            result.data?.data?.let { contactUri ->
                val projection = arrayOf(
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                )
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                        selectedContactInfo = displayName to lookupKey
                        viewModel.selectContact(lookupKey)
                    }
                }
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            )
        } else {
            Toast.makeText(context, context.getString(R.string.contacts_permission_to_select_recipient), Toast.LENGTH_LONG).show()
        }
    }

    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var message by remember { mutableStateOf("") }
    var encryptedText by remember { mutableStateOf("") }
    var isSingleUse by remember { mutableStateOf(false) }
    var isTimed by remember { mutableStateOf(false) }
    var ttlHours by remember { mutableStateOf("1.0") }
    var ttlStartsOnOpen by remember { mutableStateOf(false) }
    var showKeySelectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(barcodes) {
        selectedBarcode = barcodes.firstOrNull()
    }

    val noRecipientSelectedText = stringResource(id = R.string.no_recipient_selected)
    val noKeySelectedText = stringResource(id = R.string.no_key_selected)
    val recipientLabelText = stringResource(id = R.string.recipient)
    val keyLabelText = stringResource(id = R.string.key)
    val selectRecipientAndKeyButtonText = stringResource(id = R.string.select_recipient_and_key)
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                showPasswordDialog = false
                val barcode = selectedBarcode
                if (message.isNotBlank() && barcode != null) {
                    coroutineScope.launch {
                        val options = mutableListOf<String>()
                        if (isSingleUse) options.add(com.hereliesaz.barcodencrypt.crypto.EncryptionManager.OPTION_SINGLE_USE)
                        if (isTimed) {
                            options.add("ttl_hours=${ttlHours.toDoubleOrNull() ?: 1.0}")
                            if(ttlStartsOnOpen) options.add("ttl_on_open=true")
                        }

                        val result = viewModel.encryptMessage(
                            plaintext = message,
                            barcode = barcode,
                            options = options,
                            password = password
                        )
                        if (result != null) {
                            encryptedText = result
                        } else {
                            Toast.makeText(context, context.getString(R.string.encryption_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(
            text = "Select a recipient, type your message, and then encrypt it.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text("Select a contact from your address book.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    contactPickerLauncher.launch(
                        Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    )
                } else {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectRecipientAndKeyButtonText)
        }
        Spacer(Modifier.height(16.dp))

        RecipientInfoRow(
            label = recipientLabelText,
            value = selectedContactInfo?.first ?: noRecipientSelectedText
        )
        Spacer(Modifier.height(8.dp))
        Text("Select the key to use for encryption.", style = MaterialTheme.typography.bodySmall)
        RecipientInfoRow(
            label = keyLabelText,
            value = selectedBarcode?.name ?: noKeySelectedText,
            onClick = {
                if (barcodes.isNotEmpty()) {
                    showKeySelectionDialog = true
                } else if (selectedContactInfo != null) {
                    Toast.makeText(context, context.getString(R.string.contact_has_no_keys_add_one), Toast.LENGTH_LONG).show()
                } else {
                     Toast.makeText(context, context.getString(R.string.no_recipient_selected), Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Text("Type your message here.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringResource(id = R.string.message)) },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { isSingleUse = !isSingleUse }
        ) {
            Checkbox(checked = isSingleUse, onCheckedChange = { isSingleUse = it })
            Text("Single-use message: this message can only be decrypted once.", style = MaterialTheme.typography.bodySmall)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = isTimed, onCheckedChange = { isTimed = it })
            Text("Timed message: this message will disappear after a set time.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = ttlHours,
                onValueChange = { ttlHours = it.filter { char -> char.isDigit() || char == '.' } },
                label = { Text("Duration (hours)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = isTimed,
                modifier = Modifier.width(120.dp)
            )
        }

        if (isTimed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { ttlStartsOnOpen = !ttlStartsOnOpen }
            ) {
                Checkbox(checked = ttlStartsOnOpen, onCheckedChange = { ttlStartsOnOpen = it })
                Text("Start timer on message open", style = MaterialTheme.typography.bodySmall)
            }
        }


        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val barcode = selectedBarcode
                if (message.isNotBlank() && barcode != null) {
                    if (barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE) {
                        showPasswordDialog = true
                    } else {
                        coroutineScope.launch {
                            val options = mutableListOf<String>()
                            if (isSingleUse) options.add(com.hereliesaz.barcodencrypt.crypto.EncryptionManager.OPTION_SINGLE_USE)
                            if (isTimed) {
                                options.add("ttl_hours=${ttlHours.toDoubleOrNull() ?: 1.0}")
                                if (ttlStartsOnOpen) options.add("ttl_on_open=true")
                            }

                            val result = viewModel.encryptMessage(
                                plaintext = message,
                                barcode = barcode,
                                options = options
                            )
                            if (result != null) {
                                encryptedText = result
                            } else {
                                Toast.makeText(context, context.getString(R.string.encryption_failed), Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = message.isNotBlank() && selectedBarcode != null
        ) {
            Text(stringResource(id = R.string.encrypt))
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = encryptedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(id = R.string.encrypted_message_will_appear_here)) },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("encrypted_message", encryptedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.message_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            },
            enabled = encryptedText.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.copy))
        }
    }

    if (showKeySelectionDialog) {
        KeySelectionDialog(
            barcodes = barcodes,
            onDismiss = { showKeySelectionDialog = false },
            onKeySelected = {
                selectedBarcode = it
                showKeySelectionDialog = false
            }
        )
    }
}

@Composable
fun RecipientInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}

@Composable
fun KeySelectionDialog(
    barcodes: List<Barcode>,
    onDismiss: () -> Unit,
    onKeySelected: (Barcode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_key)) },
        text = {
            Column {
                barcodes.forEach { barcode ->
                    Text(
                        text = barcode.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onKeySelected(barcode) }
                            .padding(vertical = 12.dp)
                    )
                }
                if (barcodes.isEmpty()) {
                    Text(stringResource(id = R.string.no_barcodes_for_contact))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}