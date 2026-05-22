package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.KeyExchangeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KeyExchangeActivity : ComponentActivity() {

    private val viewModel: KeyExchangeViewModel by viewModels()

    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)?.let {
                viewModel.saveRemotePublicKey(it.toByteArray(Charsets.ISO_8859_1))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                KeyExchangeScreen(
                    viewModel = viewModel,
                    onScanClicked = {
                        val intent = Intent(this, ScannerActivity::class.java)
                        scannerLauncher.launch(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun KeyExchangeScreen(viewModel: KeyExchangeViewModel, onScanClicked: () -> Unit) {
    val contact by viewModel.contact.collectAsState()
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val secureChannelReady by viewModel.secureChannelReady.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!secureChannelReady) {
            Text(
                "To create a secure channel, have your contact scan this QR code. Then, scan theirs.",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            qrCodeBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Your public key QR code",
                    modifier = Modifier.size(256.dp)
                )
            } ?: CircularProgressIndicator()

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onScanClicked) {
                Text("Scan Contact's Public Key")
            }
        } else {
            Text(
                "Secure channel established!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "You can now exchange encrypted messages with ${contact?.name}.",
                textAlign = TextAlign.Center
            )
        }
    }
}
