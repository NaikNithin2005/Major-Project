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
import kotlinx.coroutines.withContext

/**
 * Intelligent Cybersecurity Engine powering Squish AI Assistant.
 * Produces structured, scannable conversational responses:
 * 1. Quick Summary (1-2 clear sentences)
 * 2. Key Findings (bulleted points with icons)
 * 3. Recommended Actions (numbered list in priority order)
 * 4. Safety Tip (contextual protection tip)
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
        if (activeContext != null && (activeContext.type == AssistantContextType.ALERT_EXPLANATION || activeContext.details != null)) {
            if (trimmed.contains("why", ignoreCase = true) ||
                trimmed.contains("flagged", ignoreCase = true) ||
                trimmed.contains("explain", ignoreCase = true) ||
                trimmed.contains("bank", ignoreCase = true) ||
                trimmed.contains("what should i do", ignoreCase = true) ||
                trimmed.contains("check sender", ignoreCase = true)
            ) {
                return@withContext explainAlertContext(activeContext, trimmed)
            }
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

        // 4. Conversational Cybersecurity Educational & Guidance Inquiries
        return@withContext handleEducationalQuery(trimmed, activeContext)
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
            listOf("Structural features checked with on-device URL model.")
        }

        val actionsList = when (verdict) {
            "MALICIOUS" -> listOf(
                "Do NOT open this link or visit any web destination.",
                "If credentials were entered, change your account password immediately from an official app.",
                "Block and report the sender address."
            )
            "SUSPICIOUS" -> listOf(
                "Verify the exact domain name before interacting.",
                "Open the official company website by typing it directly in your browser.",
                "Never share passwords, PINs, or OTPs on this website."
            )
            else -> listOf(
                "No phishing indicators detected in the web link.",
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

        val summary = when (verdict) {
            "MALICIOUS" -> "⚠️ **High-Risk Phishing Warning:** The link `$url` exhibits strong characteristics of a deceptive phishing website."
            "SUSPICIOUS" -> "⚠️ **Caution Advised:** The link `$url` contains unusual structural parameters that warrant verification before opening."
            else -> "✅ **Looks Safe:** The link `$url` passed structural safety checks with no phishing signs detected."
        }

        val formattedText = buildString {
            append(summary)
            append("\n\n### Key Findings\n")
            reasonsList.forEach { append("• $it\n") }
            append("\n### Recommended Actions\n")
            actionsList.forEachIndexed { i, act -> append("${i + 1}. $act\n") }
            append("\n💡 **Safety Tip:** Scammers often register lookalike domain names with minor spelling changes. Always inspect the exact web address.")
        }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = formattedText,
            verdictCard = card
        )
    }

    private suspend fun analyzeMessageQuery(messageText: String): ChatMessage {
        var smishingProb = 0.1f

        if (smishingClassifier is OnnxTinyBertSmishingClassifier) {
            val eval = smishingClassifier.evaluateSms(messageText)
            smishingProb = eval.smishingProbability
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
            listOf("Processed using on-device TinyBERT natural language model.")
        }

        val actionsList = when (verdict) {
            "MALICIOUS" -> listOf(
                "Do not tap any links or dial phone numbers in this message.",
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
                "Always verify unexpected requests for payment or transfers."
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

        val summary = when (verdict) {
            "MALICIOUS" -> "⚠️ **Dangerous Message Alert:** This message was flagged as a probable smishing or scam attempt ($riskScore/100 risk rating)."
            "SUSPICIOUS" -> "⚠️ **Needs Attention:** This message exhibits some social engineering urgency ($riskScore/100 risk rating)."
            else -> "✅ **Looks Safe:** This message passed on-device NLP checks ($riskScore/100 risk rating)."
        }

        val formattedText = buildString {
            append(summary)
            append("\n\n### Key Findings\n")
            reasonsList.forEach { append("• $it\n") }
            append("\n### Recommended Actions\n")
            actionsList.forEachIndexed { i, act -> append("${i + 1}. $act\n") }
            append("\n💡 **Safety Tip:** Legitimate banks and government departments never threaten immediate account suspension via text message.")
        }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = formattedText,
            verdictCard = card
        )
    }

    private fun explainAlertContext(context: AssistantContext, prompt: String): ChatMessage {
        val title = context.title ?: "Security Alert"
        val severity = context.severity ?: "HIGH"
        val details = context.details ?: "Suspicious pattern detected"

        val lower = prompt.lowercase()

        val response = when {
            lower.contains("is this bank message legitimate") || lower.contains("bank") -> {
                """
                ### Bank Message Legitimacy Check
                **Summary:** Genuine bank text messages have distinct characteristics that differentiate them from scams.
                
                ### Key Findings
                • **Sender Header:** Legitimate banks send SMS from registered enterprise alpha headers (e.g., `VM-HDFCBK`, `AD-SBIINB`), never from ordinary 10-digit mobile numbers.
                • **Transaction Notifications:** Real bank debits/credits include exact amounts, masked account numbers (`XX1234`), and a reference number, without asking you to open an unverified link.
                • **Account Suspension:** Legitimate banks never text threatening immediate account blockage unless you click an external link.
                
                ### Recommended Actions
                1. Check your official mobile banking app directly to see your genuine account balance and transactions.
                2. If the SMS was sent from an ordinary mobile number (+91...), do not reply or tap links.
                3. Call the customer care number printed on the back of your debit card if you are unsure.
                
                💡 **Safety Tip:** Never share a One-Time Password (OTP) with anyone over the phone or SMS. Bank officials will never ask for your OTP.
                """.trimIndent()
            }
            lower.contains("what should i do right now") || lower.contains("what should i do") -> {
                """
                ### Immediate Safety Action Plan
                **Summary:** Follow these priority steps right now to protect your accounts and personal data.
                
                ### Priority Steps
                1. **Do not tap links or call numbers:** Avoid interacting with any content inside the message.
                2. **Keep the threat isolated:** In Squish, keep the record in Quarantine so you don't accidentally open it.
                3. **Did you open a link?** Close the browser tab immediately. If you entered a password, change it on your official account immediately.
                4. **Block the sender:** In your phone messaging app, block the sender number to prevent further scam texts.
                
                💡 **Safety Tip:** When in doubt, always go directly to the verified official app or website on your phone rather than following links in messages.
                """.trimIndent()
            }
            lower.contains("how can i check this sender") || lower.contains("check sender") -> {
                """
                ### How to Verify a Message Sender
                **Summary:** Verify whether a sender is authentic before responding or acting on requests.
                
                ### Verification Guide
                • **Check the Sender Format:** Enterprise senders use 6-character registered headers (e.g. `AX-HDFCBK`, `BZ-AMAZON`). Scammers usually send from ordinary 10-digit mobile numbers.
                • **Inspect the Area Code:** International country codes (e.g. +1, +44, +234) claiming to represent local Indian banks or courier services are confirmed scams.
                • **Look up Official Numbers:** Search the official organization's verified contact page in your web browser.
                
                ### Recommended Actions
                1. Check if the sender is already in your trusted contact list.
                2. If this is a repeat legitimate sender, tap **'Trust This Sender'** in the scan details to prevent future alerts.
                3. Never call back the phone number written in the body of an unexpected text message.
                
                💡 **Safety Tip:** Spoofed sender IDs can sometimes mimic genuine names, but scammers cannot redirect official banking apps. Always verify via the official app.
                """.trimIndent()
            }
            else -> {
                """
                ### Threat Breakdown: $title
                **Summary:** This scan was flagged as **$severity** risk based on on-device security checks.
                
                ### Key Findings
                • **Severity Level:** $severity
                • **Triggered Indicators:** $details
                • **Threat Vectors:** Artificial urgency, unverified links, or sensitive information requests.
                
                ### Recommended Actions
                1. Do not open links, download attachments, or call numbers listed in the message.
                2. Verify any account requests independently through your official banking or provider app.
                3. If this was a legitimate message, tap **'Mark as Safe'** in the scan details to teach Squish your preferences.
                
                💡 **Safety Tip:** Squish checks all messages locally on your device without sending private texts to the cloud.
                """.trimIndent()
            }
        }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = response
        )
    }

    private fun handleEducationalQuery(query: String, context: AssistantContext? = null): ChatMessage {
        val lower = query.lowercase()

        val answer = when {
            lower.contains("why was this message flagged") || lower.contains("why was this flagged") -> {
                if (context?.details != null) {
                    explainAlertContext(context, query).text
                } else {
                    """
                    ### Why Was This Message Flagged?
                    **Summary:** Squish flags messages when they display characteristics frequently used in social engineering scams.
                    
                    ### Key Warning Signals
                    • **Deceptive Urgency:** Phrases like *"blocked today"*, *"suspended immediately"*, or *"act now"* create artificial panic.
                    • **Unverified Web Links:** Links using URL shorteners (e.g. `bit.ly`, `tinyurl`) or suspicious domain extensions (`.xyz`, `.top`, `.apk`).
                    • **Credential Harvesting:** Requests asking you to enter passwords, PINs, OTP codes, or update KYC details.
                    • **Personal Sender Numbers:** Official institutions sending text alerts from personal 10-digit mobile numbers.
                    
                    ### What You Should Do
                    1. Tap on any message in your Squish dashboard to see its **Detailed Evaluation Matrix**.
                    2. If you know the sender is authentic, tap **'Mark Safe'** to teach Squish your preferences.
                    
                    💡 **Safety Tip:** Legitimate banks and service providers never ask for secret OTPs or passwords over SMS.
                    """.trimIndent()
                }
            }
            lower.contains("is this bank message legitimate") || (lower.contains("bank") && lower.contains("legitimate")) -> {
                """
                ### How to Know if a Bank Message is Legitimate
                **Summary:** Genuine bank communications follow strict regulatory standards that scammers cannot replicate.
                
                ### Checklist for Real Bank Messages
                • **Sender ID:** Arrives from an official telecom header (e.g., `AD-HDFCBK`, `CP-ICICIB`), never a personal +91 mobile number.
                • **Account Masking:** Always masks account numbers, showing only the last 3 or 4 digits (e.g., `A/C XX4589`).
                • **No Pressure Links:** Standard debit/credit alerts do not demand you click a link to keep your account active.
                • **Protective Wording:** Includes warnings like *"Do not share this OTP with anyone, even bank staff."*
                
                ### Recommended Actions
                1. If a message threatens that your card or account is blocked unless you click a link, it is a scam.
                2. Open your official banking app directly to check your real account status.
                3. Call your bank using the phone number printed on the back of your card.
                
                💡 **Safety Tip:** Banks will NEVER ask you to download an APK file or install a remote access app.
                """.trimIndent()
            }
            lower.contains("what should i do right now") || (lower.contains("what should i do") && lower.contains("now")) -> {
                """
                ### Immediate Action Guide
                **Summary:** Take these straightforward steps to stay safe when you receive a suspicious message.
                
                ### Immediate Steps
                1. **Stop:** Do not click any links, download files, or dial phone numbers in the text.
                2. **Do Not Reply:** Replying confirms your number is active and invites more scam messages.
                3. **Check Squish:** Open the message in Squish to see the safety score and detailed breakdown.
                4. **Block the Sender:** Use your phone's default messaging app to block the sender number.
                
                💡 **Safety Tip:** If you already clicked a link and entered your password, immediately go to the official website and change your password.
                """.trimIndent()
            }
            lower.contains("how can i check this sender") || lower.contains("check sender") -> {
                """
                ### Checking a Message Sender
                **Summary:** Verify the sender's identity before trusting the contents of any text.
                
                ### Sender Verification Steps
                • **Format Check:** Registered institutions use alphanumeric sender codes (e.g., `VK-SBIINB`). If an alert comes from a standard 10-digit number (+91...), it is almost certainly fraudulent.
                • **Cross-Reference:** Search the phone number or sender ID on the company's verified website.
                • **Trusted Senders in Squish:** In Squish, you can mark verified senders as **'Trusted'** so legitimate family or service notifications are never falsely flagged.
                
                💡 **Safety Tip:** Scammers can sometimes attempt to spoof names, but they cannot impersonate verified banking apps.
                """.trimIndent()
            }
            lower.contains("click") && (lower.contains("link") || lower.contains("phishing")) -> {
                """
                ### What to Do If You Clicked a Suspicious Link
                **Summary:** Act quickly to minimize any security risk to your accounts.
                
                ### Step-by-Step Response
                1. **Close the Page Immediately:** Do not proceed or tap anything further on the site.
                2. **Did You Enter Credentials?** If you entered a password or PIN, open the official app or website on another device and **change your password immediately**.
                3. **Check for Downloads:** Look in your phone's **Downloads** folder. If an `.apk` file was downloaded, **delete it immediately without opening it**.
                4. **Enable 2-Factor Authentication:** Ensure your email, banking, and social accounts have two-factor authentication enabled.
                5. **Review Account Statements:** Keep an eye on your bank notifications for the next 48 hours.
                
                💡 **Safety Tip:** If you suspect unauthorized transactions, contact your bank within 24 hours to secure your funds under zero-liability policies.
                """.trimIndent()
            }
            lower.contains("quishing") || lower.contains("qr") -> {
                """
                ### Understanding QR Code Scams (Quishing)
                **Summary:** Quishing is a technique where fraudsters hide deceptive website links inside QR codes.
                
                ### How It Works
                • Scammers stick fake QR codes over legitimate ones at parking meters, restaurants, utility bills, or fuel stations.
                • When scanned, the QR code redirects your phone to a counterfeit payment or login page.
                
                ### How Squish Protects You
                • Use the **Scan QR** feature in Squish. Squish decodes the QR code without opening it in your browser.
                • Squish analyzes the destination URL with our on-device XGBoost machine learning model and alerts you before you visit.
                
                💡 **Safety Tip:** Always inspect physical QR code stickers to see if they are pasted over an existing code.
                """.trimIndent()
            }
            lower.contains("smishing") || lower.contains("sms") -> {
                """
                ### Understanding SMS Scams (Smishing)
                **Summary:** Smishing is text message phishing designed to trick you into sharing passwords, money, or sensitive data.
                
                ### Common Types of Smishing
                • **Delivery Scams:** *"Your package cannot be delivered, pay redelivery fee at [link]."*
                • **Bank & KYC Scams:** *"Your account will be suspended today, update your KYC at [link]."*
                • **Utility Cut-off Scams:** *"Electricity will be disconnected tonight, call [number] to pay."*
                • **Job & Lottery Scams:** *"Earn Rs. 5000/day working from home, deposit registration fee."*
                
                💡 **Safety Tip:** Genuine companies will never ask you to pay unexpected fees or update KYC through text message links.
                """.trimIndent()
            }
            lower.contains("otp") || lower.contains("pin") -> {
                """
                ### Protecting Your One-Time Password (OTP)
                **Summary:** Your OTP is the digital key to your money and personal accounts.
                
                ### Golden Rules
                • **Never share an OTP with ANYONE**, including callers claiming to be bank fraud managers, police officers, or telecom support.
                • Bank staff have direct system access to cancel fraud — they **never** need your secret OTP to help you.
                • **Entering your UPI PIN sends money.** You never need to enter a PIN to receive payments or refunds.
                
                💡 **Safety Tip:** If someone calls asking for an OTP to cancel a charge, hang up immediately. It is always a scam.
                """.trimIndent()
            }
            lower.contains("tip") -> {
                val tip = SecurityTipsProvider.getTodayTip()
                """
                ### Today's Security Tip: ${tip.title}
                **Summary:** ${tip.summary}
                
                ### Warning Signs to Watch For
                ${tip.warningSigns.joinToString("\n") { "• $it" }}
                
                ### Recommended Action
                ${tip.recommendedAction}
                """.trimIndent()
            }
            else -> {
                """
                ### Squish AI Cybersecurity Assistant
                **Summary:** I am your on-device AI protection assistant, analyzing text messages and QR codes locally to keep you safe from digital scams.
                
                ### How I Can Help You
                • **Analyze Messages:** Paste any suspicious text message to evaluate it with our TinyBERT model.
                • **Check Links:** Paste any web address to inspect it with our XGBoost URL classifier.
                • **Explain Alerts:** Tap 'Explain with AI' on any message in your history.
                • **Immediate Guidance:** Ask me what to do after clicking suspicious links or receiving fake bank texts.
                
                💡 **Quick Prompts to Try:**
                • *"Why was this message flagged?"*
                • *"Is this bank message legitimate?"*
                • *"What should I do right now?"*
                • *"How can I check this sender?"*
                """.trimIndent()
            }
        }

        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = answer
        )
    }
}
