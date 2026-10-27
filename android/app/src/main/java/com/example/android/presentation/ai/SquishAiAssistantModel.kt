package com.example.android.presentation.ai

data class SecurityVerdictCard(
    val verdict: String, // "SAFE", "SUSPICIOUS", "MALICIOUS"
    val severity: String, // "CRITICAL", "HIGH", "MEDIUM", "LOW", "SAFE"
    val score: Int,
    val confidence: Float,
    val category: String,
    val whyReasons: List<String>,
    val recommendedActions: List<String>,
    val rawPayload: String? = null
)

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val verdictCard: SecurityVerdictCard? = null,
    val isAnalyzing: Boolean = false
)

enum class AssistantContextType {
    GENERAL,
    ALERT_EXPLANATION,
    THREAT_RECORD,
    SCANNER_RESULT
}

data class AssistantContext(
    val type: AssistantContextType = AssistantContextType.GENERAL,
    val eventId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val details: String? = null,
    val severity: String? = null,
    val payload: String? = null
)
