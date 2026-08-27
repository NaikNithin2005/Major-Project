package com.example.android.data.parser

import com.example.android.domain.model.RawSms
import java.util.regex.Pattern

/**
 * Safe SMS Parser for extracting structural metadata, URLs, phone numbers, and indicators from incoming SMS.
 */
class SmsParser {

    private val PHONE_NUMBER_PATTERN = Pattern.compile("\\+?\\d{10,13}|\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b")
    private val OTP_PATTERN = Pattern.compile("(?i)\\b(?:otp|one time password|code|verification code|pin|passcode|is \\d{4,8}|code:?\\s*\\d{4,8})\\b")
    private val BANK_BRAND_PATTERN = Pattern.compile("(?i)\\b(?:sbi|hdfc|icici|axis|kotak|pnb|bob|paytm|phonepe|gpay|yono|amaznn|flipkart|netflix|customs|income tax|cert-in|vi|airtel|jio)\\b")

    fun parse(rawSms: RawSms): ParsedSmsData {
        val safeSender = rawSms.sender.ifBlank { "UNKNOWN_SENDER" }
        val safeBody = rawSms.body.trim()
        val extractedUrls = UrlExtractor.extractUrls(safeBody)
        
        val containsPhoneNumbers = PHONE_NUMBER_PATTERN.matcher(safeBody).find()
        val containsOtp = OTP_PATTERN.matcher(safeBody).find()
        
        val matchedBrands = mutableListOf<String>()
        val brandMatcher = BANK_BRAND_PATTERN.matcher(safeBody)
        while (brandMatcher.find()) {
            matchedBrands.add(brandMatcher.group().uppercase())
        }

        val specialCharsCount = safeBody.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        val specialCharRatio = if (safeBody.isNotEmpty()) specialCharsCount.toFloat() / safeBody.length else 0.0f

        return ParsedSmsData(
            rawSms = rawSms,
            safeSender = safeSender,
            safeBody = safeBody,
            extractedUrls = extractedUrls,
            containsOtp = containsOtp,
            containsPhoneNumbers = containsPhoneNumbers,
            detectedBrands = matchedBrands.distinct(),
            specialCharRatio = specialCharRatio
        )
    }
}

/**
 * Parsed SMS metadata object returned by [SmsParser].
 */
data class ParsedSmsData(
    val rawSms: RawSms,
    val safeSender: String,
    val safeBody: String,
    val extractedUrls: List<String>,
    val containsOtp: Boolean,
    val containsPhoneNumbers: Boolean,
    val detectedBrands: List<String>,
    val specialCharRatio: Float
)
