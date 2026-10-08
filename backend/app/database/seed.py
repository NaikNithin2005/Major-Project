import logging
from datetime import datetime, timezone, timedelta
from sqlalchemy.orm import Session
from app.models.user import User
from app.models.device import Device
from app.models.threat_event import ThreatEvent
from app.models.model_info import ModelInfo
from app.models.feedback import Feedback

logger = logging.getLogger(__name__)


def seed_initial_data(db: Session):
    """Seed initial development data if the database is newly initialized."""
    try:
        # Check if already seeded
        if db.query(ThreatEvent).count() > 0:
            return

        logger.info("Seeding initial development threat events and model telemetry...")

        # 1. Admin & Test Users
        admin_user = db.query(User).filter(User.firebase_uid == "admin-mock-uid-0001").first()
        if not admin_user:
            admin_user = User(
                firebase_uid="admin-mock-uid-0001",
                email="admin@squishshield.local",
                display_name="System Administrator",
                role="ADMIN",
                is_active=True,
                last_seen_at=datetime.now(timezone.utc),
            )
            db.add(admin_user)

        user1 = db.query(User).filter(User.firebase_uid == "user-mock-uid-0001").first()
        if not user1:
            user1 = User(
                firebase_uid="user-mock-uid-0001",
                email="user@squishshield.local",
                display_name="Security Analyst 1",
                role="USER",
                is_active=True,
                last_seen_at=datetime.now(timezone.utc),
            )
            db.add(user1)

        db.flush()

        # 2. Registered Devices
        device = db.query(Device).first()
        if not device:
            device = Device(
                user_id=user1.id,
                device_identifier="fp-pixel-7-sec-sandbox-01",
                platform="ANDROID",
                app_version="1.0.0-phase9",
                model_version="v2.0.0",
                last_sync_at=datetime.now(timezone.utc),
            )
            db.add(device)
            db.flush()

        # 3. Model Registry
        models = [
            ModelInfo(
                name="MobileBERT-Smishing-v1",
                version="1.0.0",
                type="NLP_INTENT",
                status="ACTIVE",
                artifact_hash="sha256:7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069",
                metadata_json={"accuracy": 0.982, "f1_score": 0.979, "latency_ms": 14.2, "size_mb": 24.5},
            ),
            ModelInfo(
                name="Quishing-Vision-Heuristics-v2",
                version="2.1.0",
                type="VISION_HEURISTIC",
                status="ACTIVE",
                artifact_hash="sha256:c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2",
                metadata_json={"accuracy": 0.991, "f1_score": 0.988, "latency_ms": 8.5, "size_mb": 4.1},
            ),
            ModelInfo(
                name="Homoglyph-Detector-v1",
                version="1.0.2",
                type="RULE_BASED",
                status="ACTIVE",
                artifact_hash="sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                metadata_json={"accuracy": 0.999, "latency_ms": 1.1, "size_mb": 0.8},
            ),
        ]
        db.add_all(models)

        # 4. Realistic Threat Events
        now = datetime.now(timezone.utc)
        sample_threats = [
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="SMS",
                risk_score=94,
                threat_category="HIGH",
                recommended_action="BLOCK_AND_REPORT",
                timestamp=now - timedelta(minutes=15),
                status="REPORTED",
                reasons=[
                    "Impersonation of financial institution (Chase Bank)",
                    "Urgent call-to-action requesting account credential verification",
                    "Suspicious shortened URL redirecting to non-standard TLD",
                ],
                model_results={"nlp_confidence": 0.96, "intent": "credential_harvesting"},
                url_metadata={"domain": "chase-security-verify.top", "protocol": "https", "entropy": 4.2},
            ),
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="QR",
                risk_score=88,
                threat_category="HIGH",
                recommended_action="BLOCK_AND_REPORT",
                timestamp=now - timedelta(hours=1, minutes=20),
                status="INVESTIGATING",
                reasons=[
                    "Target URL uses homoglyph character substitutions",
                    "Redirects to unverified third-party payment gateway",
                ],
                model_results={"vision_confidence": 0.91, "entropy_score": 0.88},
                qr_metadata={"encoded_url": "https://pаypal-secure.com/pay", "payload_type": "URL"},
            ),
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="SMS",
                risk_score=62,
                threat_category="MEDIUM",
                recommended_action="WARN_USER",
                timestamp=now - timedelta(hours=3, minutes=45),
                status="RESOLVED",
                reasons=[
                    "Package delivery notification from unverified sender",
                    "Unknown tracking link with tracking query parameters",
                ],
                model_results={"nlp_confidence": 0.65, "intent": "package_tracking"},
                url_metadata={"domain": "usps-redelivery-notice.info", "protocol": "http"},
            ),
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="QR",
                risk_score=24,
                threat_category="LOW",
                recommended_action="ALLOW_WITH_LOG",
                timestamp=now - timedelta(hours=6, minutes=10),
                status="RESOLVED",
                reasons=["Dynamic redirect through URL shortening service"],
                model_results={"vision_confidence": 0.22},
                qr_metadata={"encoded_url": "https://bit.ly/cafe-menu-order", "payload_type": "URL"},
            ),
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="SMS",
                risk_score=8,
                threat_category="SAFE",
                recommended_action="ALLOW",
                timestamp=now - timedelta(hours=12),
                status="RESOLVED",
                reasons=["Standard two-factor authentication code from known carrier"],
                model_results={"nlp_confidence": 0.05, "intent": "otp_verification"},
            ),
            ThreatEvent(
                user_id=user1.id,
                device_id=device.id,
                event_type="SMS",
                risk_score=91,
                threat_category="HIGH",
                recommended_action="BLOCK_AND_REPORT",
                timestamp=now - timedelta(days=1, hours=2),
                status="REPORTED",
                reasons=[
                    "Fake tax refund notification (IRS impersonation)",
                    "Demands immediate action with threat of penalties",
                ],
                model_results={"nlp_confidence": 0.94, "intent": "tax_scam"},
                url_metadata={"domain": "irs-gov-refund-tax.cc", "protocol": "https"},
            ),
        ]
        db.add_all(sample_threats)
        db.flush()

        # 5. Sample User Feedback
        feedback = Feedback(
            threat_event_id=sample_threats[0].id,
            user_id=user1.id,
            classification="TRUE_POSITIVE",
            comment="Received this fake Chase text, definitely a scam. Blocked it immediately.",
        )
        db.add(feedback)

        db.commit()
        logger.info(f"Successfully seeded {len(sample_threats)} threat events and initial telemetry.")
    except Exception as err:
        db.rollback()
        logger.warning(f"Error seeding development data: {err}")
