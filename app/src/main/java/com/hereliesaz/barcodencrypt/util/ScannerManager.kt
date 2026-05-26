package com.hereliesaz.barcodencrypt.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The Central Nerve Ganglion.
 *
 * A singleton object that decouples the request for a barcode scan from the Activity
 * that must perform it. This is crucial for components that run outside of an Activity context
 * (like a [android.app.Service]) but need to launch a UI-based scanner.
 *
 * It works using a combination of a [MutableSharedFlow] to broadcast the request and a simple
 * callback to return the result.
 *
 * This architecture prevents the spontaneous growth of parasitic structures like the `ScannerTrampolineActivity`.
 */
object ScannerManager {

    private val _requests = MutableSharedFlow<Unit>()
    /**
     * A hot flow that emits a value whenever a barcode scan is requested.
     * An Activity with a UI context should collect this flow and launch the scanner UI.
     */
    val requests = _requests.asSharedFlow()

    @Volatile
    private var resultCallback: ((String?) -> Unit)? = null

    /**
     * A component (like an OverlayService) calls this to initiate a scan.
     * It stores the [onResult] callback and emits a new value to the [requests] flow,
     * which triggers any listening UI to launch the scanner.
     *
     * @param onResult The function to execute when the scanner returns a result. The result
     * is a [String] containing the barcode value, or null if the scan was cancelled.
     */
    suspend fun requestScan(onResult: (String?) -> Unit) {
        synchronized(this) { resultCallback = onResult }
        _requests.emit(Unit)
    }

    /**
     * The Activity that launched the scanner calls this to deliver the scan result.
     * It reads and clears the stored [resultCallback] atomically (the request and result
     * arrive on different threads), then invokes it.
     *
     * @param result The scanned barcode value, or null if the scan was cancelled.
     */
    fun onScanResult(result: String?) {
        val callback = synchronized(this) {
            val current = resultCallback
            resultCallback = null
            current
        }
        callback?.invoke(result)
    }
}
