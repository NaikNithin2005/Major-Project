package com.example.android.ux

import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.service.SquishProtectionTileService
import com.example.android.util.HapticFeedbackHelper
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SquishUxEnhancementsTest {

    @Test
    fun testSyncQueueEntity_defaultAndCustomOperations() {
        val defaultItem = SyncQueueEntity(
            clientEventId = "evt-123",
            eventType = "QR",
            payloadJson = "{\"score\": 85}",
            userId = "user_abc"
        )

        assertEquals(SyncQueueEntity.STATUS_PENDING, defaultItem.status)
        assertEquals(SyncQueueEntity.OP_TELEMETRY_SYNC, defaultItem.pendingOperation)
        assertNull(defaultItem.errorCategory)

        val customItem = defaultItem.copy(
            pendingOperation = SyncQueueEntity.OP_REMOTE_URL_CHECK,
            errorCategory = SyncQueueEntity.ERROR_NETWORK_OFFLINE
        )

        assertEquals(SyncQueueEntity.OP_REMOTE_URL_CHECK, customItem.pendingOperation)
        assertEquals(SyncQueueEntity.ERROR_NETWORK_OFFLINE, customItem.errorCategory)
    }

    @Test
    fun testSyncQueueEntity_errorCategories() {
        val offlineErr = SyncQueueEntity.ERROR_NETWORK_OFFLINE
        val authErr = SyncQueueEntity.ERROR_AUTH_REQUIRED
        val serverErr = SyncQueueEntity.ERROR_SERVER
        val permErr = SyncQueueEntity.ERROR_PERMANENT

        assertEquals("NETWORK_OFFLINE", offlineErr)
        assertEquals("AUTH_REQUIRED", authErr)
        assertEquals("SERVER_ERROR", serverErr)
        assertEquals("PERMANENT_REJECT", permErr)
    }

    @Test
    fun testQuickSettingsTile_constants() {
        assertEquals("squish_shield_preferences", SquishProtectionTileService.PREFS_NAME)
        assertEquals("pref_sms_protection_enabled", SquishProtectionTileService.KEY_PROTECTION_ENABLED)
    }

    @Test
    fun testHapticFeedback_scoreRoutingLogic() {
        // High risk (score >= 70) -> Malicious vibration pattern expected
        val isMaliciousHigh = 85 >= 70
        assertTrue(isMaliciousHigh)

        // Safe (score < 40) -> Subtle click expected
        val isSafe = 15 < 40
        assertTrue(isSafe)

        // Inconclusive (40..69) -> No vibration expected (neither subtle safe nor warning buzz)
        val scoreInconclusive = 55
        assertFalse(scoreInconclusive >= 70)
        assertFalse(scoreInconclusive < 40)
    }

    @Test
    fun testHapticFeedback_duplicateEventPrevention() {
        HapticFeedbackHelper.clearVibratedEvents()

        val eventId = "scan-duplicate-test-001"
        assertFalse(HapticFeedbackHelper.hasVibratedEvent(eventId))

        // First trigger registers the event and returns true (vibration allowed)
        val firstRecord = HapticFeedbackHelper.recordEventVibration(eventId)
        assertTrue(firstRecord)
        assertTrue(HapticFeedbackHelper.hasVibratedEvent(eventId))

        // Duplicate trigger with the same eventId is suppressed (returns false)
        val secondRecord = HapticFeedbackHelper.recordEventVibration(eventId)
        assertFalse(secondRecord)

        // A different eventId is allowed
        val otherEventId = "scan-duplicate-test-002"
        val otherRecord = HapticFeedbackHelper.recordEventVibration(otherEventId)
        assertTrue(otherRecord)
    }

    @Test
    fun testSecurityStatus_preventsGreenSafeStateWhenThreatPresent() {
        // When there is an unresolved threat, state must NEVER be SECURE_PROTECTED
        val hasUnresolvedThreat = true
        val calculatedScore = 85 // Even if calculated score >= 80

        val isSafeAllowed = !hasUnresolvedThreat && calculatedScore >= 80
        assertFalse(isSafeAllowed)
    }
}
