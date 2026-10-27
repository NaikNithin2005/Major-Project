package com.example.android.presentation.securitycheck

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.classifier.OnnxUrlClassifier
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.UrlAnalysisInput
import com.example.android.domain.model.UserFeedback
import com.example.android.domain.repository.FeedbackRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.xai.ExplainableAiEngine
import com.example.android.util.DeepLinkHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

sealed class SecurityCheckUiState {
    object Idle : SecurityCheckUiState()
    object Analyzing : SecurityCheckUiState()
    data class Success(val result: SecurityCheckResult, val threatId: String) : SecurityCheckUiState()
    data class Error(val message: String, val rawUrl: String) : SecurityCheckUiState()
}

class SecurityCheckViewModel(
    private val urlClassifier: UrlClassifier,
    private val ruleEngine: RuleEngine,
    private val explainableAiEngine: ExplainableAiEngine,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val feedbackRepository: FeedbackRepository? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow<SecurityCheckUiState>(SecurityCheckUiState.Idle)
    val uiState: StateFlow<SecurityCheckUiState> = _uiState.asStateFlow()

    private var currentThreatId: String = UUID.randomUUID().toString()

    fun analyzeUrl(rawUrl: String, source: String = "INCOMING_URL") {
        if (rawUrl.isBlank()) {
            _uiState.value = SecurityCheckUiState.Error("No URL was supplied for security analysis.", rawUrl)
            return
        }

        _uiState.value = SecurityCheckUiState.Analyzing
        currentThreatId = UUID.randomUUID().toString()

        viewModelScope.launch {
            try {
                val result = withContext(defaultDispatcher) {
                    performInspection(rawUrl, source)
                }

                // Persist the threat inspection record to local database
                val record = ThreatRecord(
                    id = currentThreatId,
                    timestamp = result.timestamp,
                    source = result.source,
                    sender = result.destinationDomain,
                    riskScore = result.riskScore,
                    category = result.threatCategory.name,
                    actionTaken = when (result.classification) {
                        UrlClassification.MALICIOUS_INDICATORS_DETECTED -> "QUARANTINED"
                        UrlClassification.SUSPICIOUS -> "WARNED"
                        UrlClassification.SAFE_INDICATORS_FOUND -> "VERIFIED"
                        UrlClassification.UNKNOWN -> "PENDING_REVIEW"
                    },
                    details = result.riskIndicators.joinToString("; ")
                )
                threatHistoryRepository.addThreat(record)

                _uiState.value = SecurityCheckUiState.Success(result, currentThreatId)
            } catch (e: Exception) {
                _uiState.value = SecurityCheckUiState.Error(
                    message = "Failed to complete AI model inference: ${e.message ?: "Unknown error"}",
                    rawUrl = rawUrl
                )
            }
        }
    }

    private suspend fun performInspection(rawUrl: String, source: String): SecurityCheckResult {
        // Step 1: Deep Link validation & SSRF defense
        val validation = DeepLinkHandler.validateAndBuildRequest(rawUrl, source)

        if (!validation.isValid) {
            val isSsrf = validation.isSsrfRisk
            return SecurityCheckResult(
                targetUrl = rawUrl,
                destinationDomain = validation.domain,
                source = source,
                classification = if (isSsrf) UrlClassification.MALICIOUS_INDICATORS_DETECTED else UrlClassification.UNKNOWN,
                threatCategory = if (isSsrf) ThreatCategoryType.MALWARE_WEB_THREAT else ThreatCategoryType.UNKNOWN_PROFILE,
                riskScore = if (isSsrf) 95 else 50,
                confidence = 0.95f,
                riskIndicators = listOf(validation.validationError ?: "Malformed or unsupported URL target"),
                recommendedActions = listOf(
                    "Do NOT attempt to open this address in any browser or web view.",
                    "Internal network addresses and non-standard schemes cannot be safely verified."
                ),
                isSsrfRisk = isSsrf,
                ssrfReason = validation.ssrfReason
            )
        }

        val normalized = validation.normalizedUrl
        val features = UrlFeatureExtractor.extractFeatures(normalized)
        val domain = validation.domain

        // Step 2: Feature Extraction & ML Model Inference (XGBoost + Isolation Forest)
        var urlProb: Float
        var anomalyScore = 15f
        var modelConfidence = 0.85f

        if (urlClassifier is OnnxUrlClassifier) {
            val eval = urlClassifier.evaluateUrl(normalized, features)
            urlProb = eval.phishingProbability
            anomalyScore = eval.anomalyScore
            modelConfidence = eval.confidence
        } else {
            val res = urlClassifier.classify(
                UrlAnalysisInput(rawPayload = normalized, extractedUrl = normalized, features = features, source = source)
            )
            urlProb = res.riskScore / 100f
            modelConfidence = res.confidence
        }

        // Step 3: Heuristic Rule Engine
        val ruleResults = ruleEngine.evaluate(urlFeatures = features)

        // Step 4: Explainable AI Engine
        val xaiReasons = explainableAiEngine.generateExplanations(
            urlProbability = urlProb,
            anomalyScore = anomalyScore,
            ruleResults = ruleResults
        )

        // Step 5: Risk Fusion & Threat Categorization
        var computedScore = (urlProb * 100f).roundToInt().coerceIn(0, 100)

        // Elevate score for definitive high-risk patterns
        if (validation.isSsrfRisk) {
            computedScore = maxOf(computedScore, 95)
        }
        if (features.isPunycode || features.isIpHostname) {
            computedScore = maxOf(computedScore, 80)
        }
        if (features.suspiciousCharacteristics.size >= 3) {
            computedScore = maxOf(computedScore, 75)
        }

        val classification = when {
            computedScore >= 70 -> UrlClassification.MALICIOUS_INDICATORS_DETECTED
            computedScore in 35..69 -> UrlClassification.SUSPICIOUS
            computedScore < 35 && features.domain.isNotBlank() && !features.isIpHostname && !features.isPunycode && !features.hasSuspiciousSymbols -> UrlClassification.SAFE_INDICATORS_FOUND
            else -> UrlClassification.UNKNOWN // An unknown URL is NEVER automatically considered safe
        }

        val threatCategory = when {
            source.contains("QR", ignoreCase = true) -> ThreatCategoryType.QUISHING_ATTACK
            features.isPunycode || features.hasSuspiciousSymbols || normalized.contains("login", ignoreCase = true) || normalized.contains("verify", ignoreCase = true) || normalized.contains("bank", ignoreCase = true) -> ThreatCategoryType.PHISHING_URL
            validation.isSsrfRisk || normalized.endsWith(".apk", ignoreCase = true) || features.isIpHostname -> ThreatCategoryType.MALWARE_WEB_THREAT
            classification == UrlClassification.SAFE_INDICATORS_FOUND -> ThreatCategoryType.VERIFIED_SAFE
            else -> ThreatCategoryType.UNKNOWN_PROFILE
        }

        val indicators = mutableListOf<String>()
        if (validation.isSsrfRisk && validation.ssrfReason != null) {
            indicators.add("SSRF Defense: ${validation.ssrfReason}")
        }
        if (xaiReasons.isNotEmpty()) {
            indicators.addAll(xaiReasons.map { "${it.title}: ${it.description}" })
        } else {
            indicators.addAll(features.suspiciousCharacteristics)
        }
        if (indicators.isEmpty()) {
            indicators.add("Standard URL syntax evaluated; no high-risk heuristic anomalies detected.")
        }

        val recommendations = when (classification) {
            UrlClassification.MALICIOUS_INDICATORS_DETECTED -> listOf(
                "Do NOT open this URL in your web browser.",
                "Never submit passwords, OTPs, or payment information to this destination.",
                "If you already interacted with this domain, change credentials on the official service immediately."
            )
            UrlClassification.SUSPICIOUS -> listOf(
                "Exercise extreme caution; suspicious structural indicators were flagged.",
                "Verify the domain directly through an official verified search or contact channel.",
                "Avoid downloading attachments or entering personal identifiers."
            )
            UrlClassification.UNKNOWN -> listOf(
                "Caution: Indeterminate threat profile. No conclusive safety evidence found.",
                "Do not assume this destination is safe simply because it is unknown.",
                "Inspect the domain name carefully for subtle typos before proceeding."
            )
            UrlClassification.SAFE_INDICATORS_FOUND -> listOf(
                "All on-device machine learning and structural checks passed.",
                "Standard web safety practices apply: always confirm HTTPS padlock in your browser."
            )
        }

        return SecurityCheckResult(
            targetUrl = normalized,
            destinationDomain = domain,
            source = source,
            classification = classification,
            threatCategory = threatCategory,
            riskScore = computedScore,
            confidence = modelConfidence,
            riskIndicators = indicators,
            recommendedActions = recommendations,
            isSsrfRisk = validation.isSsrfRisk,
            ssrfReason = validation.ssrfReason,
            isOfflineMode = true
        )
    }

    fun submitFeedback(threatId: String, userCategory: String, comment: String) {
        viewModelScope.launch {
            feedbackRepository?.submitFeedback(
                UserFeedback(
                    id = UUID.randomUUID().toString(),
                    threatId = threatId,
                    timestamp = System.currentTimeMillis(),
                    userCategory = userCategory,
                    comment = comment,
                    isSubmitted = true
                )
            )
            if (userCategory == "FALSE_POSITIVE") {
                val existing = threatHistoryRepository.getThreatById(threatId).getOrNull()
                if (existing != null) {
                    threatHistoryRepository.updateThreat(existing.copy(actionTaken = "FALSE_POSITIVE"))
                }
            }
        }
    }

    fun copyUrlToClipboard(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sanitized URL", url))
    }

    fun proceedToUrl(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (ignored: Exception) {
        }
    }
}
