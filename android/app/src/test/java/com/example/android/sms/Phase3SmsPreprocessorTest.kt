package com.example.android.sms

import com.example.android.data.parser.ParsedSmsData
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.domain.model.RawSms
import org.junit.Assert.*
import org.junit.Test

class Phase3SmsPreprocessorTest {

    private val preprocessor = SmsPreprocessor()

    @Test
    fun testTextNormalization() {
        val input = "  Your ACCOUNT   is SUSPENDED \n\r immediately!  "
        val normalized = preprocessor.normalize(input)
        assertEquals("your account is suspended immediately!", normalized)
    }

    @Test
    fun testTokenization() {
        val text = "your account is suspended immediately"
        val tokens = preprocessor.tokenize(text)
        assertEquals(5, tokens.size)
        assertEquals(listOf("your", "account", "is", "suspended", "immediately"), tokens)
    }

    @Test
    fun testSuspiciousKeywordExtraction() {
        val parsed = ParsedSmsData(
            rawSms = RawSms(sender = "TEST", body = "Your bank account has been blocked and suspended. Click to verify."),
            safeSender = "TEST",
            safeBody = "Your bank account has been blocked and suspended. Click to verify.",
            extractedUrls = listOf("http://verify.com"),
            containsOtp = false,
            containsPhoneNumbers = false,
            detectedBrands = listOf("BANK"),
            specialCharRatio = 0.05f
        )

        val processed = preprocessor.preprocess(parsed)
        assertTrue(processed.evidence.detectedKeywords.contains("blocked"))
        assertTrue(processed.evidence.detectedKeywords.contains("suspended"))
        assertTrue(processed.evidence.detectedKeywords.contains("verify"))
    }

    @Test
    fun testUrgencyIndicatorExtraction() {
        val parsed = ParsedSmsData(
            rawSms = RawSms(sender = "TEST", body = "Action required immediately! Account expires today."),
            safeSender = "TEST",
            safeBody = "Action required immediately! Account expires today.",
            extractedUrls = emptyList(),
            containsOtp = false,
            containsPhoneNumbers = false,
            detectedBrands = emptyList(),
            specialCharRatio = 0.02f
        )

        val processed = preprocessor.preprocess(parsed)
        assertTrue(processed.evidence.urgencyIndicators.contains("immediately"))
        assertTrue(processed.evidence.urgencyIndicators.contains("today"))
    }

    @Test
    fun testSenderPatternClassification() {
        assertEquals("ALPHANUMERIC_SHORTCODE", preprocessor.classifySenderPattern("AD-HDFCBK"))
        assertEquals("INTERNATIONAL_PHONE", preprocessor.classifySenderPattern("+919876543210"))
        assertEquals("LOCAL_PHONE", preprocessor.classifySenderPattern("9876543210"))
        assertEquals("NUMERIC_SHORTCODE", preprocessor.classifySenderPattern("56161"))
        assertEquals("UNKNOWN", preprocessor.classifySenderPattern(""))
    }
}
