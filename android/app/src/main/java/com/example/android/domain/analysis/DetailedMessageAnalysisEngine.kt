package com.example.android.domain.analysis

import com.example.android.domain.model.ThreatRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production engine for Phase 2 message evaluation.
 * Breaks down any SMS or QR scan into an understandable, multi-dimensional security report:
 * 1. Message Information & Category
 * 2. Detection Result (Looks Safe, Needs Attention, Dangerous)
 * 3. Why Flagged (Plain English, real indicator causes)
 * 4. Detailed Breakdown Table (Sender, Urgency, Links, Personal Info, Financial Action, Overall)
 * 5. Contextual Recommendations (Banking, Delivery, Government, Job, Dangerous, Safe)
 */
object DetailedMessageAnalysisEngine {

    data class DetailedMessageAnalysis(
        val originalContent: String,
        val sender: String,
        val formattedTimestamp: String,
        val detectedCategory: String,
        val verdict: DetectionVerdict,
        val whyFlaggedExplanation: String,
        val senderVerification: EvaluationRow,
        val languageUrgency: EvaluationRow,
        val linkSafety: EvaluationRow,
        val personalInfoRequest: EvaluationRow,
        val financialAction: EvaluationRow,
        val overallAssessment: EvaluationRow,
        val contextualRecommendations: List<String>
    )

    enum class DetectionVerdict(val label: String, val level: String) {
        LOOKS_SAFE("Looks Safe", "SAFE"),
        NEEDS_ATTENTION("Needs Attention", "ATTENTION"),
        DANGEROUS("Dangerous", "DANGEROUS")
    }

    data class EvaluationRow(
        val dimension: String,
        val status: String,
        val description: String,
        val isFavorable: Boolean
    )

    private val URGENCY_REGEX = Regex(
        "(?i)\\b(immediately|urgent|urgently|within\\s+\\d+\\s*(hours?|mins?|minutes?)|expire[sd]?\\s+(today|soon)|blocked\\s+today|suspended\\s+(today|immediately)|deactivated\\s+today|act\\s+now|immediate\\s+action|final\\s+notice|penalty)\\b"
    )

    private val CREDENTIAL_REGEX = Regex(
        "(?i)\\b((enter|submit|provide)\\s+(your\\s+)?(password|pin|cvv|otp|credentials)|share\\s+otp|send\\s+(your\\s+)?(otp|pin|password)|login\\s+to\\s+verify|(verify|update)\\s+(your\\s+)?(kyc|pan|aadhaar|account|identity|details)|update\\s+kyc)\\b"
    )

    private val PAYMENT_DEMAND_REGEX = Regex(
        "(?i)\\b(send\\s+money|transfer\\s+(inr|rs|\\$|amount)|pay\\s+(\\w+\\s+)?(fee|fine|penalty|charges|registration|redelivery)|deposit\\s+(money|advance)|recharge\\s+wallet|unfreeze\\s+account|redelivery\\s+fee)\\b"
    )

    private val BANK_KEYWORDS = Regex(
        "(?i)\\b(bank|account|a/c|debited|credited|inr|rs\\.?|upi|neft|imps|rtgs|balance|atm|card|otp)\\b"
    )

    private val DELIVERY_KEYWORDS = Regex(
        "(?i)\\b(delivery|courier|package|parcel|shipment|fedex|dhl|usps|blue\\s*dart|track\\s*(order|package)|shipping\\s*fee|redelivery)\\b"
    )

    private val GOV_KEYWORDS = Regex(
        "(?i)\\b(income\\s*tax|irs|tax\\s*refund|challan|court|police|arrest|warrant|traffic\\s*police|govt|government)\\b"
    )

    private val JOB_KEYWORDS = Regex(
        "(?i)\\b(job\\s*offer|part\\s*time\\s*job|work\\s*from\\s*home|daily\\s*income|hiring\\s*now|recruitment|salary\\s*per\\s*day)\\b"
    )

    private val SUSPICIOUS_DOMAINS = Regex(
        "(?i)(bit\\.ly|tinyurl\\.com|is\\.gd|rb\\.gy|t\\.co|cutt\\.ly|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|\\.top|\\.xyz|\\.buzz|\\.club|\\.loan|\\.work|\\.rest|apk|download-app)"
    )

    fun analyze(threat: ThreatRecord, isSenderTrusted: Boolean = false): DetailedMessageAnalysis {
        val raw = threat.rawContent ?: threat.details
        val sender = threat.sender
        val isQr = threat.source.contains("QR", ignoreCase = true)
        val extractedUrl = threat.extractedUrl

        // Format Date
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(threat.timestamp))

        // 1. Identify Category
        val category = when {
            isQr -> "QR Code Payload"
            raw.contains("otp", ignoreCase = true) && raw.contains("verification", ignoreCase = true) -> "One-Time Password (OTP)"
            DELIVERY_KEYWORDS.containsMatchIn(raw) -> "Delivery & Shipping Notice"
            GOV_KEYWORDS.containsMatchIn(raw) -> "Government / Tax Notice"
            JOB_KEYWORDS.containsMatchIn(raw) -> "Job Offer / Recruitment"
            BANK_KEYWORDS.containsMatchIn(raw) || Regex("^[A-Z]{2}-[A-Z]{2,6}$").matches(sender) -> "Bank Transaction / Notification"
            else -> "General Text Message"
        }

        // 2. Identify Verdict
        val isHigh = threat.riskScore >= 70 ||
                threat.category.contains("HIGH", ignoreCase = true) ||
                threat.category.contains("SMISHING", ignoreCase = true) ||
                threat.category.contains("QUISHING", ignoreCase = true) ||
                threat.category.contains("MALICIOUS", ignoreCase = true)

        val isMedium = !isHigh && (
                threat.riskScore >= 40 ||
                threat.category.contains("MEDIUM", ignoreCase = true) ||
                threat.category.contains("SUSPICIOUS", ignoreCase = true) ||
                threat.category.contains("SPAM", ignoreCase = true)
        )

        val verdict = when {
            isHigh -> DetectionVerdict.DANGEROUS
            isMedium -> DetectionVerdict.NEEDS_ATTENTION
            else -> DetectionVerdict.LOOKS_SAFE
        }

        // 3. Signal Extractions
        val hasUrgency = URGENCY_REGEX.containsMatchIn(raw)
        val isProtectiveOtp = raw.contains("do not share", ignoreCase = true) ||
                raw.contains("never share", ignoreCase = true) ||
                raw.contains("not to be shared", ignoreCase = true)
        val hasCredentialHarvest = !isProtectiveOtp && CREDENTIAL_REGEX.containsMatchIn(raw)
        val hasPaymentDemand = PAYMENT_DEMAND_REGEX.containsMatchIn(raw)

        val urlPresent = !extractedUrl.isNullOrBlank() || raw.contains("http://", ignoreCase = true) || raw.contains("https://", ignoreCase = true)
        val hasSuspiciousUrl = urlPresent && (
                SUSPICIOUS_DOMAINS.containsMatchIn(extractedUrl ?: raw) ||
                raw.contains(".apk", ignoreCase = true) ||
                raw.contains("bit.ly", ignoreCase = true) ||
                raw.contains("tinyurl", ignoreCase = true)
        )

        // 4. Detailed Evaluation Rows
        val senderRow = when {
            isSenderTrusted -> EvaluationRow(
                dimension = "Sender Verification",
                status = "Trusted Sender",
                description = "You marked this sender as safe and trusted in your profile.",
                isFavorable = true
            )
            Regex("^[A-Z]{2}-[A-Z]{2,6}$").matches(sender) -> EvaluationRow(
                dimension = "Sender Verification",
                status = "Official Sender Header",
                description = "Sender format matches registered enterprise telecom sender ID standards.",
                isFavorable = true
            )
            Regex("^(\\+?\\d{10,14})$").matches(sender) && (category.contains("Bank") || category.contains("Government")) -> EvaluationRow(
                dimension = "Sender Verification",
                status = "Suspicious Personal Number",
                description = "Claims to be an institution but sent from an ordinary mobile number.",
                isFavorable = false
            )
            sender.isBlank() || sender.equals("Unknown", ignoreCase = true) -> EvaluationRow(
                dimension = "Sender Verification",
                status = "Unknown Origin",
                description = "Sender identity could not be verified.",
                isFavorable = false
            )
            else -> EvaluationRow(
                dimension = "Sender Verification",
                status = "Standard Sender",
                description = "Standard sender contact address.",
                isFavorable = true
            )
        }

        val urgencyRow = if (hasUrgency) {
            EvaluationRow(
                dimension = "Language Urgency",
                status = "Artificial Urgency Detected",
                description = "Uses panic triggers (e.g. 'immediately', 'blocked today') to pressure hasty action.",
                isFavorable = false
            )
        } else {
            EvaluationRow(
                dimension = "Language Urgency",
                status = "Normal Calm Tone",
                description = "No psychological panic or artificial urgency tactics detected.",
                isFavorable = true
            )
        }

        val linkRow = when {
            !urlPresent -> EvaluationRow(
                dimension = "Link Safety",
                status = "No Links Present",
                description = "Message does not contain links to external web destinations.",
                isFavorable = true
            )
            hasSuspiciousUrl -> EvaluationRow(
                dimension = "Link Safety",
                status = "Suspicious or Unofficial Link",
                description = "Contains an unverified, masked, or suspicious web domain.",
                isFavorable = false
            )
            else -> EvaluationRow(
                dimension = "Link Safety",
                status = "External Link Present",
                description = "Contains an external web address. Verify origin before tapping.",
                isFavorable = verdict == DetectionVerdict.LOOKS_SAFE
            )
        }

        val personalInfoRow = if (hasCredentialHarvest) {
            EvaluationRow(
                dimension = "Personal Info Request",
                status = "Demands Sensitive Data",
                description = "Asks for passwords, PIN, OTP, or card details. Legitimate services never ask this via SMS.",
                isFavorable = false
            )
        } else if (isProtectiveOtp) {
            EvaluationRow(
                dimension = "Personal Info Request",
                status = "Protective Notice Included",
                description = "Includes legitimate security reminder advising you never to disclose your OTP.",
                isFavorable = true
            )
        } else {
            EvaluationRow(
                dimension = "Personal Info Request",
                status = "No Sensitive Requests",
                description = "Does not request passwords, OTPs, or private credentials.",
                isFavorable = true
            )
        }

        val financialRow = if (hasPaymentDemand) {
            EvaluationRow(
                dimension = "Financial Action Required",
                status = "Immediate Payment Demanded",
                description = "Demands an upfront fee, transfer, or money deposit to unblock accounts or prizes.",
                isFavorable = false
            )
        } else if (category.contains("Bank")) {
            EvaluationRow(
                dimension = "Financial Action Required",
                status = "Informational Notice",
                description = "Standard transaction receipt or balance alert with no payment demands.",
                isFavorable = true
            )
        } else {
            EvaluationRow(
                dimension = "Financial Action Required",
                status = "None Required",
                description = "No financial transaction or monetary transfer requested.",
                isFavorable = true
            )
        }

        val overallRow = EvaluationRow(
            dimension = "Overall Assessment",
            status = verdict.label,
            description = when (verdict) {
                DetectionVerdict.LOOKS_SAFE -> "Content passed all safety checks. No malicious indicators found."
                DetectionVerdict.NEEDS_ATTENTION -> "Some unusual elements detected. Review carefully before interacting."
                DetectionVerdict.DANGEROUS -> "High scam risk identified. Do not click links or share any details."
            },
            isFavorable = verdict == DetectionVerdict.LOOKS_SAFE
        )

        // 5. Build Why Flagged Explanation in Clear, Everyday English
        val flaggedReasons = mutableListOf<String>()
        if (hasUrgency) flaggedReasons.add("creates artificial urgency by threatening account suspension or immediate deadlines")
        if (hasCredentialHarvest) flaggedReasons.add("requests confidential information such as passwords, OTPs, or card details")
        if (hasSuspiciousUrl) flaggedReasons.add("contains an unverified or shortened link that does not belong to the claimed organization")
        if (hasPaymentDemand) flaggedReasons.add("demands an upfront money transfer or payment under false pretenses")
        if (!senderRow.isFavorable) flaggedReasons.add("was sent from an unverified or suspicious sender address")

        val whyFlagged = when {
            verdict == DetectionVerdict.LOOKS_SAFE ->
                "This message was checked using on-device security intelligence and verified safe. It shows no signs of deceptive urgency, credential harvesting, or dangerous links."
            flaggedReasons.isNotEmpty() ->
                "This message was flagged because it ${flaggedReasons.joinToString(", and ")}. Legitimate organizations do not request sensitive actions through unsolicited messages."
            threat.details.isNotBlank() ->
                threat.details.replace(";", ". ")
            else ->
                "This message exhibited characteristics frequently associated with social engineering and phishing campaigns."
        }

        // 6. Contextual Recommendations
        val recommendations = when {
            category.contains("Bank") || category.contains("OTP") -> listOf(
                "Never share your one-time password (OTP) or PIN with anyone, even if they claim to be a bank official.",
                "If you are concerned about your account, open your official mobile banking app directly or visit your local branch.",
                "Call your bank using the official phone number printed on the back of your debit or credit card, never the number in this message."
            )
            category.contains("Delivery") -> listOf(
                "Check your recent delivery orders inside your official shopping or carrier application.",
                "Legitimate courier companies will never ask for unexpected fees or payments via text message to release a package.",
                "Do not open tracking links in text messages unless you were specifically expecting an update from this courier."
            )
            category.contains("Government") -> listOf(
                "Government agencies communicate official matters through postal mail or verified government web portals.",
                "Never pay traffic fines, taxes, or court penalties through links received in text messages.",
                "Verify this notice by visiting the official government department website independently."
            )
            category.contains("Job") -> listOf(
                "Legitimate employers never offer high-paying jobs through unsolicited text messages.",
                "Real companies never demand registration fees or equipment advance payments before employment.",
                "Search the hiring organization on official career websites before responding."
            )
            verdict == DetectionVerdict.DANGEROUS -> listOf(
                "Do not click any web links or open attachments in this message.",
                "Do not reply to this text or call back any phone numbers mentioned in it.",
                "Delete this message or keep it safely isolated in Squish."
            )
            verdict == DetectionVerdict.NEEDS_ATTENTION -> listOf(
                "Exercise caution before clicking any links or following instructions.",
                "Contact the sender independently through a verified official channel to confirm validity.",
                "Do not provide personal information, passwords, or payment cards."
            )
            else -> listOf(
                "Squish found no security threats or scam indicators in this content.",
                "You can interact with this message normally. Standard caution is always recommended with unfamiliar contacts."
            )
        }

        return DetailedMessageAnalysis(
            originalContent = raw,
            sender = sender.ifBlank { "Unknown Origin" },
            formattedTimestamp = formattedDate,
            detectedCategory = category,
            verdict = verdict,
            whyFlaggedExplanation = whyFlagged,
            senderVerification = senderRow,
            languageUrgency = urgencyRow,
            linkSafety = linkRow,
            personalInfoRequest = personalInfoRow,
            financialAction = financialRow,
            overallAssessment = overallRow,
            contextualRecommendations = recommendations
        )
    }
}
