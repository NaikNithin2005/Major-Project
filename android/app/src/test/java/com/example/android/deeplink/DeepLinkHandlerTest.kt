package com.example.android.deeplink

import com.example.android.util.DeepLinkHandler
import org.junit.Assert.*
import org.junit.Test

class DeepLinkHandlerTest {

    @Test
    fun testSquishCustomSchemeScanUrl() {
        val uri = "squish://scan?url=https%3A%2F%2Fexample.com%2Flogin"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("DEEP_LINK", request!!.source)
        assertEquals("https://example.com/login", request.normalizedUrl)
        assertEquals("example.com", request.domain)
        assertTrue(request.isValid)
        assertFalse(request.isSsrfRisk)
    }

    @Test
    fun testSquishCustomSchemeCheckTarget() {
        val uri = "squish://check?target=https%3A%2F%2Fbank-secure-update.com%2Fverify"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("DEEP_LINK", request!!.source)
        assertEquals("https://bank-secure-update.com/verify", request.normalizedUrl)
        assertEquals("bank-secure-update.com", request.domain)
        assertTrue(request.isValid)
    }

    @Test
    fun testAppLinkSquishSecurityScan() {
        val uri = "https://squish.security/scan?url=https%3A%2F%2Fsecure.chase.com%2Fauth"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("APP_LINK", request!!.source)
        assertEquals("https://secure.chase.com/auth", request.normalizedUrl)
        assertEquals("chase.com", request.domain)
        assertTrue(request.isValid)
    }

    @Test
    fun testAppLinkSquishShieldCheck() {
        val uri = "https://squish-shield.app/check?url=https%3A%2F%2Fpaypal-verify.tk"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("APP_LINK", request!!.source)
        assertEquals("https://paypal-verify.tk", request.normalizedUrl)
        assertEquals("paypal-verify.tk", request.domain)
        assertTrue(request.isValid)
    }

    @Test
    fun testDirectHttpUrl() {
        val uri = "http://legitimate-service.org/info"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("INCOMING_URL", request!!.source)
        assertEquals("http://legitimate-service.org/info", request.normalizedUrl)
        assertEquals("legitimate-service.org", request.domain)
        assertTrue(request.isValid)
    }

    @Test
    fun testDirectHttpsUrl() {
        val uri = "https://github.com/google"
        val request = DeepLinkHandler.parseUriString(uri)

        assertNotNull(request)
        assertEquals("INCOMING_URL", request!!.source)
        assertEquals("https://github.com/google", request.normalizedUrl)
        assertEquals("github.com", request.domain)
        assertTrue(request.isValid)
    }

    @Test
    fun testDangerousSchemeJavascript() {
        val dangerousUri = "javascript:alert(document.cookie)"
        val request = DeepLinkHandler.validateAndBuildRequest(dangerousUri, "INCOMING_URL")

        assertFalse(request.isValid)
        assertNotNull(request.validationError)
        assertTrue(request.validationError!!.contains("Dangerous or unsupported protocol scheme"))
    }

    @Test
    fun testDangerousSchemeFile() {
        val dangerousUri = "file:///etc/passwd"
        val request = DeepLinkHandler.validateAndBuildRequest(dangerousUri, "INCOMING_URL")

        assertFalse(request.isValid)
        assertNotNull(request.validationError)
        assertTrue(request.validationError!!.contains("Dangerous or unsupported protocol scheme"))
    }

    @Test
    fun testDangerousSchemeData() {
        val dangerousUri = "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg=="
        val request = DeepLinkHandler.validateAndBuildRequest(dangerousUri, "INCOMING_URL")

        assertFalse(request.isValid)
        assertNotNull(request.validationError)
        assertTrue(request.validationError!!.contains("Dangerous or unsupported protocol scheme"))
    }

    @Test
    fun testDangerousSchemeIntent() {
        val dangerousUri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        val request = DeepLinkHandler.validateAndBuildRequest(dangerousUri, "INCOMING_URL")

        assertFalse(request.isValid)
        assertNotNull(request.validationError)
        assertTrue(request.validationError!!.contains("Dangerous or unsupported protocol scheme"))
    }

    @Test
    fun testSsrfLoopbackLocalhost() {
        val url = "http://localhost:8080/admin/delete"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertNotNull(request.ssrfReason)
        assertTrue(request.ssrfReason!!.contains("loopback"))
    }

    @Test
    fun testSsrfLoopback127() {
        val url = "http://127.0.0.1:9000/internal-api"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertNotNull(request.ssrfReason)
        assertTrue(request.ssrfReason!!.contains("loopback"))
    }

    @Test
    fun testSsrfCloudMetadata169() {
        val url = "http://169.254.169.254/latest/meta-data/"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertNotNull(request.ssrfReason)
        assertTrue(request.ssrfReason!!.contains("internal or private IP"))
    }

    @Test
    fun testSsrfPrivateNetwork10() {
        val url = "http://10.200.1.25/admin"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertTrue(request.ssrfReason!!.contains("internal or private IP"))
    }

    @Test
    fun testSsrfPrivateNetwork192168() {
        val url = "http://192.168.1.1/router-login"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertTrue(request.ssrfReason!!.contains("internal or private IP"))
    }

    @Test
    fun testSsrfPrivateNetwork172ClassB() {
        val url = "http://172.16.0.1/status"
        val request = DeepLinkHandler.validateAndBuildRequest(url, "INCOMING_URL")

        assertTrue(request.isValid)
        assertTrue(request.isSsrfRisk)
        assertTrue(request.ssrfReason!!.contains("RFC 1918"))
    }

    @Test
    fun testMaxUrlLengthExceeded() {
        val veryLongUrl = "https://example.com/" + "a".repeat(2100)
        val request = DeepLinkHandler.validateAndBuildRequest(veryLongUrl, "INCOMING_URL")

        assertFalse(request.isValid)
        assertNotNull(request.validationError)
        assertTrue(request.validationError!!.contains("exceeds maximum limit"))
    }

    @Test
    fun testBlankUrlRejection() {
        val blank = "   "
        val request = DeepLinkHandler.validateAndBuildRequest(blank, "INCOMING_URL")

        assertFalse(request.isValid)
        assertEquals("URL payload is empty", request.validationError)
    }
}
