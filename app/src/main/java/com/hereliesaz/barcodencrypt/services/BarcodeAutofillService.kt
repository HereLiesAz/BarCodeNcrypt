package com.hereliesaz.barcodencrypt.services

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class BarcodeAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        // The scan-to-fill dataset is not implemented yet. The Autofill framework requires
        // the callback to be invoked exactly once, otherwise the request hangs until it
        // times out — so always answer, with `null` meaning "no suggestions".
        val structure: AssistStructure? = request.fillContexts.lastOrNull()?.structure
        if (structure == null || structure.windowNodeCount == 0) {
            callback.onSuccess(null)
            return
        }

        // (Detection retained for when the dataset flow lands; harmless and bounded.)
        var hasPasswordField = false
        fun visit(node: AssistStructure.ViewNode?) {
            if (node == null || hasPasswordField) return
            val looksLikePassword = node.isFocused &&
                (node.className?.contains("EditText", true) == true ||
                    node.hint?.contains("password", true) == true)
            if (looksLikePassword && node.autofillId != null) {
                hasPasswordField = true
                return
            }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        visit(structure.getWindowNodeAt(0).rootViewNode)

        callback.onSuccess(null)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Not implemented.
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
