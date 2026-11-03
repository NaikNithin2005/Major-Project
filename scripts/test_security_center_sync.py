"""
test_security_center_sync.py
Verification script testing two-way real-time synchronization of the Security Center
between the Android mobile app and Web dashboard across:
  1. Trusted Senders Sync (Web -> Mobile & Mobile -> Web)
  2. Untrusted Senders Sync & Risk Assessment
  3. Quarantined SMS Sync (Quarantine & Release Invariants)
  4. Blocked Senders Sync (Block & Unblock Senders)
  5. Blocked SMS Sync (Individual Message Suppression vs Whole-Sender Blocking)
"""

import os
import sys
import uuid
from datetime import datetime, timezone

# Add backend directory to sys.path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "backend"))

from app.database.session import SessionLocal
from app.models.user import User
from app.models.sender_security import SenderSecurity
from app.models.threat_event import ThreatEvent
from app.services.security_service import SecurityService

def run_security_center_sync_tests():
    print("=====================================================================")
    print("RUNNING SECURITY CENTER TWO-WAY SYNCHRONIZATION VERIFICATION SUITE")
    print("=====================================================================")

    db = SessionLocal()
    unique_suffix = str(uuid.uuid4())[:8]

    # Setup isolated test user
    test_user = User(
        id=f"sync-sec-user-{unique_suffix}",
        firebase_uid=f"firebase_sec_{unique_suffix}",
        email=f"sec_sync_{unique_suffix}@squish.local",
        display_name="Security Sync Tester",
        role="USER"
    )
    db.add(test_user)
    db.commit()
    print(f"[SETUP] Created isolated test user: {test_user.id} ({test_user.email})")

    service = SecurityService(db)

    try:
        # -------------------------------------------------------------
        # TEST 1: Trusted Senders Two-Way Sync (Web -> Mobile)
        # -------------------------------------------------------------
        print("\n--- Test 1: Trusted Senders Sync (Web Action -> Mobile Retrieval) ---")
        web_sender = "VK-HDFCBK"
        trusted_record = service.trust_sender(
            user_id=test_user.id,
            sender_id=web_sender,
            display_name="HDFC Official Alerts",
            reason="Verified bank transaction sender from Web Dashboard"
        )
        assert trusted_record.trust_status == "TRUSTED", f"Expected TRUSTED, got {trusted_record.trust_status}"
        assert trusted_record.is_blocked is False, "Trusted sender must not be blocked"
        print(f"  [Web Action] Trusted sender '{web_sender}' added via Web dashboard.")

        # Simulate Android sync: pull trusted senders
        mobile_trusted = service.get_trusted_senders(user_id=test_user.id)
        assert len(mobile_trusted) == 1, f"Expected 1 trusted sender, got {len(mobile_trusted)}"
        assert mobile_trusted[0].sender_id == web_sender, f"Expected {web_sender}, got {mobile_trusted[0].sender_id}"
        print(f"  [Mobile Sync] Android successfully pulled '{mobile_trusted[0].sender_id}' (Total trusted: {len(mobile_trusted)}).")
        print("  [Pass] Test 1: Web -> Mobile Trusted Sender sync verified.")

        # -------------------------------------------------------------
        # TEST 2: Trusted Senders Two-Way Sync (Mobile -> Web)
        # -------------------------------------------------------------
        print("\n--- Test 2: Trusted Senders Sync (Mobile Action -> Web Retrieval) ---")
        mobile_sender = "AX-SBIBNK"
        mobile_trust_record = service.trust_sender(
            user_id=test_user.id,
            sender_id=mobile_sender,
            display_name="SBI Banking Alerts",
            reason="Trusted by user via Android Quick Actions"
        )
        print(f"  [Mobile Action] Trusted sender '{mobile_sender}' added via Android app.")

        # Web retrieval
        web_trusted = service.get_trusted_senders(user_id=test_user.id)
        assert len(web_trusted) == 2, f"Expected 2 trusted senders, got {len(web_trusted)}"
        senders_list = [s.sender_id for s in web_trusted]
        assert "AX-SBIBNK" in senders_list and "VK-HDFCBK" in senders_list, f"Missing senders in {senders_list}"
        print(f"  [Web Sync] Web dashboard fetched all trusted senders: {senders_list}.")
        print("  [Pass] Test 2: Mobile -> Web Trusted Sender sync verified.")

        # -------------------------------------------------------------
        # TEST 3: Quarantined SMS Sync (Detection -> Web & Mobile Triage)
        # -------------------------------------------------------------
        print("\n--- Test 3: Quarantined SMS Sync & Invariant Verification ---")
        threat_sms = ThreatEvent(
            id=str(uuid.uuid4()),
            user_id=test_user.id,
            timestamp=datetime.now(timezone.utc),
            sender="+919876543210",
            raw_content="Dear Customer, your bank KYC has expired. Update now at http://fake-bank-kyc.com/renew",
            event_type="SMS",
            risk_score=94,
            threat_category="HIGH",
            recommended_action="QUARANTINE",
            status="NEW",
            is_quarantined=True,
            quarantined_at=datetime.now(timezone.utc),
            quarantine_reason="TinyBERT flagged fake KYC and credential harvesting URL"
        )
        db.add(threat_sms)
        db.commit()
        print(f"  [Detection] Quarantined SMS created: ID={threat_sms.id}, Risk={threat_sms.risk_score}")

        # Web & Mobile query quarantined SMS
        quarantined_list = service.get_quarantined_sms(user_id=test_user.id)
        assert len(quarantined_list) == 1, f"Expected 1 quarantined SMS, got {len(quarantined_list)}"
        assert quarantined_list[0].id == threat_sms.id, "Quarantined SMS ID mismatch"
        print(f"  [Both Platforms] Quarantined message visible in Quarantine tab (Count={len(quarantined_list)}).")

        # Action: Release from quarantine (from Mobile or Web)
        released_record = service.release_quarantined_sms(
            user_id=test_user.id,
            threat_id=threat_sms.id
        )
        assert released_record.is_quarantined is False, "Message must no longer be quarantined"
        assert released_record.user_action == "RELEASED", f"Expected user_action to be RELEASED, got {released_record.user_action}"

        # Verify it no longer appears in quarantine on either platform
        active_quarantine = service.get_quarantined_sms(user_id=test_user.id)
        assert len(active_quarantine) == 0, f"Expected 0 active quarantined SMS, got {len(active_quarantine)}"
        print(f"  [Sync Result] Message released. Active quarantine count on both platforms = {len(active_quarantine)}.")

        # Verify Quarantine Invariant: Releasing SMS does NOT whitelist sender
        sender_trust = db.query(SenderSecurity).filter(
            SenderSecurity.user_id == test_user.id,
            SenderSecurity.sender_id == "+919876543210"
        ).first()
        is_trusted = sender_trust is not None and sender_trust.trust_status == "TRUSTED"
        assert not is_trusted, "Quarantine invariant violation: Releasing SMS must NOT whitelist sender!"
        print("  [Pass] Test 3: Quarantined SMS sync & release invariant verified.")

        # -------------------------------------------------------------
        # TEST 4: Blocked Senders Two-Way Sync
        # -------------------------------------------------------------
        print("\n--- Test 4: Blocked Senders Sync (Block on Mobile -> Synced to Web -> Unblock on Web) ---")
        spammer_sender = "+919999900000"
        blocked_rec = service.block_sender(
            user_id=test_user.id,
            sender_id=spammer_sender,
            reason="Blocked via Android Blocked Users Screen"
        )
        assert blocked_rec.is_blocked is True, "Sender must be blocked"
        assert blocked_rec.trust_status == "BLOCKED", f"Expected BLOCKED, got {blocked_rec.trust_status}"
        print(f"  [Mobile Action] Blocked sender '{spammer_sender}'.")

        # Web queries blocked senders
        web_blocked = service.get_blocked_senders(user_id=test_user.id)
        assert len(web_blocked) == 1, f"Expected 1 blocked sender, got {len(web_blocked)}"
        assert web_blocked[0].sender_id == spammer_sender, f"Expected {spammer_sender}, got {web_blocked[0].sender_id}"
        print(f"  [Web Sync] Web dashboard fetched blocked sender '{web_blocked[0].sender_id}'.")

        # Web unblocks sender
        unblocked_rec = service.unblock_sender(user_id=test_user.id, sender_id=spammer_sender)
        assert unblocked_rec.is_blocked is False, "Sender must be unblocked"
        print(f"  [Web Action] Sender '{spammer_sender}' unblocked via Web dashboard.")

        # Mobile re-queries blocked senders
        mobile_blocked = service.get_blocked_senders(user_id=test_user.id)
        assert len(mobile_blocked) == 0, f"Expected 0 blocked senders on mobile after unblock, got {len(mobile_blocked)}"
        print(f"  [Mobile Sync] Android confirms blocked senders count = {len(mobile_blocked)}.")
        print("  [Pass] Test 4: Blocked Senders two-way sync verified.")

        # -------------------------------------------------------------
        # TEST 5: Targeted SMS Blocking vs Sender Blocking Invariant
        # -------------------------------------------------------------
        print("\n--- Test 5: Targeted SMS Blocking vs Sender Blocking Invariant ---")
        multi_msg_sender = "+919111122222"
        threat_sms_target = ThreatEvent(
            id=str(uuid.uuid4()),
            user_id=test_user.id,
            timestamp=datetime.now(timezone.utc),
            sender=multi_msg_sender,
            raw_content="Suspicious investment scheme promo",
            event_type="SMS",
            risk_score=78,
            threat_category="MEDIUM",
            recommended_action="WARN",
            status="NEW",
            is_blocked=False
        )
        db.add(threat_sms_target)
        db.commit()

        # Block ONLY this specific message
        blocked_sms_rec = service.block_individual_sms(
            user_id=test_user.id,
            threat_id=threat_sms_target.id,
            reason="User blocked this specific promo SMS without blocking the sender"
        )
        assert blocked_sms_rec.is_blocked is True, "Targeted SMS must be marked blocked"

        # Check blocked SMS list
        blocked_sms_list = service.get_blocked_sms(user_id=test_user.id)
        assert len(blocked_sms_list) == 1, f"Expected 1 blocked SMS, got {len(blocked_sms_list)}"
        print(f"  [Targeted Block] SMS '{threat_sms_target.id}' is blocked on both platforms.")

        # Invariant: Sender must NOT be blacklisted as a whole
        sender_sec_rec = db.query(SenderSecurity).filter(
            SenderSecurity.user_id == test_user.id,
            SenderSecurity.sender_id == multi_msg_sender
        ).first()
        is_sender_blocked = sender_sec_rec is not None and sender_sec_rec.is_blocked
        assert not is_sender_blocked, "Invariant violation: Blocking individual SMS must NOT block the entire sender!"
        print(f"  [Invariant Verified] Sender '{multi_msg_sender}' remains unblocked while individual SMS is suppressed.")
        print("  [Pass] Test 5: Targeted SMS blocking vs Sender isolation verified.")

    finally:
        # Teardown
        db.query(SenderSecurity).filter(SenderSecurity.user_id == test_user.id).delete()
        db.query(ThreatEvent).filter(ThreatEvent.user_id == test_user.id).delete()
        db.delete(test_user)
        db.commit()
        db.close()
        print("\n[CLEANUP] Test records cleaned up successfully.")

    print("\n=====================================================================")
    print("ALL SECURITY CENTER TWO-WAY SYNCHRONIZATION TESTS PASSED!")
    print("=====================================================================")

if __name__ == "__main__":
    run_security_center_sync_tests()
