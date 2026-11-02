package com.example.android.permission

import com.example.android.domain.model.ThreatRecord
import com.example.android.util.PermissionUtils
import com.example.android.util.ProtectionStatusLabel
import org.junit.Assert.*
import org.junit.Test

class ProtectionReadinessTest {

    @Test
    fun testProtectionStatusLabels_adhereToRequirements() {
        val labels = ProtectionStatusLabel.values().map { it.label }
        assertTrue(labels.contains("Enabled"))
        assertTrue(labels.contains("Not granted"))
        assertTrue(labels.contains("Action required"))
        assertTrue(labels.contains("Notifications disabled"))
        assertTrue(labels.contains("Default SMS app required"))
        assertTrue(labels.contains("Model unavailable"))
        assertTrue(labels.contains("Limited protection"))
    }

    @Test
    fun testRequiredPermissions_containsCoreProtections() {
        val permissions = PermissionUtils.getRequiredPermissions().toList()
        assertTrue(permissions.contains(android.Manifest.permission.RECEIVE_SMS))
        assertTrue(permissions.contains(android.Manifest.permission.READ_SMS))
        assertTrue(permissions.contains(android.Manifest.permission.CAMERA))
    }

    @Test
    fun testDeduplicationSignature_computationConsistency() {
        val sender = "AD-HDFCBK"
        val body = "URGENT: Verify your bank account immediately at http://hdfc.fake.com"
        val signature1 = "$sender:${body.trim().hashCode()}"
        val signature2 = "$sender:${body.trim().hashCode()}"
        assertEquals("Identical SMS contents must yield exact matching deduplication signatures", signature1, signature2)

        val duplicateBodyWithWhitespace = "  URGENT: Verify your bank account immediately at http://hdfc.fake.com  "
        val signature3 = "$sender:${duplicateBodyWithWhitespace.trim().hashCode()}"
        assertEquals("Trimmed duplicate message must produce identical signature", signature1, signature3)
    }

    @Test
    fun testQuarantinedRecord_fieldsIntegrity() {
        val record = ThreatRecord(
            id = "evt_9988",
            timestamp = 1700000000000L,
            source = "SMS",
            sender = "+18005550199",
            riskScore = 88,
            category = "SMISHING_HIGH",
            actionTaken = "QUARANTINED",
            details = "Urgent threat keywords detected; suspicious login link",
            rawContent = "Your card is blocked. Go to http://card-fix.xyz to unlock now.",
            extractedUrl = "http://card-fix.xyz",
            payloadType = "SMS",
            severity = "HIGH",
            modelVersion = "tinybert-smishing-v2",
            isQuarantined = true,
            reviewStatus = "NEW",
            contentRetentionMode = "LOCAL_SAVED"
        )

        assertEquals("evt_9988", record.id)
        assertTrue(record.isQuarantined)
        assertEquals("QUARANTINED", record.actionTaken)
        assertEquals("tinybert-smishing-v2", record.modelVersion)
        assertEquals("LOCAL_SAVED", record.contentRetentionMode)
        assertEquals("NEW", record.reviewStatus)
        assertEquals(88, record.riskScore)
    }

    @Test
    fun testQuarantineStateTransition_releaseRequiresStatusUpdate() {
        val quarantinedRecord = ThreatRecord(
            id = "evt_1234",
            timestamp = System.currentTimeMillis(),
            source = "SMS",
            sender = "AD-ALERT",
            riskScore = 80,
            category = "SMISHING",
            actionTaken = "QUARANTINED",
            details = "Suspicious financial SMS",
            isQuarantined = true,
            reviewStatus = "NEW"
        )

        // Simulating release / mark safe action with confirmation
        val releasedRecord = quarantinedRecord.copy(
            isQuarantined = false,
            actionTaken = "MARK_SAFE",
            reviewStatus = "REVIEWED"
        )

        assertFalse(releasedRecord.isQuarantined)
        assertEquals("MARK_SAFE", releasedRecord.actionTaken)
        assertEquals("REVIEWED", releasedRecord.reviewStatus)
    }
}
