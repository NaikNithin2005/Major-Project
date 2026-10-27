package com.example.android.util

import android.content.Intent
import android.net.Uri
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Validated request extracted from an incoming Android Intent (Deep Link, App Link, or Sharesheet).
 */
data class DeepLinkRequest(
    val rawInput: String,
    val normalizedUrl: String,
    val domain: String,
    val source: String, // "DEEP_LINK", "APP_LINK", "SHARESHEET", "QR_CODE", "MANUAL"
    val isSsrfRisk: Boolean = false,
    val ssrfReason: String? = null,
    val isValid: Boolean = true,
    val validationError: String? = null
)

/**
 * Robust handler and validator for incoming URL intents, App Links, custom deep links,
 * and Android Sharesheet text payloads.
 */
object DeepLinkHandler {

    private const val MAX_URL_LENGTH = 2048

    private val DANGEROUS_SCHEMES = setOf(
        "javascript", "file", "data", "content", "intent", "blob", "vbscript", "about"
    )

    private val PRIVATE_IP_PREFIXES = listOf(
        "127.", "10.", "192.168.", "0.0.0.0", "169.254."
    )

    /**
     * Extracts and validates a target URL from an incoming Android [Intent].
     * Supports:
     * - ACTION_VIEW with scheme "squish" (e.g. squish://scan?url=https://...)
     * - ACTION_VIEW with scheme "http" or "https" (Web link / App Link)
     * - ACTION_SEND with MIME type "text/plain" (Android Sharesheet)
     */
    fun parseIntent(intent: Intent?): DeepLinkRequest? {
        if (intent == null) return null

        val action = intent.action ?: return null

        return when (action) {
            Intent.ACTION_VIEW -> handleActionView(intent)
            Intent.ACTION_SEND -> handleActionSend(intent)
            else -> null
        }
    }

    private fun handleActionView(intent: Intent): DeepLinkRequest? {
        val dataUriString = intent.dataString ?: intent.data?.toString() ?: return null
        return parseUriString(dataUriString)
    }

    private fun handleActionSend(intent: Intent): DeepLinkRequest? {
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: return null

        if (sharedText.isBlank()) return null

        // Extract URL from shared text (which may contain extra messages like "Check out this link: https://...")
        val extractedUrls = UrlExtractor.extractUrls(sharedText)
        val targetUrl = if (extractedUrls.isNotEmpty()) {
            extractedUrls.first()
        } else {
            sharedText.trim()
        }

        return validateAndBuildRequest(targetUrl, "SHARESHEET")
    }

    /**
     * Parses and decodes a URI string directly without needing an Android [Intent].
     * Supports squish://, https://squish.security/scan?url=..., and direct URLs.
     */
    fun parseUriString(uriString: String): DeepLinkRequest? {
        val trimmed = uriString.trim()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()
        var targetUrl: String? = null
        var source = "DEEP_LINK"

        if (lower.startsWith("squish://")) {
            val idx = trimmed.indexOf("url=")
            val targetIdx = if (idx != -1) idx else trimmed.indexOf("target=")
            val uriIdx = if (targetIdx != -1) targetIdx else trimmed.indexOf("uri=")
            if (uriIdx != -1) {
                val paramKey = if (idx != -1) "url=" else if (targetIdx != -1) "target=" else "uri="
                val rawParam = trimmed.substring(uriIdx + paramKey.length).split("&")[0]
                targetUrl = tryDecode(rawParam)
            }
            source = "DEEP_LINK"
        } else if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                val uri = URI(trimmed)
                val host = uri.host?.lowercase() ?: ""
                val path = uri.path?.lowercase() ?: ""
                if ((host == "squish.security" || host == "squish-shield.app" || host.contains("squish")) &&
                    (path.contains("/scan") || path.contains("/check") || path.contains("/verify"))
                ) {
                    val query = uri.rawQuery ?: ""
                    for (param in query.split("&")) {
                        val kv = param.split("=")
                        if (kv.size == 2 && (kv[0] == "url" || kv[0] == "target")) {
                            targetUrl = tryDecode(kv[1])
                            break
                        }
                    }
                    source = "APP_LINK"
                }
            } catch (e: Exception) {
                // Fallback to direct raw url
            }
            if (targetUrl.isNullOrBlank()) {
                targetUrl = trimmed
                source = "INCOMING_URL"
            }
        } else {
            targetUrl = trimmed
            source = "RAW_INPUT"
        }

        if (targetUrl.isNullOrBlank()) return null
        return validateAndBuildRequest(targetUrl, source)
    }

    /**
     * Validates URL scheme, length, encoding, and SSRF restrictions.
     */
    fun validateAndBuildRequest(rawUrl: String, source: String): DeepLinkRequest {
        val trimmed = rawUrl.trim()

        if (trimmed.isBlank()) {
            return DeepLinkRequest(
                rawInput = rawUrl,
                normalizedUrl = "",
                domain = "",
                source = source,
                isValid = false,
                validationError = "URL payload is empty"
            )
        }

        if (trimmed.length > MAX_URL_LENGTH) {
            return DeepLinkRequest(
                rawInput = rawUrl,
                normalizedUrl = trimmed.take(100) + "...",
                domain = "Invalid",
                source = source,
                isValid = false,
                validationError = "URL length exceeds maximum limit of $MAX_URL_LENGTH characters"
            )
        }

        // Check for dangerous protocol schemes
        val lowerTrimmed = trimmed.lowercase()
        for (scheme in DANGEROUS_SCHEMES) {
            if (lowerTrimmed.startsWith("$scheme:")) {
                return DeepLinkRequest(
                    rawInput = rawUrl,
                    normalizedUrl = trimmed,
                    domain = "Prohibited Scheme",
                    source = source,
                    isValid = false,
                    validationError = "Dangerous or unsupported protocol scheme '$scheme:' detected. Refusing to process."
                )
            }
        }

        // Normalize URL to ensure valid http/https scheme
        val normalized = UrlExtractor.normalizeUrl(trimmed)
        val features = UrlFeatureExtractor.extractFeatures(normalized)
        val host = features.hostname.lowercase()

        // Check for SSRF targets / internal network probes
        var isSsrf = false
        var ssrfReason: String? = null

        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") {
            isSsrf = true
            ssrfReason = "Target points to local loopback interface ($host)"
        } else if (PRIVATE_IP_PREFIXES.any { host.startsWith(it) }) {
            isSsrf = true
            ssrfReason = "Target points to internal or private IP address space ($host)"
        } else if (host.startsWith("172.")) {
            // Check 172.16.0.0 - 172.31.255.255
            val parts = host.split(".")
            if (parts.size >= 2) {
                val secondOctet = parts[1].toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) {
                    isSsrf = true
                    ssrfReason = "Target points to RFC 1918 private IP range (172.16.0.0/12)"
                }
            }
        }

        return DeepLinkRequest(
            rawInput = rawUrl,
            normalizedUrl = normalized,
            domain = features.domain.ifBlank { features.hostname.ifBlank { "Unknown Domain" } },
            source = source,
            isSsrfRisk = isSsrf,
            ssrfReason = ssrfReason,
            isValid = true,
            validationError = null
        )
    }

    private fun tryDecode(text: String): String {
        return try {
            URLDecoder.decode(text, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            text
        }
    }
}
