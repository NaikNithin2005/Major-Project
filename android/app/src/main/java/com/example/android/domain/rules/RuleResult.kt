package com.example.android.domain.rules

/**
 * Structured evidence output produced by deterministic rule triggers.
 */
data class RuleResult(
    val id: String,
    val category: String,
    val severity: String,       // INFO, LOW, MEDIUM, HIGH, CRITICAL
    val description: String,
    val scoreContribution: Float,
    val evidence: String,
    val source: String = "RULE"
)
