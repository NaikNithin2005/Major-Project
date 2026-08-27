package com.example.android.data.parser

import com.example.android.domain.model.UrlFeatures
import java.net.URI
import java.util.regex.Pattern

/**
 * Utility for extracting comprehensive structural features from URLs for preliminary analysis
 * and future Phase 5 XGBoost classification.
 */
object UrlFeatureExtractor {

    private val IP_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    private val SUSPICIOUS_SYMBOLS_PATTERN = Pattern.compile("[@%]|^//|//[a-zA-Z0-9_-]+/.*//")

    fun extractFeatures(rawUrl: String): UrlFeatures {
        val normalized = UrlExtractor.normalizeUrl(rawUrl)
        var scheme = "http"
        var hostname = ""
        var path = "/"
        var query: String? = null
        var port = -1

        try {
            val uri = URI(normalized)
            scheme = uri.scheme?.lowercase() ?: "http"
            hostname = uri.host?.lowercase() ?: ""
            path = uri.path ?: "/"
            query = uri.query
            port = uri.port
        } catch (e: Exception) {
            // Fallback manual parsing for malformed URLs
            if (normalized.contains("://")) {
                val parts = normalized.split("://", limit = 2)
                scheme = parts[0].lowercase()
                val hostAndPath = parts[1].split("/", limit = 2)
                hostname = hostAndPath[0].split("?")[0].lowercase()
                if (hostAndPath.size > 1) {
                    val pathAndQuery = hostAndPath[1].split("?", limit = 2)
                    path = "/" + pathAndQuery[0]
                    if (pathAndQuery.size > 1) {
                        query = pathAndQuery[1]
                    }
                }
            } else {
                hostname = normalized.split("/", "?")[0].lowercase()
            }
        }

        // Clean port from hostname if present
        if (hostname.contains(":")) {
            val hostPortParts = hostname.split(":")
            hostname = hostPortParts[0]
            if (port == -1) {
                port = hostPortParts[1].toIntOrNull() ?: -1
            }
        }

        val domain = extractBaseDomain(hostname)
        val isHttps = scheme == "https"
        val isIp = IP_PATTERN.matcher(hostname).matches()
        val dots = hostname.count { it == '.' }
        val subdomainCount = if (isIp || dots <= 1) 0 else dots - 1
        val hasSuspiciousSymbols = SUSPICIOUS_SYMBOLS_PATTERN.matcher(normalized).find() || hostname.count { it == '-' } > 2
        val hasUnusualPort = port != -1 && port != 80 && port != 443
        val isPunycode = hostname.contains("xn--", ignoreCase = true)

        val characteristics = mutableListOf<String>()
        if (!isHttps) characteristics.add("Insecure connection (Missing HTTPS)")
        if (isIp) characteristics.add("IP address used as hostname")
        if (subdomainCount > 2) characteristics.add("Excessive subdomains ($subdomainCount levels)")
        if (hasSuspiciousSymbols) characteristics.add("Suspicious symbols or hyphenation")
        if (hasUnusualPort) characteristics.add("Non-standard network port ($port)")
        if (isPunycode) characteristics.add("Punycode/IDN homograph indicator")
        if (normalized.length > 100) characteristics.add("Abnormally long URL length (${normalized.length} characters)")

        return UrlFeatures(
            url = normalized,
            scheme = scheme,
            domain = domain,
            hostname = hostname,
            path = path,
            query = query,
            isHttps = isHttps,
            urlLength = normalized.length,
            hostnameLength = hostname.length,
            pathLength = path.length,
            hasQuery = !query.isNullOrBlank(),
            isIpHostname = isIp,
            subdomainCount = subdomainCount,
            hasSuspiciousSymbols = hasSuspiciousSymbols,
            hasUnusualPort = hasUnusualPort,
            isPunycode = isPunycode,
            suspiciousCharacteristics = characteristics
        )
    }

    private fun extractBaseDomain(hostname: String): String {
        if (hostname.isBlank() || IP_PATTERN.matcher(hostname).matches()) return hostname
        val parts = hostname.split(".")
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            hostname
        }
    }
}
