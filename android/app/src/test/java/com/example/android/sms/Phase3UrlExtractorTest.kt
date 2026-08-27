package com.example.android.sms

import com.example.android.data.parser.UrlExtractor
import org.junit.Assert.*
import org.junit.Test

class Phase3UrlExtractorTest {

    @Test
    fun testSingleHttpsUrl() {
        val text = "Please verify your account at https://secure-bank-login.com immediately."
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(1, urls.size)
        assertEquals("https://secure-bank-login.com", urls[0])
    }

    @Test
    fun testSingleHttpUrl() {
        val text = "Click http://update-kyc.in to restore your access."
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(1, urls.size)
        assertEquals("http://update-kyc.in", urls[0])
    }

    @Test
    fun testMultipleUrls() {
        val text = "Main link: https://portal.com/login and secondary: http://backup.net/verify?id=123."
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://portal.com/login"))
        assertTrue(urls.contains("http://backup.net/verify?id=123"))
    }

    @Test
    fun testUrlWithQueryParams() {
        val text = "Your code is ready: https://auth.domain.com/token?user=1234&session=abc#ref"
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(1, urls.size)
        assertEquals("https://auth.domain.com/token?user=1234&session=abc#ref", urls[0])
    }

    @Test
    fun testIpBasedUrl() {
        val text = "Suspicious link: http://192.168.1.100:8080/phish"
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(1, urls.size)
        assertEquals("http://192.168.1.100:8080/phish", urls[0])
    }

    @Test
    fun testTextWithNoUrl() {
        val text = "Dear Customer, your OTP for transaction of Rs. 500 is 492014. Do not share with anyone."
        val urls = UrlExtractor.extractUrls(text)
        assertTrue(urls.isEmpty())
    }

    @Test
    fun testMalformedOrPunctuationAttachedUrl() {
        val text = "Visit http://my-bank.org/test."
        val urls = UrlExtractor.extractUrls(text)
        assertEquals(1, urls.size)
        assertEquals("http://my-bank.org/test", urls[0])
    }

    @Test
    fun testUrlNormalization() {
        val raw = "  www.paytm-secure.com/login.  "
        val normalized = UrlExtractor.normalizeUrl(raw)
        assertEquals("http://www.paytm-secure.com/login", normalized)
    }
}
