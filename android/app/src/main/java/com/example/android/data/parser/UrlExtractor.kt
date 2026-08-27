package com.example.android.data.parser

import java.util.regex.Pattern

/**
 * Utility for extracting and normalizing URLs from raw text content.
 * Designed to prevent false positives while identifying http, https, www, IP, and query link formats.
 */
object UrlExtractor {

    // Strict URL matching requiring explicit scheme, www prefix, or valid standalone domain token without spaces
    private val EXPLICIT_URL_PATTERN: Pattern = Pattern.compile(
        "(?i)\\b(?:https?://|www\\.)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|" +
        "\\b[a-zA-Z0-9\\-]+\\.(?:com|org|net|edu|gov|mil|biz|info|in|co|us|uk|io|ai|app|dev|link|xyz|top|site|online|tech|store|cc|tk)(?::\\d{1,5})?(?:/[^\\s()<>]*)?",
        Pattern.CASE_INSENSITIVE
    )

    private val IP_PATTERN: Pattern = Pattern.compile(
        "\\b(?:https?://)?(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/[^\\s()<>]*)?\\b"
    )

    /**
     * Extracts all unique URLs found within [text].
     */
    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()

        val results = mutableSetOf<String>()

        val matcher = EXPLICIT_URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawMatch = matcher.group()
            val cleaned = cleanUrlMatch(rawMatch)
            if (cleaned.isNotBlank() && cleaned.length > 3) {
                results.add(cleaned)
            }
        }

        val ipMatcher = IP_PATTERN.matcher(text)
        while (ipMatcher.find()) {
            val rawMatch = ipMatcher.group()
            val cleaned = cleanUrlMatch(rawMatch)
            if (cleaned.isNotBlank()) {
                results.add(cleaned)
            }
        }

        return results.toList()
    }

    /**
     * Normalizes a URL for feature extraction (e.g. adding scheme if missing, trim trailing punctuation).
     */
    fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        trimmed = trimmed.replace(Regex("[.,;:!?)\\]]+$"), "")

        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed = "http://$trimmed"
        }
        return trimmed
    }

    private fun cleanUrlMatch(raw: String): String {
        var cleaned = raw.trim()
        cleaned = cleaned.replace(Regex("[.,;:!?)\\]]+$"), "")
        return cleaned
    }
}
