package com.example.android.data.parser

import java.net.URI
import java.util.Locale

/**
 * Structured parsed QR code payload representation.
 */
data class QrParsedPayload(
    val payloadType: String,         // "URL", "WIFI", "CONTACT", "EMAIL", "SMS", "GEO", "PHONE", "TEXT"
    val displayTitle: String,
    val rawPayload: String,
    val extractedUrl: String? = null,
    val normalizedHostname: String? = null,
    val structuredFields: Map<String, String> = emptyMap()
)

object QrPayloadParser {

    fun parse(rawPayload: String): QrParsedPayload {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) {
            return QrParsedPayload(
                payloadType = "EMPTY",
                displayTitle = "Empty QR Payload",
                rawPayload = rawPayload
            )
        }

        // 1. Wi-Fi Configuration (e.g., WIFI:T:WPA;S:MyNetwork;P:Secret123;H:false;;)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val fields = parseWifi(trimmed)
            val ssid = fields["SSID"] ?: "Unknown Network"
            return QrParsedPayload(
                payloadType = "WIFI",
                displayTitle = "Wi-Fi: $ssid",
                rawPayload = rawPayload,
                structuredFields = fields
            )
        }

        // 2. Contact Information (vCard or MeCard)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) ||
            trimmed.startsWith("MECARD:", ignoreCase = true)
        ) {
            val fields = parseContact(trimmed)
            val name = fields["Name"] ?: "Contact Card"
            return QrParsedPayload(
                payloadType = "CONTACT",
                displayTitle = "Contact: $name",
                rawPayload = rawPayload,
                structuredFields = fields
            )
        }

        // 3. Email (mailto: or MATMSG:)
        if (trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("MATMSG:", ignoreCase = true)
        ) {
            val fields = parseEmail(trimmed)
            val recipient = fields["To"] ?: "Email"
            return QrParsedPayload(
                payloadType = "EMAIL",
                displayTitle = "Email to: $recipient",
                rawPayload = rawPayload,
                structuredFields = fields
            )
        }

        // 4. SMS (smsto: or sms:)
        if (trimmed.startsWith("smsto:", ignoreCase = true) ||
            trimmed.startsWith("sms:", ignoreCase = true)
        ) {
            val fields = parseSms(trimmed)
            val recipient = fields["Phone"] ?: "SMS"
            return QrParsedPayload(
                payloadType = "SMS",
                displayTitle = "SMS to: $recipient",
                rawPayload = rawPayload,
                structuredFields = fields
            )
        }

        // 5. Telephone (tel:)
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val number = trimmed.removePrefix("tel:").removePrefix("TEL:")
            return QrParsedPayload(
                payloadType = "PHONE",
                displayTitle = "Phone: $number",
                rawPayload = rawPayload,
                structuredFields = mapOf("Phone Number" to number)
            )
        }

        // 6. Geographic Location (geo:)
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val coords = trimmed.removePrefix("geo:").removePrefix("GEO:")
            return QrParsedPayload(
                payloadType = "GEO",
                displayTitle = "Coordinates: $coords",
                rawPayload = rawPayload,
                structuredFields = mapOf("Coordinates" to coords)
            )
        }

        // 7. URL Check (explicit scheme or domain-like)
        val isExplicitUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("ftp://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true)

        val extractedUrls = UrlExtractor.extractUrls(trimmed)
        if (isExplicitUrl || (extractedUrls.isNotEmpty() && !trimmed.contains("\n"))) {
            val targetUrl = if (isExplicitUrl) {
                UrlExtractor.normalizeUrl(trimmed)
            } else {
                UrlExtractor.normalizeUrl(extractedUrls.first())
            }

            val hostname = extractHostname(targetUrl)
            val scheme = if (targetUrl.startsWith("https://", ignoreCase = true)) "HTTPS (Encrypted)" else "HTTP (Insecure / Unencrypted)"

            val fields = mapOf(
                "Full URL" to targetUrl,
                "Hostname" to hostname,
                "Security Protocol" to scheme
            )

            return QrParsedPayload(
                payloadType = "URL",
                displayTitle = hostname.ifBlank { "Web URL" },
                rawPayload = rawPayload,
                extractedUrl = targetUrl,
                normalizedHostname = hostname,
                structuredFields = fields
            )
        }

        // 8. Plain Text Fallback
        return QrParsedPayload(
            payloadType = "TEXT",
            displayTitle = "Plain Text",
            rawPayload = rawPayload,
            structuredFields = mapOf("Text Content" to trimmed)
        )
    }

    private fun extractHostname(url: String): String {
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url)
            uri.host?.lowercase(Locale.ROOT) ?: url.substringBefore("/").substringBefore("?")
        } catch (e: Exception) {
            url.substringBefore("/").substringBefore("?")
        }
    }

    private fun parseWifi(payload: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val content = payload.removePrefix("WIFI:").removePrefix("wifi:")
        val tokens = content.split(";")
        for (token in tokens) {
            if (token.isBlank()) continue
            val parts = token.split(":", limit = 2)
            if (parts.size == 2) {
                when (parts[0].uppercase(Locale.ROOT)) {
                    "S" -> map["SSID"] = parts[1]
                    "T" -> map["Security"] = if (parts[1].isBlank() || parts[1].equals("nopass", true)) "None (Open)" else parts[1]
                    "P" -> map["Password"] = parts[1]
                    "H" -> map["Hidden"] = if (parts[1].equals("true", true)) "Yes" else "No"
                }
            }
        }
        return map
    }

    private fun parseContact(payload: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (payload.startsWith("MECARD:", ignoreCase = true)) {
            val content = payload.removePrefix("MECARD:").removePrefix("mecard:")
            val tokens = content.split(";")
            for (token in tokens) {
                val parts = token.split(":", limit = 2)
                if (parts.size == 2) {
                    when (parts[0].uppercase(Locale.ROOT)) {
                        "N" -> map["Name"] = parts[1]
                        "TEL" -> map["Phone"] = parts[1]
                        "EMAIL" -> map["Email"] = parts[1]
                        "ORG" -> map["Organization"] = parts[1]
                        "URL" -> map["Website"] = parts[1]
                    }
                }
            }
        } else {
            // vCard parsing
            val lines = payload.lines()
            for (line in lines) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].uppercase(Locale.ROOT).substringBefore(";")
                    when (key) {
                        "FN", "N" -> if (!map.containsKey("Name")) map["Name"] = parts[1].replace(";", " ").trim()
                        "TEL" -> map["Phone"] = parts[1].trim()
                        "EMAIL" -> map["Email"] = parts[1].trim()
                        "ORG" -> map["Organization"] = parts[1].replace(";", " ").trim()
                        "TITLE" -> map["Title"] = parts[1].trim()
                    }
                }
            }
        }
        return map
    }

    private fun parseEmail(payload: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (payload.startsWith("mailto:", ignoreCase = true)) {
            val rest = payload.removePrefix("mailto:").removePrefix("MAILTO:")
            val parts = rest.split("?", limit = 2)
            map["To"] = parts[0]
            if (parts.size == 2) {
                val queryParams = parts[1].split("&")
                for (qp in queryParams) {
                    val kv = qp.split("=", limit = 2)
                    if (kv.size == 2) {
                        when (kv[0].lowercase(Locale.ROOT)) {
                            "subject" -> map["Subject"] = java.net.URLDecoder.decode(kv[1], "UTF-8")
                            "body" -> map["Body"] = java.net.URLDecoder.decode(kv[1], "UTF-8")
                        }
                    }
                }
            }
        } else if (payload.startsWith("MATMSG:", ignoreCase = true)) {
            val content = payload.removePrefix("MATMSG:").removePrefix("matmsg:")
            val tokens = content.split(";")
            for (token in tokens) {
                val parts = token.split(":", limit = 2)
                if (parts.size == 2) {
                    when (parts[0].uppercase(Locale.ROOT)) {
                        "TO" -> map["To"] = parts[1]
                        "SUB" -> map["Subject"] = parts[1]
                        "BODY" -> map["Body"] = parts[1]
                    }
                }
            }
        }
        return map
    }

    private fun parseSms(payload: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val rest = payload.replace(Regex("^(sms|smsto):", RegexOption.IGNORE_CASE), "")
        val parts = rest.split(":", limit = 2)
        map["Phone"] = parts[0]
        if (parts.size == 2) {
            map["Message"] = parts[1]
        }
        return map
    }
}
