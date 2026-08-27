package com.example.android.domain.classifier

import com.example.android.domain.model.ProcessedSms

/**
 * Interface contract between SMS preprocessing (Phase 3) and future TinyBERT inference (Phase 5).
 * Phase 5 will implement this interface using ONNX Runtime / TFLite TinyBERT model.
 */
interface SmishingClassifier {
    suspend fun classify(processedSms: ProcessedSms): SmsClassificationResult
}
