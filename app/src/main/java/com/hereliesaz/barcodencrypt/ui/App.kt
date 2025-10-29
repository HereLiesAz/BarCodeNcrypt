package com.hereliesaz.barcodencrypt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.ui.composable.SuggestionOverlay
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactsViewModel
import com.hereliesaz.barcodencrypt.viewmodel.SettingsViewModel
import com.hereliesaz.barcodencrypt.viewmodel.TutorialViewModel
import kotlinx.coroutines.launch

@Composable
fun App() {
    val navController = rememberNavController()
    AzNavRail(navController = navController) {
        azRailItem(id = "contacts", text = "Contacts", route = "contacts")
        azRailItem(id = "tutorial", text = "Tutorial", route = "tutorial")
        azRailItem(id = "settings", text = "Settings", route = "settings")
    }
    NavHost(navController = navController, startDestination = "contacts") {
        composable("contacts") { ContactsScreen(navController = navController) }
        composable("tutorial") { TutorialScreen() }
        composable("settings") { SettingsScreen() }
        composable("contactDetail/{contactLookupKey}") { backStackEntry ->
            val contactLookupKey = backStackEntry.arguments?.getString("contactLookupKey")
            ContactDetailScreen(contactLookupKey = contactLookupKey)
        }
    }
}

@Composable
fun ContactsScreen(navController: NavController, viewModel: ContactsViewModel = hiltViewModel()) {
    val contacts by viewModel.contacts.collectAsState()
    LazyColumn {
        items(contacts) { contact ->
            ContactItem(contact = contact) {
                navController.navigate("contactDetail/${contact.lookupKey}")
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onClick: () -> Unit) {
    Text(text = contact.name, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun ContactDetailScreen(
    contactLookupKey: String?,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val barcodes by viewModel.barcodes.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var newBarcodeName by remember { mutableStateOf("") }

    LaunchedEffect(contactLookupKey) {
        contactLookupKey?.let { viewModel.loadBarcodes(it) }
    }

    Column {
        Text("Barcodes for contact $contactLookupKey")
        LazyColumn {
            items(barcodes) { barcode ->
                BarcodeItem(barcode = barcode) {
                    viewModel.removeBarcode(barcode)
                }
            }
        }
        TextField(
            value = newBarcodeName,
            onValueChange = { newBarcodeName = it },
            label = { Text("Barcode Name") }
        )
        Button(onClick = {
            coroutineScope.launch {
                ScannerManager.requestScan { scannedBarcode ->
                    if (scannedBarcode != null && contactLookupKey != null) {
                        val newBarcode = Barcode(
                            contactLookupKey = contactLookupKey,
                            name = newBarcodeName,
                            value = scannedBarcode,
                            keyType = KeyType.SINGLE_BARCODE
                        )
                        viewModel.addBarcode(newBarcode)
                        newBarcodeName = ""
                    }
                }
            }
        }) {
            Text("Add Barcode")
        }
    }
}

@Composable
fun BarcodeItem(barcode: Barcode, onRemove: () -> Unit) {
    Column {
        Text(text = barcode.name)
        Button(onClick = onRemove) {
            Text("Remove")
        }
    }
}


@Composable
fun TutorialScreen(viewModel: TutorialViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { message ->
                    Text(
                        text = message,
                        modifier = Modifier.clickable {
                            selectedMessage = message
                        }
                    )
                }
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message") }
            )
            Button(onClick = {
                viewModel.sendMessage(text)
                text = ""
            }) {
                Text("Send")
            }
        }

        selectedMessage?.let { message ->
            SuggestionOverlay(
                message = message,
                onDecryptClick = {
                    viewModel.decryptMessage(message)
                    selectedMessage = null
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.updateServiceStatus(context)
    }

    Column {
        Text("Settings")
        Switch(
            checked = isServiceEnabled,
            onCheckedChange = {
                viewModel.openAccessibilitySettings(context)
            }
        )
        Text("Enable On-Screen Detection")
        Button(onClick = { viewModel.openOverlaySettings(context) }) {
            Text("Grant Overlay Permission")
        }
    }
}
