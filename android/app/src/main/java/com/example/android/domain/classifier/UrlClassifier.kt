package com.example.android.domain.classifier

import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlAnalysisInput

/**
 * Interface contract between QR URL feature extraction (Phase 4) and future XGBoost classification (Phase 5).
 * Phase 5 will implement this interface using ONNX Runtime / XGBoost URL classification model.
 */
interface UrlClassifier {
    suspend fun classify(input: UrlAnalysisInput): QrAnalysisResult
}
