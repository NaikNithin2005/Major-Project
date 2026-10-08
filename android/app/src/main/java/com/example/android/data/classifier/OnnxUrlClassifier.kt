package com.example.android.data.classifier

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlAnalysisInput
import com.example.android.domain.model.UrlFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.math.ln
import kotlin.math.roundToInt

data class UrlModelEvaluation(
    val phishingProbability: Float,
    val anomalyScore: Float,
    val isQuishing: Boolean,
    val riskScore: Int,
    val confidence: Float,
    val isXgboostAvailable: Boolean,
    val isAnomalyAvailable: Boolean,
    val modelVersion: String = "xgboost-url-v2",
    val featureVector: FloatArray
)

/**
 * Production ONNX Runtime Classifier for URLs & Quishing Prevention.
 * Runs Phase 5 trained XGBoost URL Classifier (v2) and Isolation Forest Zero-Day Anomaly Detector (v2) on-device.
 * Guarantees zero crashes and graceful heuristic fallback upon model loading or tensor failure.
 */
class OnnxUrlClassifier(
    private val context: Context,
    private val xgboostModelAsset: String = "models/xgboost_url.onnx",
    private val isolationForestAsset: String = "models/isolation_forest.onnx"
) : UrlClassifier {

    companion object {
        private const val TAG = "OnnxUrlClassifier"
        const val MODEL_VERSION = "xgboost-url-v2"
        const val ANOMALY_VERSION = "isolation-forest-v2"

        val SUSPICIOUS_TLDS = setOf(
            ".xyz", ".top", ".site", ".online", ".cc", ".tk", ".zip", ".club", ".link", ".tech", ".store", ".info"
        )
    }

    private var ortEnv: OrtEnvironment? = null
    private var xgboostSession: OrtSession? = null
    private var isolationForestSession: OrtSession? = null

    @Volatile
    var isInitialized: Boolean = false
        private set

    init {
        initializeSessions()
    }

    private fun initializeSessions() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()

            // 1. Load XGBoost URL Classifier
            try {
                context.assets.open(xgboostModelAsset).use { inputStream ->
                    val modelBytes = inputStream.readBytes()
                    xgboostSession = ortEnv?.createSession(modelBytes, sessionOptions)
                    Log.i(TAG, "Successfully loaded XGBoost ONNX model from $xgboostModelAsset (${modelBytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize XGBoost ONNX model: ${e.message}")
            }

            // 2. Load Isolation Forest Anomaly Detector
            try {
                context.assets.open(isolationForestAsset).use { inputStream ->
                    val modelBytes = inputStream.readBytes()
                    isolationForestSession = ortEnv?.createSession(modelBytes, sessionOptions)
                    Log.i(TAG, "Successfully loaded Isolation Forest ONNX model from $isolationForestAsset (${modelBytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize Isolation Forest ONNX model: ${e.message}")
            }

            isInitialized = xgboostSession != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OrtEnvironment: ${e.message}", e)
            isInitialized = false
        }
    }

    override suspend fun classify(input: UrlAnalysisInput): QrAnalysisResult {
        val eval = evaluateUrl(input.extractedUrl, input.features)
        return QrAnalysisResult(
            id = UUID.randomUUID().toString(),
            timestamp = input.timestamp,
            rawContent = input.rawPayload,
            extractedUrl = input.extractedUrl,
            domain = input.features.domain.ifBlank { input.features.hostname },
            riskScore = eval.riskScore,
            isQuishing = eval.isQuishing,
            confidence = eval.confidence,
            processedAt = System.currentTimeMillis()
        )
    }

    suspend fun evaluateUrl(url: String, features: UrlFeatures): UrlModelEvaluation = withContext(Dispatchers.Default) {
        val featureVector = extract16FeatureArray(url, features)

        var pPhishing: Float? = null
        var anomalyScore: Float? = null
        var isXgbOk = false
        var isAnomOk = false

        // 1. Run XGBoost Inference
        val env = ortEnv
        val xgbSess = xgboostSession
        if (env != null && xgbSess != null) {
            try {
                val floatBuffer = FloatBuffer.wrap(featureVector)
                val tensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 16))
                tensor.use { onnxTensor ->
                    val results = xgbSess.run(mapOf("float_input" to onnxTensor))
                    results.use { ortOutputs ->
                        // Output 1 is "probabilities" (float32 array of shape [1, 2])
                        if (ortOutputs.size() >= 2) {
                            val probValue = ortOutputs.get(1).value
                            if (probValue is Array<*> && probValue.isNotEmpty() && probValue[0] is FloatArray) {
                                val probs = probValue[0] as FloatArray
                                if (probs.size >= 2) {
                                    pPhishing = probs[1]
                                    isXgbOk = true
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "XGBoost inference failed: ${e.message}", e)
            }
        }

        // 2. Run Isolation Forest Inference
        val isoSess = isolationForestSession
        if (env != null && isoSess != null) {
            try {
                val floatBuffer = FloatBuffer.wrap(featureVector)
                val tensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 16))
                tensor.use { onnxTensor ->
                    val results = isoSess.run(mapOf("float_input" to onnxTensor))
                    results.use { ortOutputs ->
                        // Output 0 is "label" (LongArray [[1]] or [[-1]])
                        // Output 1 is "scores" (FloatArray [[score]])
                        if (ortOutputs.size() >= 1) {
                            val labelValue = ortOutputs.get(0).value
                            if (labelValue is Array<*> && labelValue.isNotEmpty() && labelValue[0] is LongArray) {
                                val labels = labelValue[0] as LongArray
                                val label = labels[0] // 1: normal, -1: anomaly
                                anomalyScore = if (label == -1L) 85f else 15f
                                isAnomOk = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Isolation Forest inference failed: ${e.message}", e)
            }
        }

        // 3. Graceful fallback if XGBoost was unavailable
        val finalProb = pPhishing ?: calculateFallbackHeuristicProbability(features)
        val finalAnomaly = anomalyScore ?: (if (features.suspiciousCharacteristics.size >= 2) 75f else 10f)

        val riskScore = (finalProb * 100f).roundToInt().coerceIn(0, 100)
        val isQuishing = riskScore >= 50
        val confidence = if (isXgbOk) {
            if (finalProb >= 0.5f) finalProb else 1.0f - finalProb
        } else {
            0.75f // Lower confidence when using heuristic fallback
        }

        UrlModelEvaluation(
            phishingProbability = finalProb,
            anomalyScore = finalAnomaly,
            isQuishing = isQuishing,
            riskScore = riskScore,
            confidence = confidence.coerceIn(0.5f, 0.99f),
            isXgboostAvailable = isXgbOk,
            isAnomalyAvailable = isAnomOk,
            featureVector = featureVector
        )
    }

    /**
     * Extracts exactly 16 canonical features matching Phase 5 schema & Python url_feature_extractor.py:
     * 1. url_length
     * 2. domain_length
     * 3. subdomain_count
     * 4. path_length
     * 5. query_length
     * 6. has_ip
     * 7. has_at_symbol
     * 8. has_double_slash_redirect
     * 9. has_dash_in_domain
     * 10. suspicious_tld
     * 11. digit_count
     * 12. special_char_count
     * 13. entropy
     * 14. https_enabled
     * 15. port_specified
     * 16. encoding_present
     */
    fun extract16FeatureArray(url: String, features: UrlFeatures): FloatArray {
        val trimmed = url.trim()
        val afterScheme = if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed

        val urlLength = trimmed.length.toFloat()
        val domainLength = features.hostname.length.toFloat()
        val subdomainCount = features.subdomainCount.toFloat()
        val pathLength = features.path.length.toFloat()
        val queryLength = (features.query?.length ?: 0).toFloat()
        val hasIp = if (features.isIpHostname) 1.0f else 0.0f
        val hasAt = if (trimmed.contains("@")) 1.0f else 0.0f
        val hasDoubleSlash = if (afterScheme.contains("//")) 1.0f else 0.0f
        val hasDash = if (features.hostname.contains("-")) 1.0f else 0.0f
        val suspiciousTld = if (SUSPICIOUS_TLDS.any { features.hostname.lowercase().endsWith(it) }) 1.0f else 0.0f
        val digitCount = trimmed.count { it.isDigit() }.toFloat()
        val specialCharCount = trimmed.count { it in "-_@?=%&~#$!,;:*+[](){}<>" }.toFloat()
        val entropy = calculateShannonEntropy(trimmed)
        val httpsEnabled = if (features.isHttps) 1.0f else 0.0f
        val portSpecified = if (features.hasUnusualPort || (trimmed.contains(":") && afterScheme.contains(":"))) 1.0f else 0.0f
        val encodingPresent = if (trimmed.contains("%")) 1.0f else 0.0f

        return floatArrayOf(
            urlLength,
            domainLength,
            subdomainCount,
            pathLength,
            queryLength,
            hasIp,
            hasAt,
            hasDoubleSlash,
            hasDash,
            suspiciousTld,
            digitCount,
            specialCharCount,
            entropy,
            httpsEnabled,
            portSpecified,
            encodingPresent
        )
    }

    private fun calculateShannonEntropy(text: String): Float {
        if (text.isEmpty()) return 0.0f
        val len = text.length.toDouble()
        val freqMap = text.groupingBy { it }.eachCount()
        var entropy = 0.0
        for ((_, count) in freqMap) {
            val p = count / len
            entropy -= p * (ln(p) / ln(2.0))
        }
        return (Math.round(entropy * 10000.0) / 10000.0).toFloat()
    }

    private fun calculateFallbackHeuristicProbability(features: UrlFeatures): Float {
        var score = 0f
        if (!features.isHttps) score += 0.35f
        if (features.isIpHostname) score += 0.40f
        if (features.subdomainCount > 2) score += 0.20f
        if (features.hasSuspiciousSymbols) score += 0.15f
        if (features.hasUnusualPort) score += 0.25f
        if (features.isPunycode) score += 0.30f
        if (SUSPICIOUS_TLDS.any { features.hostname.lowercase().endsWith(it) }) score += 0.25f
        return score.coerceIn(0.05f, 0.95f)
    }
}
