package com.example.android.qr

import com.example.android.data.parser.UrlFeatureExtractor
import org.junit.Assert.*
import org.junit.Test

class Phase4UrlFeatureExtractorTest {

    @Test
    fun testHttpsStandardUrlFeatureExtraction() {
        val url = "https://www.example.com/account/login?ref=123"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertEquals("https", features.scheme)
        assertEquals("www.example.com", features.hostname)
        assertEquals("example.com", features.domain)
        assertEquals("/account/login", features.path)
        assertEquals("ref=123", features.query)
        assertTrue(features.isHttps)
        assertTrue(features.hasQuery)
        assertFalse(features.isIpHostname)
        assertEquals(1, features.subdomainCount) // 'www' is 1 subdomain
        assertFalse(features.hasUnusualPort)
        assertFalse(features.isPunycode)
        assertTrue(features.suspiciousCharacteristics.isEmpty())
    }

    @Test
    fun testInsecureHttpUrlDetection() {
        val url = "http://verify-bank.com"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertFalse(features.isHttps)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("Missing HTTPS") })
    }

    @Test
    fun testIpAddressHostnameDetection() {
        val url = "http://192.168.1.100/login"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertTrue(features.isIpHostname)
        assertEquals("192.168.1.100", features.hostname)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("IP address") })
    }

    @Test
    fun testExcessiveSubdomainsDetection() {
        val url = "https://a.b.c.d.suspicious-domain.com/path"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertTrue(features.subdomainCount > 2)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("Excessive subdomains") })
    }

    @Test
    fun testUnusualPortDetection() {
        val url = "http://example.com:8443/auth"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertTrue(features.hasUnusualPort)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("Non-standard network port") })
    }

    @Test
    fun testPunycodeDetection() {
        val url = "https://xn--e1afmkfd.xn--p1ai/home"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertTrue(features.isPunycode)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("Punycode") })
    }

    @Test
    fun testSuspiciousSymbolsAndHyphens() {
        val url = "http://secure-login-account-verify-update.com/user@admin"
        val features = UrlFeatureExtractor.extractFeatures(url)

        assertTrue(features.hasSuspiciousSymbols)
        assertTrue(features.suspiciousCharacteristics.any { it.contains("Suspicious symbols") })
    }

    @Test
    fun testEmptyOrMalformedUrl() {
        val features = UrlFeatureExtractor.extractFeatures("")
        assertNotNull(features)
        assertFalse(features.isHttps)
    }
}
