package com.hereliesaz.barcodencrypt.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.services.OverlayService
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.TryItViewModel

class TryItActivity : ComponentActivity() {

    private val viewModel: TryItViewModel by viewModels()

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val decryptedMessage = result.data?.getStringExtra("DECRYPTED_MESSAGE")
                if (decryptedMessage != null) {
                    viewModel.onMessageDecrypted(decryptedMessage)
                } else {
                    Toast.makeText(this, "Decryption failed or was cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.hereliesaz.barcodencrypt.DECRYPTION_SUCCESS") {
                val decryptedText = intent.getStringExtra("decrypted_text")
                viewModel.onMessageDecrypted(decryptedText ?: "Error: No decrypted text found.")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(receiver, IntentFilter("com.hereliesaz.barcodencrypt.DECRYPTION_SUCCESS"), RECEIVER_EXPORTED)
        setContent {
            BarcodencryptTheme {
                TryItScreen(
                    viewModel = viewModel,
                    onStartDecryption = {
                        val intent = Intent(this, ScannerActivity::class.java)
                        intent.action = Constants.ACTION_DECRYPT_MESSAGE
                        intent.putExtra(OverlayService.EXTRA_MESSAGE, viewModel.encryptedMessage.value)
                        scannerLauncher.launch(intent)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}

@Composable
fun TryItScreen(viewModel: TryItViewModel, onStartDecryption: () -> Unit) {
    val encryptedMessage by viewModel.encryptedMessage.observeAsState("")
    val decryptedMessage by viewModel.decryptedMessage.observeAsState(null)
    val stage by viewModel.stage.observeAsState(TryItViewModel.Stage.INITIAL)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (stage) {
            TryItViewModel.Stage.INITIAL -> {
                Text(
                    "This is a safe space to test decryption. The Watcher is off. When you're ready, tap the button below.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { viewModel.generateMessage() }) {
                    Text("Generate an Encrypted Message")
                }
            }
            TryItViewModel.Stage.MESSAGE_GENERATED -> {
                Text("Here is your encrypted message. Imagine you've received this in a chat app. The Watcher would normally highlight this automatically.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = encryptedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartDecryption) {
                    Text("Tap to Decrypt (Simulate Highlight Tap)")
                }
            }
            TryItViewModel.Stage.DECRYPTED -> {
                Text("Success! The message has been decrypted:", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = decryptedMessage ?: "Error displaying message.",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { viewModel.reset() }) {
                    Text("Try Again")
                }
            }
        }
    }
}