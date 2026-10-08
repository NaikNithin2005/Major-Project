package com.example.android.domain.context

/**
 * Structured evidence output produced by the Context Correlation Engine.
 */
data class ContextFinding(
    val checkName: String,
    val matched: Boolean,
    val severity: String,       // INFO, LOW, MEDIUM, HIGH, CRITICAL
    val description: String,
    val evidence: String,
    val source: String = "CONTEXT"
)
