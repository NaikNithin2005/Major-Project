package com.example.android.sms

import com.example.android.data.parser.SmsParser
import com.example.android.domain.model.RawSms
import org.junit.Assert.*
import org.junit.Test

class Phase3SmsParserTest {

    private val parser = SmsParser()

    @Test
    fun testNormalSmsParsing() {
        val raw = RawSms(
            messageId = "1001",
            sender = "AD-HDFCBK",
            body = "Dear Customer, your HDFC account 4910 has been blocked. Update at https://hdfc-verify.com immediately.",
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)

        assertEquals("AD-HDFCBK", parsed.safeSender)
        assertEquals("Dear Customer, your HDFC account 4910 has been blocked. Update at https://hdfc-verify.com immediately.", parsed.safeBody)
        assertEquals(1, parsed.extractedUrls.size)
        assertEquals("https://hdfc-verify.com", parsed.extractedUrls[0])
        assertTrue(parsed.detectedBrands.contains("HDFC"))
    }

    @Test
    fun testEmptySmsBody() {
        val raw = RawSms(
            messageId = "1002",
            sender = "12345",
            body = "   ",
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)
        assertEquals("12345", parsed.safeSender)
        assertEquals("", parsed.safeBody)
        assertTrue(parsed.extractedUrls.isEmpty())
        assertFalse(parsed.containsOtp)
    }

    @Test
    fun testMissingSender() {
        val raw = RawSms(
            messageId = "1003",
            sender = "",
            body = "Test message body without sender",
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)
        assertEquals("UNKNOWN_SENDER", parsed.safeSender)
    }

    @Test
    fun testUnicodeAndSpecialCharactersSms() {
        val raw = RawSms(
            messageId = "1004",
            sender = "+919876543210",
            body = "Dear subscriber, your ₹5000 cashback is ready! Claim at http://cashback.in 🎉 !!!",
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)
        assertEquals(1, parsed.extractedUrls.size)
        assertTrue(parsed.specialCharRatio > 0.0f)
    }

    @Test
    fun testLongSmsPayload() {
        val longBody = "URGENT NOTICE ".repeat(100) + " Visit http://scam.site to claim."
        val raw = RawSms(
            messageId = "1005",
            sender = "VK-SBIINB",
            body = longBody,
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)
        assertEquals(1, parsed.extractedUrls.size)
        assertEquals("http://scam.site", parsed.extractedUrls[0])
    }

    @Test
    fun testOtpAndPhoneNumberDetection() {
        val raw = RawSms(
            messageId = "1006",
            sender = "AD-PAYTM",
            body = "Your Paytm OTP is 849201. Call +919876543210 if you did not request this.",
            timestamp = 1690000000000L
        )

        val parsed = parser.parse(raw)
        assertTrue(parsed.containsOtp)
        assertTrue(parsed.containsPhoneNumbers)
        assertTrue(parsed.detectedBrands.contains("PAYTM"))
    }
}
