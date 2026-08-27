package com.example.android.presentation.qr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.scanner.GalleryQrScanner
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlFeatures
import com.example.android.domain.repository.QrAnalysisRepository
import com.example.android.domain.usecase.CheckCameraPermissionUseCase
import com.example.android.domain.usecase.ProcessQrCodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class CameraPermissionState {
    object Granted : CameraPermissionState()
    object Denied : CameraPermissionState()
    object PermanentlyDenied : CameraPermissionState()
}

class QrScannerViewModel(
    private val checkCameraPermissionUseCase: CheckCameraPermissionUseCase,
    private val processQrCodeUseCase: ProcessQrCodeUseCase,
    private val qrAnalysisRepository: QrAnalysisRepository
) : ViewModel() {

    private val _permissionState = MutableStateFlow<CameraPermissionState>(CameraPermissionState.Denied)
    val permissionState: StateFlow<CameraPermissionState> = _permissionState.asStateFlow()

    private val _isFlashEnabled = MutableStateFlow(false)
    val isFlashEnabled: StateFlow<Boolean> = _isFlashEnabled.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _scanResult = MutableStateFlow<QrAnalysisResult?>(null)
    val scanResult: StateFlow<QrAnalysisResult?> = _scanResult.asStateFlow()

    private val _extractedFeatures = MutableStateFlow<UrlFeatures?>(null)
    val extractedFeatures: StateFlow<UrlFeatures?> = _extractedFeatures.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val scanHistory: StateFlow<List<QrAnalysisResult>> = qrAnalysisRepository.observeAllAnalyses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkPermission()
    }

    fun checkPermission() {
        val granted = checkCameraPermissionUseCase()
        _permissionState.value = if (granted) CameraPermissionState.Granted else CameraPermissionState.Denied
    }

    fun onCameraPermissionResult(isGranted: Boolean) {
        _permissionState.value = if (isGranted) CameraPermissionState.Granted else CameraPermissionState.Denied
    }

    fun toggleFlash() {
        _isFlashEnabled.value = !_isFlashEnabled.value
    }

    fun setZoomRatio(ratio: Float) {
        _zoomRatio.value = ratio.coerceIn(1.0f, 5.0f)
    }

    fun onQrCodeScanned(rawPayload: String, source: String = "CAMERA") {
        if (_isProcessing.value) return
        if (rawPayload.isBlank()) {
            _errorMessage.value = "Scanned QR code contains empty or invalid payload."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null

            val result = processQrCodeUseCase(rawPayload, source)
            result.fold(
                onSuccess = { analysisResult ->
                    _scanResult.value = analysisResult
                    if (analysisResult.extractedUrl.isNotBlank()) {
                        _extractedFeatures.value = UrlFeatureExtractor.extractFeatures(analysisResult.extractedUrl)
                    } else {
                        _extractedFeatures.value = null
                    }
                },
                onFailure = { throwable ->
                    _errorMessage.value = throwable.message ?: "Failed to decode QR code"
                }
            )
            _isProcessing.value = false
        }
    }

    fun onGalleryImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null

            val qrText = withContext(Dispatchers.IO) {
                GalleryQrScanner.scanGalleryUri(context, uri)
            }

            if (!qrText.isNullOrBlank()) {
                onQrCodeScanned(qrText, source = "GALLERY")
            } else {
                _errorMessage.value = "No valid QR code found in selected image."
                _isProcessing.value = false
            }
        }
    }

    fun dismissResult() {
        _scanResult.value = null
        _extractedFeatures.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteHistoryRecord(id: String) {
        viewModelScope.launch {
            qrAnalysisRepository.deleteAnalysis(id)
        }
    }
}
