package com.example.android.data.local

import com.example.android.data.local.entity.*
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import org.junit.Assert.*
import org.junit.Test

class Phase2RoomUnitTest {

    @Test
    fun testUserEntityCreation() {
        val user = UserEntity(
            uid = "user_123",
            email = "user@test.com",
            displayName = "Test User",
            isGuest = false
        )
        assertEquals("user_123", user.uid)
        assertEquals("user@test.com", user.email)
        assertFalse(user.isGuest)
    }

    @Test
    fun testUserEntityDoesNotStorePlaintextPassword() {
        val fields = UserEntity::class.java.declaredFields.map { it.name }
        assertFalse("User entity must not contain password field", fields.contains("password"))
        assertFalse("User entity must not contain authSecret field", fields.contains("authSecret"))
    }

    @Test
    fun testThreatHistoryEntityMapping() {
        val threat = ThreatHistoryEntity(
            id = "t_1",
            timestamp = 1000L,
            source = "SMS",
            sender = "+18005550199",
            riskScore = 95,
            category = "Smishing",
            actionTaken = "BLOCKED",
            details = "Bank phishing text"
        )

        assertEquals("t_1", threat.id)
        assertEquals(95, threat.riskScore)
        assertEquals("BLOCKED", threat.actionTaken)
    }

    @Test
    fun testSmsAnalysisEntityDataMinimization() {
        val fields = SMSAnalysisEntity::class.java.declaredFields.map { it.name }
        assertFalse("SMS analysis must NOT store raw body text for privacy", fields.contains("body"))
        assertFalse("SMS analysis must NOT store raw message text", fields.contains("rawText"))
    }

    @Test
    fun testQrAnalysisEntityCreation() {
        val qr = QRAnalysisEntity(
            id = "qr_100",
            timestamp = 2000L,
            rawContent = "https://phishing-site.xyz/login",
            extractedUrl = "https://phishing-site.xyz/login",
            domain = "phishing-site.xyz",
            riskScore = 90,
            isQuishing = true,
            confidence = 0.95f
        )
        assertTrue(qr.isQuishing)
        assertEquals("phishing-site.xyz", qr.domain)
    }

    @Test
    fun testFeedbackEntityDefaults() {
        val fb = FeedbackEntity(
            id = "fb_1",
            threatId = "t_1",
            timestamp = 3000L,
            userCategory = "False Positive",
            comment = "Legitimate bank notification"
        )
        assertFalse(fb.isSubmitted)
    }

    @Test
    fun testModelVersionEntityCreation() {
        val mv = ModelVersionEntity(
            modelType = "TINYBERT",
            version = "1.0.0",
            sha256Checksum = "a1b2c3d4e5",
            isActive = true
        )
        assertEquals("TINYBERT", mv.modelType)
        assertTrue(mv.isActive)
    }
}
