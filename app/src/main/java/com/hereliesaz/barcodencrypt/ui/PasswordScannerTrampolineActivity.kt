package com.hereliesaz.barcodencrypt.ui

import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager

class PasswordScannerTrampolineActivity : ComponentActivity() {
    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)?.let {
                    PasswordPasteManager.pastePassword(it, this)
                }
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannerLauncher.launch(Intent(this, ScannerActivity::class.java).apply {
            action = Constants.ACTION_SCAN_RESULT
        })
    }
}