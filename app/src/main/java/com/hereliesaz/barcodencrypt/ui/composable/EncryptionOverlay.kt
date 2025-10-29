package com.hereliesaz.barcodencrypt.ui.composable

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.viewmodel.EncryptionOverlayViewModel

@Composable
fun EncryptionOverlay(
    viewModel: EncryptionOverlayViewModel = hiltViewModel(),
    node: AccessibilityNodeInfo,
    onEncrypt: (String) -> Unit
) {
    var text by remember { mutableStateOf(node.text?.toString() ?: "") }
    val contacts by viewModel.contacts.collectAsState()
    val barcodes by viewModel.barcodes.collectAsState()
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }

    Column {
        if (selectedContact == null) {
            LazyColumn {
                items(contacts) { contact ->
                    Text(
                        text = contact.name,
                        modifier = Modifier.clickable {
                            selectedContact = contact
                            viewModel.getBarcodesForContact(contact)
                        }
                    )
                }
            }
        } else if (selectedBarcode == null) {
            LazyColumn {
                items(barcodes) { barcode ->
                    Text(
                        text = barcode.name,
                        modifier = Modifier.clickable { selectedBarcode = barcode }
                    )
                }
            }
        } else {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message") }
            )
            Button(onClick = {
                viewModel.encrypt(text, selectedBarcode!!) { encryptedText ->
                    if (encryptedText != null) {
                        val arguments = Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, encryptedText)
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        onEncrypt(encryptedText)
                    }
                }
            }) {
                Text("Encrypt")
            }
        }
    }
}
