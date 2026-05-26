package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.Contact

/**
 * In-place encrypt overlay. Stateless about its data source: the hosting [OverlayService]
 * supplies the contact/barcode lists and the encrypt action, so this composable does not
 * depend on `hiltViewModel()` (which is unavailable from a bare Service window).
 */
@Composable
fun EncryptionOverlay(
    initialText: String,
    contacts: List<Contact>,
    barcodes: List<Barcode>,
    onContactSelected: (Contact) -> Unit,
    onEncryptClicked: (text: String, barcode: Barcode, ttlMs: Long?, openCount: Int?) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var ttl by remember { mutableStateOf("") }
    var openCount by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.width(280.dp).padding(12.dp)) {
            when {
                selectedContact == null -> {
                    Text("Select a contact", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(contacts, key = { it.lookupKey }) { contact ->
                            Text(
                                text = contact.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedContact = contact
                                        onContactSelected(contact)
                                    }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                selectedBarcode == null -> {
                    Text("Select a key", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(barcodes, key = { it.id }) { barcode ->
                            Text(
                                text = barcode.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBarcode = barcode }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                else -> {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = ttl,
                        onValueChange = { ttl = it.filter(Char::isDigit) },
                        label = { Text("Time-to-live (ms)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = openCount,
                        onValueChange = { openCount = it.filter(Char::isDigit) },
                        label = { Text("Open count") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val barcode = selectedBarcode ?: return@Button
                            onEncryptClicked(text, barcode, ttl.toLongOrNull(), openCount.toIntOrNull())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Encrypt")
                    }
                }
            }
        }
    }
}
