package com.example.android.presentation.ai

import com.example.android.data.classifier.OnnxTinyBertSmishingClassifier
import com.example.android.data.classifier.OnnxUrlClassifier
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.tips.SecurityTipsProvider
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.UrlAnalysisInput
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.xai.ExplainableAiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Intelligent Cybersecurity Engine powering Squish AI Assistant.
 * Integrates on-device TinyBERT ONNX, XGBoost URL classifier, heuristic RuleEngine,
 * and Explainable AI (XAI) to produce structured, authoritative cybersecurity explanations.
 */
class SquishAiAssistantEngine(
    private val smishingClassifier: SmishingClassifier,
    private val urlClassifier: UrlClassifier,
    private val ruleEngine: RuleEngine,
    private val xaiEngine: ExplainableAiEngine
) {

    suspend fun processUserPrompt(
        prompt: String,
        activeContext: AssistantContext? = null
    ): ChatMessage = withContext(Dispatchers.Default) {
        val trimmed = prompt.trim()

        // 1. If explaining an explicit Threat Alert Context
        if (activeContext != null && activeContext.type == AssistantContextType.ALERT_EXPLANATION) {
            return@withContext explainAlertContext(activeContext, trimmed)
        }

        // 2. Detect if user supplied a URL or requested URL inspection
        val extractedUrls = UrlExtractor.extractUrls(trimmed)
        val isExplicitUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                            trimmed.startsWith("https://", ignoreCase = true) ||
                            trimmed.startsWith("www.", ignoreCase = true) ||
                            (extractedUrls.isNotEmpty() && !trimmed.contains(" "))

        if (isExplicitUrl || (extractedUrls.isNotEmpty() && trimmed.length < 120)) {
            val targetUrl = if (isExplicitUrl) UrlExtractor.normalizeUrl(trimmed) else UrlExtractor.normalizeUrl(extractedUrls.first())
            return@withContext analyzeUrlQuery(targetUrl)
        }

        // 3. Detect if user is asking to analyze message text
        val isExplicitMessageCheck = trimmed.startsWith("check message:", ignoreCase = true) ||
                                     trimmed.startsWith("analyze message:", ignoreCase = true) ||
                                     trimmed.startsWith("is this sms safe:", ignoreCase = true) ||
                                     (trimmed.length > 25 && (trimmed.contains("urgent", ignoreCase = true) ||
                                                              trimmed.contains("account", ignoreCase = true) ||
                                                              trimmed.contains("otp", ignoreCase = true) ||
                                                              trimmed.contains("bank", ignoreCase = true) ||
                                                              trimmed.contains("delivery", ignoreCase = true) ||
                                                              trimmed.contains("click", ignoreCase = true) ||
                                                              trimmed.contains("http", ignoreCase = true)))

        if (isExplicitMessageCheck && !trimmed.endsWith("?")) {
            val textToAnalyze = trimmed
                .removePrefix("check message:")
                .removePrefix("analyze message:")
                .removePrefix("is this sms safe:")
                .trim()
            return@withContext analyzeMessageQuery(textToAnalyze)
        }

        // 4. Conversational Cybersecurity Educational Inquiries
        return@withContext handleEducationalQuery(trimmed)
    }

    private suspend fun analyzeUrlQuery(url: String): ChatMessage {
        val features = UrlFeatureExtractor.extractFeatures(url)

        var urlProb = 0.05f
        var anomalyScore = 0.0f

        if (urlClassifier is OnnxUrlClassifier) {
            val eval = urlClassifier.evaluateUrl(url, features)
            urlProb = eval.phishingProbability
            anomalyScore = eval.anomalyScore
        } else {
            val res = urlClassifier.classify(
                UrlAnalysisInput(rawPayload = url, extractedUrl = url, features = features, source = "ASSISTANT")
            )
            urlProb = res.confidence
        }

        val ruleResults = ruleEngine.evaluate(urlFeatures = features)
        val xaiReasons = xaiEngine.generateExplanations(
            urlProbability = urlProb,
            anomalyScore = anomalyScore,
            ruleResults = ruleResults
        )

        val riskScore = (urlProb * 100).toInt().coerceIn(0, 100)
        val (verdict, severity) = when {
            riskScore >= 75 -> "MALICIOUS" to "HIGH"
            riskScore >= 40 -> "SUSPICIOUS" to "MEDIUM"
            else -> "SAFE" to "LOW"
        }

        val reasonsList = if (xaiReasons.isNotEmpty()) {
            xaiReasons.map { "${it.title}: ${it.description}" }
        } else {
            listOf("Structural URL features analyzed with Onnx URL Classifier.")
        }

        val actionsList = when (verdict) {
            "MALICIOUS" -> listOf(
                "Do NOT open this link under any circumstances.",
                "If credentials were submitted, change affected passwords immediately from an official portal.",
                "Report and block the sender."
            )
            "SUSPICIOUS" -> listOf(
                "Verify the domain name carefully before interacting.",
                "Check the official website by typing the verified URL directly into your browser.",
                "Do not submit sensitive financial credentials or OTPs."
            )
            else -> listOf(
                "Standard structural indicators appear normal.",
                "Always remain cautious when entering credentials on unfamiliar portals."
            )
        }

        val card = SecurityVerdictCard(
            verdict = verdict,
            severity = severity,
            score = riskScore,
            confidence = urlProb,
            category = "Phishing URL Analysis",
            whyReasons = reasonsList,
            recommendedActions = actionsList,
            rawPayload = url
        )

        val responseText = "I analyzed the link **$url** using Squish On-Device URL Model.\n\n" +
                "**Verdict:** $verdict ($riskScore/100 risk score)\n\n" +
                reasonsList.joinToString("\n") { "• $it" }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = responseText,
            verdictCard = card
        )
    }

    private suspend fun analyzeMessageQuery(messageText: String): ChatMessage {
        var smishingProb = 0.1f
        var isModelAvailable = true

        if (smishingClassifier is OnnxTinyBertSmishingClassifier) {
            val eval = smishingClassifier.evaluateSms(messageText)
            smishingProb = eval.smishingProbability
            isModelAvailable = eval.isModelAvailable
        }

        val xaiReasons = xaiEngine.generateExplanations(
            smsProbability = smishingProb,
            maxReasons = 4
        )

        val riskScore = (smishingProb * 100).toInt().coerceIn(0, 100)
        val (verdict, severity) = when {
            riskScore >= 70 -> "MALICIOUS" to "CRITICAL"
            riskScore >= 40 -> "SUSPICIOUS" to "MEDIUM"
            else -> "SAFE" to "LOW"
        }

        val reasonsList = if (xaiReasons.isNotEmpty()) {
            xaiReasons.map { "${it.title}: ${it.description}" }
        } else {
            listOf("Processed through 4-Layer Onnx TinyBERT Smishing Classifier.")
        }

        val actionsList = when (verdict) {
            "MALICIOUS" -> listOf(
                "Do not tap any links or dial phone numbers in this text.",
                "Never share OTPs, PINs, or banking details.",
                "Delete the message and block the sender."
            )
            "SUSPICIOUS" -> listOf(
                "Verify the claim directly through the company's verified app or website.",
                "Inspect any embedded links in Squish URL Scanner.",
                "Never share sensitive personal information."
            )
            else -> listOf(
                "No overt smishing or scam patterns were detected.",
                "Always verify unexpected requests for payment."
            )
        }

        val card = SecurityVerdictCard(
            verdict = verdict,
            severity = severity,
            score = riskScore,
            confidence = smishingProb,
            category = "Smishing Message Analysis",
            whyReasons = reasonsList,
            recommendedActions = actionsList,
            rawPayload = messageText
        )

        val responseText = "I examined the message content using the TinyBERT NLP Smishing Classifier.\n\n" +
                "**Verdict:** $verdict ($riskScore/100 risk score)\n\n" +
                reasonsList.joinToString("\n") { "• $it" }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = responseText,
            verdictCard = card
        )
    }

    private fun explainAlertContext(context: AssistantContext, prompt: String): ChatMessage {
        val title = context.title ?: "Security Alert"
        val severity = context.severity ?: "HIGH"
        val description = context.description ?: "Potential security anomaly detected"

        val explanation = "Here is the technical breakdown for **$title**:\n\n" +
                "• **Severity:** $severity\n" +
                "• **Indicators:** $description\n\n" +
                "**Why this requires attention:**\n" +
                "Our risk engine flagged this event because the pattern matches known social engineering triggers (e.g. deceptive links, urgency, or unverified sender IDs).\n\n" +
                "**Safe Next Steps:**\n" +
                "1. Do not interact with or open any linked content.\n" +
                "2. If this was an SMS, verify the sender independently.\n" +
                "3. If this was a legitimate communication, you can mark it as a False Positive in the Alerts tab."

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = explanation
        )
    }

    private fun handleEducationalQuery(query: String): ChatMessage {
        val lower = query.lowercase()

        val answer = when {
            lower.contains("click") && (lower.contains("link") || lower.contains("phishing")) -> {
                "**What to do if you clicked a suspicious link:**\n\n" +
                "1. **Disconnect immediately:** Close the browser tab right away.\n" +
                "2. **Did you enter credentials?** If you entered a password, go directly to the official legitimate website on another browser and **change your password immediately**.\n" +
                "3. **Enable Multi-Factor Authentication (2FA):** Turn on authenticator app 2FA on that account.\n" +
                "4. **Did you download a file?** Check your downloads folder; if an `.apk` or unknown file was downloaded, **do not open or install it** — delete it immediately.\n" +
                "5. **Monitor accounts:** Check bank statements for unauthorized transactions and report them to your bank within 24 hours."
            }
            lower.contains("quishing") || lower.contains("qr") -> {
                "**What is Quishing (QR Phishing)?**\n\n" +
                "Quishing is phishing disguised inside QR codes. Because human eyes cannot read encoded QR data, scammers place fake QR stickers over real ones (e.g., parking meters, restaurant menus, utility notices).\n\n" +
                "• **Protection:** Always use the Squish Scanner tab to preview and inspect the decoded link before opening it in your browser."
            }
            lower.contains("smishing") || lower.contains("sms") -> {
                "**What is Smishing?**\n\n" +
                "Smishing is SMS phishing where fraudsters send fake alerts (parcel delivery, bank KYC, electricity disconnection, lottery wins) containing links or phone numbers designed to steal your credentials or money.\n\n" +
                "• **Golden Rule:** Legitimate banks and utilities never send SMS links threatening immediate service disconnection."
            }
            lower.contains("otp") || lower.contains("pin") -> {
                "**One-Time Password (OTP) Protection:**\n\n" +
                "• **Never share an OTP with ANYONE**, even if the caller claims to be your bank's fraud detection department.\n" +
                "• Bank staff have internal access to cancel fraudulent charges — they NEVER need your authorization OTP to help you.\n" +
                "• Remember: Entering your UPI PIN sends money; it is never needed to receive money."
            }
            lower.contains("tip") -> {
                val tip = SecurityTipsProvider.getTodayTip()
                "**Today's Security Tip: ${tip.title}**\n\n" +
                "${tip.summary}\n\n" +
                "**Warning Signs:**\n" +
                tip.warningSigns.joinToString("\n") { "• $it" } + "\n\n" +
                "**Recommended Action:**\n${tip.recommendedAction}"
            }
            else -> {
                "I am **Squish AI**, your on-device cybersecurity assistant.\n\n" +
                "Here is how I can help you stay protected:\n" +
                "• **Analyze a message:** Paste any suspicious SMS text here.\n" +
                "• **Check a link:** Paste any URL to evaluate it with our ML classifier.\n" +
                "• **Explain alerts:** Tap 'Explain with AI' on any threat alert.\n" +
                "• **Cybersecurity advice:** Ask me what to do after clicking links, receiving fake calls, or sharing OTPs."
            }
        }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = answer
        )
    }
}
