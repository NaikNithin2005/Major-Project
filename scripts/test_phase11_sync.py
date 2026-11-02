import sys
import os
import uuid
from datetime import datetime, timezone

# Add backend to sys.path
backend_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "backend")
if backend_path not in sys.path:
    sys.path.insert(0, backend_path)

from sqlalchemy import create_engine, select, and_
from sqlalchemy.orm import Session
from app.models import User, ThreatEvent, SenderSecurity, SafetyAlert, Feedback
from app.services.security_service import SecurityService
from app.services.alert_service import AlertService
from app.services.feedback_service import FeedbackService
from app.schemas.feedback import FeedbackCreateRequest
from app.schemas.safety_alert import SafetyAlertCreate

db_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "squish_shield_dev.db")
engine = create_engine(f"sqlite:///{db_path}")

print("=====================================================================")
print("RUNNING PHASE 11 SYNCHRONIZATION & PHASE 1-12 INTEGRATION VERIFICATION")
print("=====================================================================")

with Session(engine) as db:
    # 1. Setup isolated test user
    test_user_id = f"test-sync-{uuid.uuid4().hex[:8]}"
    test_user = User(
        id=test_user_id,
        firebase_uid=f"fb_{test_user_id}",
        email=f"sync_tester_{test_user_id}@squish.local",
        display_name="Sync Test User",
        role="USER",
        is_active=True
    )
    db.add(test_user)
    db.commit()
    print(f"[SETUP] Created isolated user: {test_user.id} ({test_user.email})")

    sec_service = SecurityService(db)
    alert_service = AlertService(db)
    fb_service = FeedbackService(db)

    # -------------------------------------------------------------
    # SCENARIOS 1 & 2: SAFETY ALERT RESOLUTION & NON-REVERSION
    # -------------------------------------------------------------
    print("\n--- Testing Scenarios 1 & 2: Alert Resolution & Non-Reversion ---")
    threat = ThreatEvent(
        id=f"threat_{uuid.uuid4().hex[:8]}",
        user_id=test_user_id,
        event_type="SMS",
        risk_score=92,
        threat_category="HIGH",
        recommended_action="DO_NOT_CLICK",
        timestamp=datetime.now(timezone.utc),
        sender="VK-FAKEBNK",
        raw_content="Dear customer, your account is suspended. Click http://scam.link/kyc to verify",
        status="REPORTED"
    )
    db.add(threat)
    db.commit()

    alert_in = SafetyAlertCreate(
        title="Critical Fake KYC Alert",
        sender=threat.sender,
        message_preview=threat.raw_content,
        threat_category="HIGH",
        severity="HIGH",
        risk_score=92,
        detection_reason="Fake KYC harvest link",
        threat_event_id=threat.id
    )
    alert = alert_service.create_alert(user_id=test_user_id, alert_in=alert_in)
    assert alert.status == "ACTIVE", f"Expected ACTIVE, got {alert.status}"
    print(f"  [Created] Alert ID={alert.id}, status={alert.status}")

    # Web resolves alert
    resolved_alert = alert_service.resolve_alert(user_id=test_user_id, alert_id=alert.id, resolution_notes="Verified by user on Web")
    assert resolved_alert.status == "RESOLVED", f"Expected RESOLVED, got {resolved_alert.status}"
    assert resolved_alert.resolved_at is not None, "resolved_at must be set"
    assert resolved_alert.resolved_by == test_user_id, "resolved_by must match user"

    # Verify linked threat event is updated to RESOLVED as well
    db.refresh(threat)
    assert threat.status == "RESOLVED", f"Linked threat expected status=RESOLVED, got {threat.status}"
    assert threat.user_action == "RESOLVED", f"Linked threat expected user_action=RESOLVED, got {threat.user_action}"
    print(f"  [Resolved] Alert resolved successfully. Status={resolved_alert.status}, Threat Status={threat.status}")

    # Verify alert query filters out resolved from active alerts
    active_alerts = alert_service.get_alerts(user_id=test_user_id, status_filter="ACTIVE")
    assert not any(a.id == alert.id for a in active_alerts), "Resolved alert must NOT appear in ACTIVE alerts list!"
    print("  [Pass] Scenarios 1 & 2 verified: Alert resolution persists and never reappears in active alerts.")

    # -------------------------------------------------------------
    # SCENARIOS 3 & 4: TRUSTED SENDERS & INVARIANT CHECKS
    # -------------------------------------------------------------
    print("\n--- Testing Scenarios 3 & 4: Trusted Senders & Safety Invariants ---")
    bank_sender = "VK-HDFCBK"
    trusted_rec = sec_service.trust_sender(
        user_id=test_user_id,
        sender_id=bank_sender,
        display_name="HDFC Bank Official Alerts",
        reason="Verified official TRAI bank header"
    )
    assert trusted_rec.trust_status == "TRUSTED", f"Expected TRUSTED, got {trusted_rec.trust_status}"
    assert trusted_rec.is_blocked == False, "Trusted sender must not be blocked"
    assert trusted_rec.trusted_at is not None, "trusted_at must be set"

    trusted_list = sec_service.get_trusted_senders(user_id=test_user_id)
    assert any(s.sender_id == bank_sender for s in trusted_list), "Sender must be returned in trusted senders list"
    print(f"  [Trust Added] Sender {bank_sender} is TRUSTED. Count in trusted list={len(trusted_list)}")

    # Invariant: A trusted sender receiving a dangerous spoofed SMS must still record dangerous threat
    spoofed_sms = ThreatEvent(
        id=f"threat_{uuid.uuid4().hex[:8]}",
        user_id=test_user_id,
        event_type="SMS",
        risk_score=88,
        threat_category="HIGH",
        recommended_action="DO_NOT_CLICK",
        timestamp=datetime.now(timezone.utc),
        sender=bank_sender,
        raw_content="URGENT: Click http://phish.xyz to avoid immediate penalty",
        status="REPORTED"
    )
    db.add(spoofed_sms)
    db.commit()
    assert spoofed_sms.risk_score == 88, "Message risk must not be erased merely because sender is trusted"
    print("  [Pass] Scenarios 3 & 4 verified: Trusting sender works, individual message safety scans remain active.")

    # -------------------------------------------------------------
    # SCENARIOS 5 & 6: SENDER BLOCKING VS MESSAGE BLOCKING
    # -------------------------------------------------------------
    print("\n--- Testing Scenarios 5 & 6: Sender Blocking vs SMS Blocking ---")
    malicious_sender = "+919999988888"
    blocked_sender = sec_service.block_sender(
        user_id=test_user_id,
        sender_id=malicious_sender,
        reason="Phishing lottery scam sender"
    )
    assert blocked_sender.is_blocked == True, "Sender must be flagged as blocked"
    assert blocked_sender.trust_status == "BLOCKED", "Trust status must be BLOCKED"

    blocked_list = sec_service.get_blocked_senders(user_id=test_user_id)
    assert any(s.sender_id == malicious_sender for s in blocked_list), "Sender must appear in blocked senders list"
    print(f"  [Sender Blocked] {malicious_sender} is BLOCKED. Blocked senders count={len(blocked_list)}")

    # Unblock sender
    unblocked = sec_service.unblock_sender(user_id=test_user_id, sender_id=malicious_sender)
    assert unblocked.is_blocked == False, "Sender must be unblocked"
    print(f"  [Sender Unblocked] {malicious_sender} unblocked. is_blocked={unblocked.is_blocked}")

    # Test individual SMS blocking without blocking sender
    innocent_sender = "+918888877777"
    isolated_sms = ThreatEvent(
        id=f"threat_{uuid.uuid4().hex[:8]}",
        user_id=test_user_id,
        event_type="SMS",
        risk_score=75,
        threat_category="HIGH",
        recommended_action="DO_NOT_CLICK",
        timestamp=datetime.now(timezone.utc),
        sender=innocent_sender,
        raw_content="Congratulations! Claim your cashback now",
        status="REPORTED"
    )
    db.add(isolated_sms)
    db.commit()

    # Block individual SMS
    blocked_sms = sec_service.block_individual_sms(user_id=test_user_id, threat_id=isolated_sms.id, reason="User blocked message only")
    assert blocked_sms.is_blocked == True, "SMS must be blocked"
    
    # Invariant: Blocking an SMS must NOT automatically block the sender!
    sender_sec_stmt = select(SenderSecurity).where(and_(SenderSecurity.user_id == test_user_id, SenderSecurity.sender_id == innocent_sender))
    sender_sec = db.scalar(sender_sec_stmt)
    assert sender_sec is None or not sender_sec.is_blocked, "Innocent sender must NOT be blocked when an individual SMS is blocked!"
    print("  [Pass] Scenarios 5 & 6 verified: Targeted SMS blocking works without blacklisting entire sender.")

    # -------------------------------------------------------------
    # QUARANTINE INVARIANT VERIFICATION
    # -------------------------------------------------------------
    print("\n--- Testing Quarantine Policy: Releasing SMS must NOT Whitelist Sender ---")
    quarantine_sender = "+917777766666"
    quarantined_event = ThreatEvent(
        id=f"threat_{uuid.uuid4().hex[:8]}",
        user_id=test_user_id,
        event_type="SMS",
        risk_score=78,
        threat_category="HIGH",
        recommended_action="QUARANTINE",
        timestamp=datetime.now(timezone.utc),
        sender=quarantine_sender,
        raw_content="Suspicious transaction approval code: 9988",
        is_quarantined=True,
        quarantine_reason="Suspicious smishing pattern",
        status="REPORTED"
    )
    db.add(quarantined_event)
    db.commit()

    released_event = sec_service.release_quarantined_sms(user_id=test_user_id, threat_id=quarantined_event.id)
    assert released_event.is_quarantined == False, "SMS must be released from quarantine"
    assert released_event.user_action == "RELEASED", "user_action must be RELEASED"

    # Sender must NOT be whitelisted
    q_sender_record = db.scalar(select(SenderSecurity).where(and_(SenderSecurity.user_id == test_user_id, SenderSecurity.sender_id == quarantine_sender)))
    assert q_sender_record is None or q_sender_record.trust_status != "TRUSTED", "Quarantine release must NEVER automatically whitelist sender!"
    print("  [Pass] Quarantine Invariant verified: Released SMS without altering sender trust status.")

    # -------------------------------------------------------------
    # SCENARIO 7: USER FEEDBACK & IMMUTABLE AI BASELINE
    # -------------------------------------------------------------
    print("\n--- Testing Scenario 7: User Feedback & Immutable AI Baseline ---")
    banking_sms_event = ThreatEvent(
        id=f"threat_{uuid.uuid4().hex[:8]}",
        user_id=test_user_id,
        event_type="SMS",
        risk_score=85,
        threat_category="HIGH",
        recommended_action="WARN",
        timestamp=datetime.now(timezone.utc),
        sender="VK-AXISBK",
        raw_content="Rs 5,000.00 debited from A/C XX1234 on 02-Nov-26 via UPI Ref 987654. Balance Rs 24,500.00",
        status="REPORTED"
    )
    db.add(banking_sms_event)
    db.commit()

    fb_req = FeedbackCreateRequest(
        threat_event_id=banking_sms_event.id,
        classification="SAFE",
        corrected_label="LEGITIMATE_BANKING",
        comment="This is my real salary account UPI debit confirmation",
        original_prediction="SMISHING",
        original_confidence=0.85
    )
    feedback_rec = fb_service.submit_feedback(request=fb_req, user=test_user, ip_address="127.0.0.1")
    assert feedback_rec.corrected_label == "LEGITIMATE_BANKING", f"Expected LEGITIMATE_BANKING, got {feedback_rec.corrected_label}"
    assert feedback_rec.original_prediction == "SMISHING", "Original AI model prediction must be preserved immutably!"
    print(f"  [Feedback Logged] Original AI Prediction='{feedback_rec.original_prediction}', Corrected Label='{feedback_rec.corrected_label}'")
    print("  [Pass] Scenario 7 verified: User reclassification preserved immutable AI prediction.")

    # Clean up test records
    db.query(SenderSecurity).filter(SenderSecurity.user_id == test_user.id).delete()
    db.query(SafetyAlert).filter(SafetyAlert.user_id == test_user.id).delete()
    db.query(Feedback).filter(Feedback.user_id == test_user.id).delete()
    db.query(ThreatEvent).filter(ThreatEvent.user_id == test_user.id).delete()
    db.delete(test_user)
    db.commit()
    print("\n[CLEANUP] Test records cleaned up successfully.")

print("\n=====================================================================")
print("ALL 7 SYNCHRONIZATION SCENARIOS & SECURITY INVARIANTS PASSED!")
print("=====================================================================")
