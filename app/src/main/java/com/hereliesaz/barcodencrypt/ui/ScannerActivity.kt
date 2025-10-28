package com.hereliesaz.barcodencrypt.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.services.OverlayService
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.*
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels { CameraViewModelFactory(application) }
    private val scannerViewModel: ScannerViewModel by viewModels { ScannerViewModelFactory(application) }
    private lateinit var previewView: PreviewView
    private var barcodeFound = AtomicBoolean(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setupCamera()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
                finish()
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            BarcodencryptTheme {
                val decryptionResult by scannerViewModel.decryptionResult.observeAsState()

                if (decryptionResult != null) {
                    DecryptionResultDialog(result = decryptionResult!!) {
                        // Reset result and finish activity
                        scannerViewModel.resetDecryptionResult()
                        finish()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(id = R.string.scan_key)) }
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        ScannerScreen(previewView)
                    }
                }
            }
        }
    }

    private fun setupCamera() {
        cameraViewModel.cameraProviderLiveData.observe(this) { cameraProvider ->
            cameraViewModel.bindUseCases(cameraProvider, this)
            cameraViewModel.previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
            cameraViewModel.imageAnalysisUseCase.setAnalyzer(
                cameraViewModel.cameraExecutor,
                BarcodeAnalyzer()
            )
        }
    }

    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let {
                                if (barcodeFound.compareAndSet(false, true)) {
                                    handleBarcode(it)
                                }
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnFailureListener { e -> Log.e("BarcodeAnalyzer", "Analysis failed.", e) }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }
    }

    private fun handleBarcode(barcodeValue: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (intent.action == Constants.ACTION_DECRYPT_MESSAGE) {
                val encryptedMessage = intent.getStringExtra(OverlayService.EXTRA_MESSAGE)
                if (encryptedMessage != null) {
                    scannerViewModel.decryptMessage(barcodeValue, encryptedMessage)
                } else {
                    Toast.makeText(this, "Error: No message to decrypt.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                val resultIntent = Intent().apply {
                    putExtra(Constants.IntentKeys.SCAN_RESULT, barcodeValue)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}

@Composable
fun DecryptionResultDialog(result: DecryptionResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            when (result) {
                is DecryptionResult.Success -> Text("Decryption Successful")
                is DecryptionResult.Error -> Text("Decryption Failed")
            }
        },
        text = {
            when (result) {
                is DecryptionResult.Success -> Text(result.plaintext)
                is DecryptionResult.Error -> Text(result.message)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun ScannerScreen(previewView: PreviewView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = stringResource(id = R.string.point_camera_at_a_barcode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}