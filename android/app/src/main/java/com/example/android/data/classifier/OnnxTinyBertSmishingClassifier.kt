package com.example.android.data.classifier

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.android.data.tokenizer.BertWordPieceTokenizer
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.classifier.SmsClassificationResult
import com.example.android.domain.model.ProcessedSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.roundToInt

data class SmishingModelEvaluation(
    val smishingProbability: Float,
    val isSmishing: Boolean,
    val riskScore: Int,
    val confidence: Float,
    val threatCategory: String,
    val isModelAvailable: Boolean,
    val modelVersion: String = "tinybert-smishing-v2"
)

/**
 * Production ONNX Runtime Smishing Classifier for SMS messages.
 * Runs Phase 5 trained 4-Layer TinyBERT model (v2) on-device with BertWordPieceTokenizer.
 * Guarantees zero crashes and graceful fallback upon tensor or model loading errors.
 */
class OnnxTinyBertSmishingClassifier(
    private val context: Context,
    private val modelAsset: String = "models/tinybert_smishing.onnx",
    private val vocabAsset: String = "models/vocab.txt"
) : SmishingClassifier {

    companion object {
        private const val TAG = "OnnxTinyBertClassifier"
        const val MODEL_VERSION = "tinybert-smishing-v2"
        const val MAX_SEQ_LENGTH = 128
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BertWordPieceTokenizer? = null

    @Volatile
    var isInitialized: Boolean = false
        private set

    init {
        initializeModel()
    }

    private fun initializeModel() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()

            tokenizer = BertWordPieceTokenizer.fromAssets(context, vocabAsset, MAX_SEQ_LENGTH)

            context.assets.open(modelAsset).use { inputStream ->
                val modelBytes = inputStream.readBytes()
                ortSession = ortEnv?.createSession(modelBytes, sessionOptions)
                Log.i(TAG, "Successfully loaded TinyBERT ONNX model from $modelAsset (${modelBytes.size} bytes)")
            }

            isInitialized = ortSession != null
        } catch (e: Exception) {
            Log.w(TAG, "TinyBERT ONNX initialization unavailable: ${e.message}")
            isInitialized = false
        }
    }

    override suspend fun classify(processedSms: ProcessedSms): SmsClassificationResult {
        val eval = evaluateSms(processedSms.normalizedText)

        return SmsClassificationResult(
            riskScore = eval.riskScore,
            isSmishing = eval.isSmishing,
            confidence = eval.confidence,
            threatCategory = eval.threatCategory,
            evidence = processedSms.evidence
        )
    }

    suspend fun evaluateSms(text: String): SmishingModelEvaluation = withContext(Dispatchers.Default) {
        val env = ortEnv
        val sess = ortSession
        val tok = tokenizer

        var probSmishing: Float? = null
        var isModelOk = false

        if (env != null && sess != null && tok != null) {
            try {
                val encoded = tok.encode(text)

                val inputIdsBuffer = LongBuffer.wrap(encoded.inputIds)
                val attentionMaskBuffer = LongBuffer.wrap(encoded.attentionMask)

                val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsBuffer, longArrayOf(1, MAX_SEQ_LENGTH.toLong()))
                val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, longArrayOf(1, MAX_SEQ_LENGTH.toLong()))

                val inputs = mapOf(
                    "input_ids" to inputIdsTensor,
                    "attention_mask" to attentionMaskTensor
                )

                sess.run(inputs).use { ortOutputs ->
                    if (ortOutputs.size() >= 1) {
                        val logitsVal = ortOutputs.get(0).value
                        if (logitsVal is Array<*> && logitsVal.isNotEmpty() && logitsVal[0] is FloatArray) {
                            val logits = logitsVal[0] as FloatArray
                            if (logits.size >= 2) {
                                // Label 0: Safe, Label 1: Smishing
                                val exp0 = exp(logits[0].toDouble())
                                val exp1 = exp(logits[1].toDouble())
                                val sumExp = exp0 + exp1
                                if (sumExp > 0.0) {
                                    probSmishing = (exp1 / sumExp).toFloat()
                                    isModelOk = true
                                }
                            }
                        }
                    }
                }

                inputIdsTensor.close()
                attentionMaskTensor.close()
            } catch (e: Exception) {
                Log.e(TAG, "TinyBERT inference failed: ${e.message}", e)
            }
        }

        // Graceful fallback if model inference failed
        val finalProb = probSmishing ?: calculateFallbackHeuristic(text)
        val riskScore = (finalProb * 100f).roundToInt().coerceIn(0, 100)
        val isSmishing = riskScore >= 50

        val category = when {
            riskScore >= 76 -> "HIGH"
            riskScore >= 51 -> "MEDIUM"
            riskScore >= 26 -> "LOW"
            else -> "SAFE"
        }

        val confidence = if (isModelOk) {
            if (finalProb >= 0.5f) finalProb else 1.0f - finalProb
        } else {
            0.70f
        }

        SmishingModelEvaluation(
            smishingProbability = finalProb,
            isSmishing = isSmishing,
            riskScore = riskScore,
            confidence = confidence.coerceIn(0.5f, 0.99f),
            threatCategory = category,
            isModelAvailable = isModelOk
        )
    }

    private fun calculateFallbackHeuristic(text: String): Float {
        val lower = text.lowercase()
        var score = 0f
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("bit.ly")) score += 0.35f
        if (listOf("urgent", "immediate", "blocked", "suspended", "verify", "kyc", "deactivated").any { lower.contains(it) }) score += 0.30f
        if (listOf("bank", "sbi", "hdfc", "icici", "otp", "pan", "aadhar", "card").any { lower.contains(it) }) score += 0.25f
        return score.coerceIn(0.05f, 0.90f)
    }
}
