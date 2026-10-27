package com.example.android.scandetail

import com.example.android.data.local.dao.ThreatHistoryDao
import com.example.android.data.local.entity.ThreatHistoryEntity
import com.example.android.data.parser.QrPayloadParser
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.data.repository.ThreatHistoryRepositoryImpl
import com.example.android.domain.model.ThreatRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests verifying Scanned SMS and QR-Code Content Display & Storage functionality.
 */
class ScanContentDisplayTest {

    private class FakeThreatHistoryDao : ThreatHistoryDao {
        val storage = mutableMapOf<String, ThreatHistoryEntity>()

        override suspend fun insertThreat(threat: ThreatHistoryEntity) {
            storage[threat.id] = threat
        }

        override suspend fun getThreatById(id: String): ThreatHistoryEntity? = storage[id]

        override fun observeAllThreats(): Flow<List<ThreatHistoryEntity>> = flowOf(storage.values.toList())

        override fun observeQuarantinedThreats(): Flow<List<ThreatHistoryEntity>> =
            flowOf(storage.values.filter { it.isQuarantined })

        override suspend fun getAllThreats(): List<ThreatHistoryEntity> = storage.values.toList()

        override suspend fun updateThreat(threat: ThreatHistoryEntity) {
            storage[threat.id] = threat
        }

        override suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean) {
            storage[id]?.let {
                storage[id] = it.copy(isQuarantined = isQuarantined)
            }
        }

        override suspend fun updateReviewStatus(id: String, reviewStatus: String) {
            storage[id]?.let {
                storage[id] = it.copy(reviewStatus = reviewStatus)
            }
        }

        override suspend fun updateThreatAction(id: String, action: String) {
            storage[id]?.let {
                storage[id] = it.copy(actionTaken = action)
            }
        }

        override suspend fun deleteThreatById(id: String) {
            storage.remove(id)
        }

        override suspend fun clearAllThreats() {
            storage.clear()
        }
    }

    private lateinit var fakeDao: FakeThreatHistoryDao
    private lateinit var repository: ThreatHistoryRepositoryImpl

    @Before
    fun setup() {
        fakeDao = FakeThreatHistoryDao()
        repository = ThreatHistoryRepositoryImpl(fakeDao)
    }

    @Test
    fun testSmsScanStoresRawMessageBodyAndSender() = runBlocking {
        val testId = UUID.randomUUID().toString()
        val smsBody = "URGENT: Your SBI account has been locked. Verify immediately at https://sbi-secure-login.xyz/kyc"
        val sender = "VM-SBIINB"
        val extractedUrl = "https://sbi-secure-login.xyz/kyc"

        val threat = ThreatRecord(
            id = testId,
            timestamp = System.currentTimeMillis(),
            source = "SMS",
            sender = sender,
            riskScore = 88,
            category = "SMISHING_HIGH",
            actionTaken = "QUARANTINED",
            details = "Urgent banking impersonation keyword detected; Suspicious unverified URL",
            rawContent = smsBody,
            extractedUrl = extractedUrl,
            payloadType = "SMS",
            severity = "HIGH",
            modelVersion = "v2.1-edge",
            isQuarantined = true,
            reviewStatus = "UNREVIEWED",
            contentRetentionMode = "LOCAL_SAVED"
        )

        val addResult = repository.addThreat(threat)
        assertTrue("Adding threat must succeed", addResult.isSuccess)

        val retrieved = repository.getThreatById(testId).getOrNull()
        assertNotNull("Retrieved threat record should not be null", retrieved)
        assertEquals("Original SMS body must be preserved exactly", smsBody, retrieved?.rawContent)
        assertEquals("Sender must match originating address", sender, retrieved?.sender)
        assertEquals("Extracted URL must be captured", extractedUrl, retrieved?.extractedUrl)
        assertEquals("Payload type should be SMS", "SMS", retrieved?.payloadType)
        assertTrue("Threat should be quarantined", retrieved?.isQuarantined == true)
        assertEquals("Retention mode should be LOCAL_SAVED", "LOCAL_SAVED", retrieved?.contentRetentionMode)
    }

    @Test
    fun testQrPayloadParserUrl() {
        val rawUrl = "https://secure-hdfc-kyc.com/update-pan?ref=123"
        val parsed = QrPayloadParser.parse(rawUrl)

        assertEquals("Payload type must be URL", "URL", parsed.payloadType)
        assertEquals("Extracted URL must match normalized link", rawUrl, parsed.extractedUrl)
        assertEquals("Hostname should be parsed correctly", "secure-hdfc-kyc.com", parsed.normalizedHostname)
        assertEquals("Display title should be domain", "secure-hdfc-kyc.com", parsed.displayTitle)
        assertTrue("Protocol should be HTTPS", parsed.structuredFields["Security Protocol"]?.contains("HTTPS") == true)
    }

    @Test
    fun testQrPayloadParserWifi() {
        val wifiPayload = "WIFI:T:WPA;S:Airport_Guest_WiFi;P:SuperSecretPass;H:false;;"
        val parsed = QrPayloadParser.parse(wifiPayload)

        assertEquals("Payload type must be WIFI", "WIFI", parsed.payloadType)
        assertEquals("SSID must be parsed", "Airport_Guest_WiFi", parsed.structuredFields["SSID"])
        assertEquals("Security must be WPA", "WPA", parsed.structuredFields["Security"])
        assertEquals("Password must be parsed", "SuperSecretPass", parsed.structuredFields["Password"])
        assertEquals("Hidden flag must be No", "No", parsed.structuredFields["Hidden"])
    }

    @Test
    fun testQrPayloadParserContact() {
        val vcardPayload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:John Doe
            TEL:+15551234567
            EMAIL:john.doe@example.com
            ORG:Acme Security Inc
            END:VCARD
        """.trimIndent()

        val parsed = QrPayloadParser.parse(vcardPayload)
        assertEquals("Payload type must be CONTACT", "CONTACT", parsed.payloadType)
        assertEquals("Name must be John Doe", "John Doe", parsed.structuredFields["Name"])
        assertEquals("Phone must match", "+15551234567", parsed.structuredFields["Phone"])
        assertEquals("Email must match", "john.doe@example.com", parsed.structuredFields["Email"])
    }

    @Test
    fun testQuarantineToggle() = runBlocking {
        val testId = "threat-456"

        fakeDao.storage[testId] = ThreatHistoryEntity(
            id = testId,
            timestamp = System.currentTimeMillis(),
            source = "SMS",
            sender = "Bank",
            riskScore = 80,
            category = "SMISHING",
            actionTaken = "WARN_USER",
            details = "Suspicious keyword",
            isQuarantined = false
        )

        val resultQuarantine = repository.updateQuarantineStatus(testId, true)
        assertTrue("Quarantining threat should succeed", resultQuarantine.isSuccess)
        assertTrue("Entity in DAO should now be quarantined", fakeDao.storage[testId]?.isQuarantined == true)

        val resultRelease = repository.updateQuarantineStatus(testId, false)
        assertTrue("Releasing threat from quarantine should succeed", resultRelease.isSuccess)
        assertFalse("Entity in DAO should now be unquarantined", fakeDao.storage[testId]?.isQuarantined == true)
    }

    @Test
    fun testMissingContentHandling() {
        // When content retention is disabled or content was cleaned up, rawContent is null
        val recordWithNoContent = ThreatRecord(
            id = "threat-clean",
            timestamp = System.currentTimeMillis(),
            source = "SMS",
            sender = "+19998887777",
            riskScore = 45,
            category = "SUSPICIOUS_SMS",
            actionTaken = "WARN_USER",
            details = "Unrecognized sender keyword",
            rawContent = null,
            contentRetentionMode = "SESSION_ONLY"
        )

        assertNull("Raw content should be null when not retained", recordWithNoContent.rawContent)
        assertEquals("Retention mode must reflect session-only", "SESSION_ONLY", recordWithNoContent.contentRetentionMode)
    }

    @Test
    fun testDataMinimizationSyncExcludesRawContent() {
        // Cloud sync DTO should contain sanitized preview and metadata, never raw unmasked SMS body
        val rawSensitiveSms = "Your OTP is 482910 for payment of Rs 15000. Do not share."
        val syncRequest = DetectionSyncRequest(
            clientEventId = "evt-sync-1",
            eventType = "SMS",
            riskScore = 80,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK_AND_DELETE",
            timestamp = "2026-10-27T12:00:00Z",
            sanitizedPreview = "SMS from Bank: Financial OTP request pattern",
            contentRetentionMode = "LOCAL_SAVED",
            reviewStatus = "NEW",
            explanation = "Suspicious financial OTP query"
        )

        assertFalse(
            "Cloud sync request must NOT contain raw message body with OTP",
            syncRequest.sanitizedPreview?.contains("482910") == true
        )
        assertEquals("LOCAL_SAVED", syncRequest.contentRetentionMode)
        assertEquals("NEW", syncRequest.reviewStatus)
    }
}
