package com.example.android.presentation.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.android.data.decoder.ZxingQrDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX ImageAnalysis Analyzer for real-time QR code detection.
 * Includes thread safety and cooldown debounce to avoid repeated processing of identical QR frames.
 */
class QrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val isAnalyzing = AtomicBoolean(false)
    val isPaused = AtomicBoolean(false)
    private var lastScannedText: String? = null
    private var lastScannedTimestamp: Long = 0L

    companion object {
        private const val SCAN_COOLDOWN_MS = 2500L
    }

    fun setPaused(paused: Boolean) {
        isPaused.set(paused)
        if (!paused) {
            lastScannedTimestamp = System.currentTimeMillis()
        }
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Skip frame if paused or already processing
        if (isPaused.get() || isAnalyzing.get()) {
            imageProxy.close()
            return
        }

        if (isAnalyzing.compareAndSet(false, true)) {
            try {
                val qrText = ZxingQrDecoder.decodeImageProxy(imageProxy)
                if (!qrText.isNullOrBlank()) {
                    if (qrText != lastScannedText || (currentTime - lastScannedTimestamp > SCAN_COOLDOWN_MS)) {
                        lastScannedText = qrText
                        lastScannedTimestamp = currentTime
                        onQrDetected(qrText)
                    }
                }
            } catch (e: Exception) {
                // Analyzer error handling
            } finally {
                isAnalyzing.set(false)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    fun resetCooldown() {
        lastScannedText = null
        lastScannedTimestamp = 0L
    }
}
