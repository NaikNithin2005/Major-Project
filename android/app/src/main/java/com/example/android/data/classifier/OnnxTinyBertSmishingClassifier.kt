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
    val modelVersion: String = "tinybert-smishing-banking-v2",
    val classId: Int = 1, // 0: LEGITIMATE_BANKING, 1: LEGITIMATE_GENERAL, 2: SMISHING, 3: SUSPICIOUS
    val classificationLabel: String = "LEGITIMATE_GENERAL",
    val probabilities: Map<String, Float> = emptyMap()
)

/**
 * Production ONNX Runtime Smishing Classifier for SMS messages.
 * Runs 4-Layer TinyBERT 4-class model on-device with BertWordPieceTokenizer.
 * Accurately differentiates legitimate banking transactions from smishing attacks.
 * Guarantees zero crashes and graceful fallback upon tensor or model loading errors.
 */
class OnnxTinyBertSmishingClassifier(
    private val context: Context,
    private val modelAsset: String = "models/tinybert_smishing.onnx",
    private val vocabAsset: String = "models/vocab.txt"
) : SmishingClassifier {

    companion object {
        private const val TAG = "OnnxTinyBertClassifier"
        const val MODEL_VERSION = "tinybert-smishing-banking-v2"
        const val MAX_SEQ_LENGTH = 128

        val CLASS_NAMES = mapOf(
            0 to "LEGITIMATE_BANKING",
            1 to "LEGITIMATE_GENERAL",
            2 to "SMISHING",
            3 to "SUSPICIOUS"
        )
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
            evidence = processedSms.evidence,
            classId = eval.classId,
            classificationLabel = eval.classificationLabel,
            probabilities = eval.probabilities
        )
    }

    suspend fun evaluateSms(text: String): SmishingModelEvaluation = withContext(Dispatchers.Default) {
        val env = ortEnv
        val sess = ortSession
        val tok = tokenizer

        var probSmishing: Float? = null
        var isModelOk = false
        var predictedClassId = 1
        var predictedLabel = "LEGITIMATE_GENERAL"
        var evaluatedConfidence = 0.70f
        val probMap = mutableMapOf<String, Float>()

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
                            if (logits.size >= 4) {
                                // 4-Class Inference:
                                // 0: LEGITIMATE_BANKING, 1: LEGITIMATE_GENERAL, 2: SMISHING, 3: SUSPICIOUS
                                val maxLogit = logits.maxOrNull() ?: 0f
                                val exps = logits.map { exp((it - maxLogit).toDouble()) }
                                val sumExp = exps.sum()
                                if (sumExp > 0.0) {
                                    val p0 = (exps[0] / sumExp).toFloat()
                                    val p1 = (exps[1] / sumExp).toFloat()
                                    val p2 = (exps[2] / sumExp).toFloat()
                                    val p3 = (exps[3] / sumExp).toFloat()

                                    probMap["LEGITIMATE_BANKING"] = p0
                                    probMap["LEGITIMATE_GENERAL"] = p1
                                    probMap["SMISHING"] = p2
                                    probMap["SUSPICIOUS"] = p3

                                    probSmishing = p2
                                    val probsList = listOf(p0, p1, p2, p3)
                                    val maxIdx = probsList.indices.maxByOrNull { probsList[it] } ?: 1
                                    predictedClassId = maxIdx
                                    predictedLabel = CLASS_NAMES[maxIdx] ?: "LEGITIMATE_GENERAL"
                                    evaluatedConfidence = probsList[maxIdx]
                                    isModelOk = true
                                }
                            } else if (logits.size >= 2) {
                                // Fallback 2-Class model: Label 0: Safe, Label 1: Smishing
                                val exp0 = exp(logits[0].toDouble())
                                val exp1 = exp(logits[1].toDouble())
                                val sumExp = exp0 + exp1
                                if (sumExp > 0.0) {
                                    probSmishing = (exp1 / sumExp).toFloat()
                                    val pSafe = (exp0 / sumExp).toFloat()
                                    probMap["safe"] = pSafe
                                    probMap["smishing"] = probSmishing
                                    predictedClassId = if (probSmishing >= 0.5f) 2 else 1
                                    predictedLabel = if (probSmishing >= 0.5f) "SMISHING" else "LEGITIMATE_GENERAL"
                                    evaluatedConfidence = maxOf(pSafe, probSmishing)
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

        // Calculate calibrated risk score & category based on 4 classes
        val finalProb = probSmishing ?: calculateFallbackHeuristic(text)
        val (riskScore, category, isSmishing) = when (predictedClassId) {
            0 -> {
                // LEGITIMATE_BANKING: explicitly safe, prevent false smishing alerts
                val score = ((finalProb * 15f)).roundToInt().coerceIn(0, 20)
                Triple(score, "SAFE", false)
            }
            1 -> {
                // LEGITIMATE_GENERAL: safe
                val score = ((finalProb * 25f)).roundToInt().coerceIn(0, 25)
                Triple(score, "SAFE", false)
            }
            3 -> {
                // SUSPICIOUS: ambiguous links/warnings, cautious medium risk
                val score = (45f + finalProb * 25f).roundToInt().coerceIn(40, 70)
                Triple(score, "MEDIUM", false)
            }
            2 -> {
                // SMISHING: malicious intent
                val score = (75f + finalProb * 24f).roundToInt().coerceIn(75, 100)
                Triple(score, "HIGH", true)
            }
            else -> {
                val score = (finalProb * 100f).roundToInt().coerceIn(0, 100)
                val cat = when {
                    score >= 76 -> "HIGH"
                    score >= 51 -> "MEDIUM"
                    score >= 26 -> "LOW"
                    else -> "SAFE"
                }
                Triple(score, cat, score >= 50)
            }
        }

        val confidence = if (isModelOk) {
            evaluatedConfidence.coerceIn(0.5f, 0.99f)
        } else {
            0.70f
        }

        SmishingModelEvaluation(
            smishingProbability = finalProb,
            isSmishing = isSmishing,
            riskScore = riskScore,
            confidence = confidence,
            threatCategory = category,
            isModelAvailable = isModelOk,
            classId = predictedClassId,
            classificationLabel = predictedLabel,
            probabilities = probMap
        )
    }

    private fun calculateFallbackHeuristic(text: String): Float {
        val lower = text.lowercase()
        var score = 0f
        val hasUrl = lower.contains("http://") || lower.contains("https://") || lower.contains("bit.ly") || lower.contains("tinyurl")
        val hasUrgency = listOf("urgent", "immediate", "blocked", "suspended", "verify kyc", "deactivated", "expire").any { lower.contains(it) }
        val hasCred = listOf("share otp", "send otp", "enter pin", "share pin", "cvv").any { lower.contains(it) }

        if (hasCred) return 0.95f
        if (hasUrl && hasUrgency) return 0.88f
        if (hasUrl) score += 0.35f
        if (hasUrgency) score += 0.25f

        // If legitimate banking format without external url, reward safety
        val isLegitBankPattern = (lower.contains("debited") || lower.contains("credited") || lower.contains("avl bal") || lower.contains("upi ref")) && !hasUrl
        if (isLegitBankPattern) {
            return 0.05f
        }

        return score.coerceIn(0.05f, 0.90f)
    }
}
