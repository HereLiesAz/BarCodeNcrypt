package com.hereliesaz.barcodencrypt.viewmodel

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _cameraProviderLiveData = MutableLiveData<ProcessCameraProvider>()
    val cameraProviderLiveData: LiveData<ProcessCameraProvider> = _cameraProviderLiveData

    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val previewUseCase: Preview = Preview.Builder().build()
    val imageAnalysisUseCase: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetResolution(android.util.Size(1280, 720))
        .build()

    init {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            _cameraProviderLiveData.postValue(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindUseCases(cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                imageAnalysisUseCase
            )
        } catch (exc: Exception) {
            Log.e("CameraViewModel", "Use case binding failed", exc)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
