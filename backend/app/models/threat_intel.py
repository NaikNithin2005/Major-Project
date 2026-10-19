import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from sqlalchemy import String, Float, Integer, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base
from app.database.types import JSONBCompat


class ThreatIntel(Base):
    """
    Verified Threat Indicator Model (Phase 14 & 18).
    Represents authoritative quishing, smishing, and phishing indicators
    validated from external feeds, SOC administrative additions, or promoted community reports.
    """
    __tablename__ = "threat_intel"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    indicator_type: Mapped[str] = mapped_column(String(32), index=True, nullable=False)   # DOMAIN, URL, IP, SENDER, CAMPAIGN
    indicator_value: Mapped[str] = mapped_column(String(512), unique=True, index=True, nullable=False)
    threat_type: Mapped[str] = mapped_column(String(32), index=True, nullable=False)       # QUISHING, SMISHING, PHISHING, MALWARE
    confidence: Mapped[float] = mapped_column(Float, default=1.0, nullable=False)
    source: Mapped[str] = mapped_column(String(64), default="INTERNAL", nullable=False)   # URLHAUS, PHISHTANK, GSB, INTERNAL, COMMUNITY
    first_seen: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    last_seen: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE", index=True, nullable=False)  # ACTIVE, VERIFIED, EXPIRED, REVOKED
    verification_status: Mapped[str] = mapped_column(String(32), default="VERIFIED", index=True, nullable=False) # UNVERIFIED, VERIFIED, EXPIRED, REVOKED
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, index=True)
    revocation_reason: Mapped[str | None] = mapped_column(String(512), nullable=True)
    promoted_from_report_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    metadata_json: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)


class UserSharingPreference(Base):
    """
    User Threat Intelligence Sharing Consent & Preferences (Phase 18).
    Strict opt-in model: defaults to false until explicit user consent is given.
    """
    __tablename__ = "user_sharing_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, index=True, nullable=False)
    is_sharing_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    consent_version: Mapped[str] = mapped_column(String(16), default="v1.0", nullable=False)
    consent_timestamp: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    allow_campaign_correlation: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    withdrawn_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

    user = relationship("User", backref="sharing_preference")


class CommunityThreatReport(Base):
    """
    Collaborative Community Threat Report (Phase 18).
    Captures opt-in, privacy-preserving threat submissions from edge clients.
    Raw SMS bodies are strictly prohibited.
    """
    __tablename__ = "community_threat_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    reporter_user_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    anonymous_submission_id: Mapped[str] = mapped_column(String(36), unique=True, index=True, default=lambda: str(uuid.uuid4()), nullable=False)
    indicator_type: Mapped[str] = mapped_column(String(32), index=True, nullable=False)   # URL, DOMAIN, CAMPAIGN
    indicator_value: Mapped[str] = mapped_column(String(512), index=True, nullable=False)  # Normalized & scrubbed target URL or domain
    indicator_hash: Mapped[str] = mapped_column(String(64), index=True, nullable=False)   # SHA-256 hash for deduplication
    threat_category: Mapped[str] = mapped_column(String(32), index=True, nullable=False)   # QUISHING, SMISHING, PHISHING, MALICIOUS_CAMPAIGN
    user_risk_score: Mapped[int] = mapped_column(Integer, nullable=False)                   # 0 - 100
    model_verdicts: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    context_metadata: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True) # Structural attributes only (NO PII)
    status: Mapped[str] = mapped_column(String(32), default="PENDING_REVIEW", index=True, nullable=False) # PENDING_REVIEW, APPROVED, REJECTED, PROMOTED
    consensus_score: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    corroboration_count: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    admin_notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    reviewed_by_admin_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    promoted_indicator_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("threat_intel.id", ondelete="SET NULL"), nullable=True)
    submitted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)
    ip_origin_masked: Mapped[str | None] = mapped_column(String(64), nullable=True)

    reporter = relationship("User", foreign_keys=[reporter_user_id])
    reviewer = relationship("User", foreign_keys=[reviewed_by_admin_id])
    promoted_indicator = relationship("ThreatIntel", foreign_keys=[promoted_indicator_id])
