package com.hereliesaz.barcodencrypt.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.hereliesaz.barcodencrypt.R
import android.app.assist.AssistStructure
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import dagger.hilt.android.AndroidEntryPoint

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class BarcodeAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return
        val passwordNodes = mutableListOf<AutofillId>()

        fun findPasswordFields(node: AssistStructure.ViewNode) {
            if (node.isFocused && (node.className?.contains("EditText", true) == true || node.hint?.contains("password", true) == true)) {
                node.autofillId?.let { passwordNodes.add(it) }
            }
            for (i in 0 until node.childCount) {
                findPasswordFields(node.getChildAt(i))
            }
        }

        findPasswordFields(structure.getWindowNodeAt(0).rootViewNode)

        if (passwordNodes.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // val presentation = RemoteViews(context.packageName, R.layout.autofill_suggestion)
        // presentation.setTextViewText(R.id.suggestion_text, "Scan with Barcodencrypt")

        // val intent = Intent(this, AutofillScannerTrampolineActivity::class.java).apply {
        //     putExtra("autofill_id", passwordNodes.first())
        // }
        // val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // val dataset = Dataset.Builder(presentation)
        //     .setValue(passwordNodes.first(), null, pendingIntent)
        //     .build()

        // val response = FillResponse.Builder()
        //     .addDataset(dataset)
        //     .build()

        // callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Not implemented
    }

    override fun onConnected() {
        super.onConnected()
        Log.d("BarcodeAutofillService", "Autofill service connected.")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.d("BarcodeAutofillService", "Autofill service disconnected.")
    }
}