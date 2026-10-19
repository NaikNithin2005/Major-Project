"""
Threat Intelligence Repository for Phase 14.
Provides persistent CRUD, canonical matching, bulk upsert deduplication,
TTL expiration filtering, and feed metrics over the threat_intel table.
"""

from datetime import datetime, timezone
from typing import List, Optional, Tuple, Dict, Any
from sqlalchemy import func, or_, and_, desc, select
from sqlalchemy.orm import Session
from app.models.threat_intel import ThreatIntel


class ThreatIntelRepository:
    def __init__(self, db: Session):
        self.db = db

    def find_by_id(self, indicator_id: str) -> Optional[ThreatIntel]:
        return self.db.query(ThreatIntel).filter(ThreatIntel.id == indicator_id).first()

    def find_by_value(
        self,
        indicator_value: str,
        include_expired: bool = False
    ) -> Optional[ThreatIntel]:
        """
        Query an indicator by exact normalized value.
        By default, filters out revoked and expired indicators.
        """
        query = self.db.query(ThreatIntel).filter(
            ThreatIntel.indicator_value == indicator_value
        )
        if not include_expired:
            now = datetime.now(timezone.utc)
            query = query.filter(
                ThreatIntel.status == "ACTIVE",
                or_(ThreatIntel.expires_at == None, ThreatIntel.expires_at > now)  # noqa: E711
            )
        return query.first()

    def find_matches(
        self,
        canonical_url: str,
        domain: str,
        include_expired: bool = False
    ) -> List[ThreatIntel]:
        """
        Find all active threat intelligence records matching the URL, domain, or IP.
        """
        now = datetime.now(timezone.utc)
        targets = [canonical_url, domain]
        # Also include without trailing slash if present
        if canonical_url.endswith("/"):
            targets.append(canonical_url[:-1])
        else:
            targets.append(canonical_url + "/")

        query = self.db.query(ThreatIntel).filter(
            ThreatIntel.indicator_value.in_(targets)
        )
        if not include_expired:
            query = query.filter(
                ThreatIntel.status == "ACTIVE",
                or_(ThreatIntel.expires_at == None, ThreatIntel.expires_at > now)  # noqa: E711
            )
        return query.all()

    def upsert_indicator(
        self,
        indicator_type: str,
        indicator_value: str,
        threat_type: str,
        confidence: float = 1.0,
        source: str = "INTERNAL",
        status: str = "ACTIVE",
        verification_status: str = "VERIFIED",
        expires_at: Optional[datetime] = None,
        revocation_reason: Optional[str] = None,
        promoted_from_report_id: Optional[str] = None,
        metadata_json: Optional[Dict[str, Any]] = None
    ) -> Tuple[ThreatIntel, bool]:
        """
        Upsert a threat indicator. Returns (indicator, created_boolean).
        """
        now = datetime.now(timezone.utc)
        existing = self.db.query(ThreatIntel).filter(
            ThreatIntel.indicator_value == indicator_value
        ).first()

        if existing:
            existing.indicator_type = indicator_type
            existing.threat_type = threat_type
            existing.confidence = max(existing.confidence, confidence)
            existing.source = source or existing.source
            existing.last_seen = now
            existing.status = status
            existing.verification_status = verification_status
            if expires_at is not None:
                existing.expires_at = expires_at
            if revocation_reason is not None:
                existing.revocation_reason = revocation_reason
            if promoted_from_report_id is not None:
                existing.promoted_from_report_id = promoted_from_report_id
            if metadata_json:
                merged = dict(existing.metadata_json or {})
                merged.update(metadata_json)
                existing.metadata_json = merged
            self.db.commit()
            self.db.refresh(existing)
            return existing, False
        else:
            new_record = ThreatIntel(
                indicator_type=indicator_type,
                indicator_value=indicator_value,
                threat_type=threat_type,
                confidence=confidence,
                source=source,
                status=status,
                verification_status=verification_status,
                first_seen=now,
                last_seen=now,
                expires_at=expires_at,
                revocation_reason=revocation_reason,
                promoted_from_report_id=promoted_from_report_id,
                metadata_json=metadata_json or {}
            )
            self.db.add(new_record)
            self.db.commit()
            self.db.refresh(new_record)
            return new_record, True

    def bulk_upsert(self, records: List[Dict[str, Any]]) -> Dict[str, int]:
        """
        Bulk upsert normalized indicators from threat feeds.
        """
        inserted = 0
        updated = 0
        rejected = 0

        for rec in records:
            val = rec.get("indicator_value")
            itype = rec.get("indicator_type", "URL")
            ttype = rec.get("threat_type", "PHISHING")
            if not val:
                rejected += 1
                continue

            try:
                _, created = self.upsert_indicator(
                    indicator_type=itype,
                    indicator_value=val,
                    threat_type=ttype,
                    confidence=float(rec.get("confidence", 1.0)),
                    source=rec.get("source", "FEED"),
                    status=rec.get("status", "ACTIVE"),
                    expires_at=rec.get("expires_at"),
                    metadata_json=rec.get("metadata_json")
                )
                if created:
                    inserted += 1
                else:
                    updated += 1
            except Exception:
                self.db.rollback()
                rejected += 1

        return {"inserted": inserted, "updated": updated, "rejected": rejected}

    def list_indicators(
        self,
        skip: int = 0,
        limit: int = 50,
        indicator_type: Optional[str] = None,
        threat_type: Optional[str] = None,
        source: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None
    ) -> Tuple[List[ThreatIntel], int]:
        """
        List threat indicators with filters and pagination.
        """
        query = self.db.query(ThreatIntel)
        if indicator_type:
            query = query.filter(ThreatIntel.indicator_type == indicator_type.upper())
        if threat_type:
            query = query.filter(ThreatIntel.threat_type == threat_type.upper())
        if source:
            query = query.filter(ThreatIntel.source == source.upper())
        if status:
            query = query.filter(ThreatIntel.status == status.upper())
        if search:
            query = query.filter(ThreatIntel.indicator_value.ilike(f"%{search}%"))

        total = query.count()
        results = query.order_by(desc(ThreatIntel.last_seen)).offset(skip).limit(limit).all()
        return results, total

    def revoke_indicator(self, indicator_id: str) -> bool:
        """
        Revoke an active indicator.
        """
        indicator = self.find_by_id(indicator_id)
        if not indicator:
            return False
        indicator.status = "REVOKED"
        indicator.verification_status = "REVOKED"
        self.db.commit()
        return True

    def update_indicator_status(
        self,
        indicator_id: str,
        status: str,
        reason: Optional[str] = None
    ) -> Optional[ThreatIntel]:
        """
        Update verification status and lifecycle state for an indicator.
        """
        indicator = self.find_by_id(indicator_id)
        if not indicator:
            return None

        indicator.verification_status = status.upper()
        if reason:
            indicator.revocation_reason = reason

        if status.upper() in ["REVOKED", "EXPIRED"]:
            indicator.status = status.upper()
        elif status.upper() == "VERIFIED":
            indicator.status = "ACTIVE"

        self.db.commit()
        self.db.refresh(indicator)
        return indicator

    def get_feed_statistics(self) -> Dict[str, Any]:
        """
        Compute feed volume, active status counts, expired indicators, and latest update timestamps.
        """
        now = datetime.now(timezone.utc)
        total = self.db.query(func.count(ThreatIntel.id)).scalar() or 0
        active = self.db.query(func.count(ThreatIntel.id)).filter(
            ThreatIntel.status == "ACTIVE",
            or_(ThreatIntel.expires_at == None, ThreatIntel.expires_at > now)  # noqa: E711
        ).scalar() or 0
        revoked = self.db.query(func.count(ThreatIntel.id)).filter(
            or_(ThreatIntel.status == "REVOKED", ThreatIntel.verification_status == "REVOKED")
        ).scalar() or 0
        expired = self.db.query(func.count(ThreatIntel.id)).filter(
            or_(
                ThreatIntel.status == "EXPIRED",
                ThreatIntel.verification_status == "EXPIRED",
                and_(ThreatIntel.expires_at != None, ThreatIntel.expires_at <= now)  # noqa: E711
            )
        ).scalar() or 0

        # Group by source
        source_counts = dict(
            self.db.query(ThreatIntel.source, func.count(ThreatIntel.id))
            .group_by(ThreatIntel.source)
            .all()
        )

        latest_record = self.db.query(ThreatIntel).order_by(
            desc(ThreatIntel.last_seen)
        ).first()

        return {
            "total_indicators": total,
            "active_indicators": active,
            "revoked_indicators": revoked,
            "expired_indicators": expired,
            "indicators_by_source": source_counts,
            "latest_update": latest_record.last_seen.isoformat() if latest_record else None
        }
