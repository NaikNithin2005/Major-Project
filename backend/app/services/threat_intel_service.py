"""
Threat Intelligence Service for Phase 14.
Coordinates URL canonicalization, local database matching, external provider querying,
TTL-based caching, and provenance tracking.
"""

from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
import logging
import time
import hashlib
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

logger = logging.getLogger("squish_shield.threat_intel")

from app.models.threat_intel import ThreatIntel, CommunityThreatReport, UserSharingPreference
from app.repositories.threat_intel_repo import ThreatIntelRepository
from app.repositories.community_threat_repo import CommunityThreatRepository
from app.repositories.audit_repo import AuditRepository
from app.services.url_normalizer import SafeUrlNormalizer, UrlNormalizationResult
from app.schemas.threat_intel import (
    CommunityThreatReportCreate,
    CommunityReportTriageRequest,
    IndicatorStatusUpdateRequest
)
from app.services.threat_intel_providers import (
    BaseThreatIntelProvider,
    URLhausProvider,
    PhishTankProvider,
    GoogleSafeBrowsingProvider,
    ProviderLookupResult,
    ReputationStatus
)


class CachedReputation:
    def __init__(self, result: Dict[str, Any], ttl_seconds: int = 3600):
        self.result = result
        self.cached_at = time.time()
        self.expires_at = self.cached_at + ttl_seconds

    def is_valid(self) -> bool:
        return time.time() < self.expires_at


class ThreatIntelService:
    # In-memory LRU/TTL cache keyed by canonical URL hash
    _reputation_cache: Dict[str, CachedReputation] = {}
    _MAX_CACHE_ENTRIES = 10000

    def __init__(self, db: Session, providers: Optional[List[BaseThreatIntelProvider]] = None):
        self.db = db
        self.repo = ThreatIntelRepository(db)
        self.comm_repo = CommunityThreatRepository(db)
        self.audit_repo = AuditRepository(db)
        self.providers = providers if providers is not None else [
            URLhausProvider(),
            PhishTankProvider(),
            GoogleSafeBrowsingProvider()
        ]

    def lookup_url_reputation(
        self,
        raw_url: str,
        user_id: Optional[str] = None,
        ip_address: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Perform a safe, enriched threat intelligence reputation lookup for a URL.
        1. Normalizes and validates the URL (SSRF & Punycode safe).
        2. Checks in-memory cache.
        3. Checks persistent local SOC database (threat_intel).
        4. Queries configured external providers (URLhaus, PhishTank, GSB).
        5. Computes provenance, freshness, and reputation status.
        """
        # Step 1: Normalize URL safely
        norm_res: UrlNormalizationResult = SafeUrlNormalizer.normalize_url(raw_url)
        if not norm_res.is_valid:
            return {
                "query_url": raw_url,
                "normalized_url": norm_res.normalized_url,
                "domain": norm_res.domain,
                "reputation_status": ReputationStatus.INVALID_URL,
                "is_malicious": False,
                "confidence": 0.0,
                "threat_type": None,
                "source": None,
                "first_seen": None,
                "last_seen": None,
                "tags": ["malformed_url"],
                "evidence_summary": [f"Malformed or dangerous URL: {norm_res.error}"],
                "cached": False,
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }

        # Step 2: Check in-memory cache
        cache_key = norm_res.canonical_hash
        if cache_key in self._reputation_cache:
            entry = self._reputation_cache[cache_key]
            if entry.is_valid():
                cached_data = dict(entry.result)
                cached_data["cached"] = True
                return cached_data
            else:
                del self._reputation_cache[cache_key]

        # Step 3: Check local database
        db_matches: List[ThreatIntel] = self.repo.find_matches(
            canonical_url=norm_res.normalized_url,
            domain=norm_res.hostname
        )
        if db_matches:
            best_match = max(db_matches, key=lambda m: m.confidence)
            result = {
                "query_url": raw_url,
                "normalized_url": norm_res.normalized_url,
                "domain": norm_res.domain,
                "reputation_status": ReputationStatus.KNOWN_MALICIOUS,
                "is_malicious": True,
                "confidence": best_match.confidence,
                "threat_type": best_match.threat_type,
                "source": best_match.source,
                "first_seen": best_match.first_seen.isoformat(),
                "last_seen": best_match.last_seen.isoformat(),
                "tags": (best_match.metadata_json or {}).get("tags", [best_match.threat_type.lower()]),
                "evidence_summary": [
                    f"Match found in {best_match.source} intelligence feed (Confidence: {int(best_match.confidence * 100)}%)"
                ],
                "cached": False,
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }
            self._set_cache(cache_key, result)
            return result

        # Step 4: Query external providers
        provider_results: List[ProviderLookupResult] = []
        for provider in self.providers:
            if provider.is_configured:
                try:
                    res = provider.check_url(
                        url=raw_url,
                        canonical_url=norm_res.normalized_url,
                        hostname=norm_res.hostname
                    )
                    provider_results.append(res)
                except Exception as ex:
                    logger.warning("Threat intelligence provider error", extra={"provider": provider.name, "error": str(ex)})
                    provider_results.append(ProviderLookupResult(
                        provider_name=provider.name,
                        reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                        is_malicious=False,
                        error_message=str(ex)
                    ))
                    continue
                if res.is_malicious:
                    # Found verified malicious URL in feed
                    now = datetime.now(timezone.utc)
                    # Cache in local DB for 7 days
                    self.repo.upsert_indicator(
                        indicator_type="URL",
                        indicator_value=norm_res.normalized_url,
                        threat_type=res.threat_type or "PHISHING",
                        confidence=res.confidence,
                        source=provider.name,
                        status="ACTIVE",
                        expires_at=now + timedelta(days=7),
                        metadata_json={"tags": res.tags, "discovered_by": "api_lookup"}
                    )

                    result = {
                        "query_url": raw_url,
                        "normalized_url": norm_res.normalized_url,
                        "domain": norm_res.domain,
                        "reputation_status": ReputationStatus.KNOWN_MALICIOUS,
                        "is_malicious": True,
                        "confidence": res.confidence,
                        "threat_type": res.threat_type,
                        "source": provider.name,
                        "first_seen": now.isoformat(),
                        "last_seen": now.isoformat(),
                        "tags": res.tags,
                        "evidence_summary": [
                            f"Identified as {res.threat_type} by {provider.name} (Confidence: {int(res.confidence * 100)}%)"
                        ],
                        "cached": False,
                        "is_ssrf_risk": norm_res.is_ssrf_risk,
                        "ssrf_reason": norm_res.ssrf_reason
                    }
                    self._set_cache(cache_key, result)
                    return result

        # Step 4b: Check for unverified community threat reports (dynamic consensus)
        comm_report = self.comm_repo.find_recent_by_hash(cache_key)
        if comm_report and comm_report.status == "PENDING_REVIEW":
            result = {
                "query_url": raw_url,
                "normalized_url": norm_res.normalized_url,
                "domain": norm_res.domain,
                "reputation_status": "SUSPICIOUS",
                "is_malicious": False,  # Unverified reports are never treated as confirmed malicious
                "confidence": comm_report.consensus_score,
                "threat_type": comm_report.threat_category,
                "source": "COMMUNITY_CONSENSUS",
                "first_seen": comm_report.submitted_at.isoformat(),
                "last_seen": comm_report.updated_at.isoformat(),
                "tags": ["community_report", "unverified"],
                "evidence_summary": [
                    f"Reported by {comm_report.corroboration_count} user(s) with consensus score {comm_report.consensus_score:.2f} (Under SOC review)"
                ],
                "cached": False,
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }
            self._set_cache(cache_key, result, ttl=300)  # 5-minute cache for active community reports
            return result

        # Step 5: Check if any provider was able to confirm clean/no_results
        has_clean = any(r.reputation_status == ReputationStatus.NO_KNOWN_THREAT for r in provider_results)
        if has_clean:
            result = {
                "query_url": raw_url,
                "normalized_url": norm_res.normalized_url,
                "domain": norm_res.domain,
                "reputation_status": ReputationStatus.NO_KNOWN_THREAT,
                "is_malicious": False,
                "confidence": 0.0,
                "threat_type": None,
                "source": "PROVIDERS_CLEAN",
                "first_seen": None,
                "last_seen": None,
                "tags": ["unlisted_in_feeds"],
                "evidence_summary": ["No matches found in active threat intelligence feeds"],
                "cached": False,
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }
            self._set_cache(cache_key, result, ttl=1800)  # 30 min cache for clean lookups
            return result

        # If all providers failed or were unconfigured:
        result = {
            "query_url": raw_url,
            "normalized_url": norm_res.normalized_url,
            "domain": norm_res.domain,
            "reputation_status": ReputationStatus.REPUTATION_UNAVAILABLE,
            "is_malicious": False,
            "confidence": 0.0,
            "threat_type": None,
            "source": None,
            "first_seen": None,
            "last_seen": None,
            "tags": ["reputation_offline"],
            "evidence_summary": ["Threat intelligence provider services currently unavailable"],
            "cached": False,
            "is_ssrf_risk": norm_res.is_ssrf_risk,
            "ssrf_reason": norm_res.ssrf_reason
        }
        return result

    @classmethod
    def _set_cache(cls, key: str, data: Dict[str, Any], ttl: int = 3600) -> None:
        if len(cls._reputation_cache) >= cls._MAX_CACHE_ENTRIES:
            # Simple eviction of oldest item
            first_key = next(iter(cls._reputation_cache))
            del cls._reputation_cache[first_key]
        cls._reputation_cache[key] = CachedReputation(data, ttl_seconds=ttl)

    @classmethod
    def clear_cache(cls) -> None:
        cls._reputation_cache.clear()

    def get_feeds_status(self) -> List[Dict[str, Any]]:
        """
        Return the operational status of all registered intelligence feed sources.
        """
        stats = self.repo.get_feed_statistics()
        by_source = stats.get("indicators_by_source", {})

        feed_list = [
            {
                "feed_name": "INTERNAL_SOC_FEED",
                "provider_type": "LOCAL_DATABASE",
                "is_active": True,
                "is_configured": True,
                "indicator_count": by_source.get("INTERNAL", 0),
                "last_sync": stats.get("latest_update"),
                "terms": "Internal academic & SOC operational rules"
            }
        ]

        for p in self.providers:
            count = by_source.get(p.name, 0)
            feed_list.append({
                "feed_name": p.name,
                "provider_type": "EXTERNAL_API",
                "is_active": p.is_configured,
                "is_configured": p.is_configured,
                "indicator_count": count,
                "last_sync": stats.get("latest_update"),
                "terms": "Academic & research evaluation use"
            })

        return feed_list

    # =========================================================================
    # PHASE 18: COLLABORATIVE SHARING & COMMUNITY TRIAGE WORKFLOWS
    # =========================================================================
    def submit_community_report(
        self,
        user_id: str,
        report_data: CommunityThreatReportCreate,
        ip_address: Optional[str] = None
    ) -> CommunityThreatReport:
        """
        Submit a privacy-preserving community threat report.
        Strict opt-in required: Rejects if user has not enabled threat intelligence sharing.
        Normalizes indicators, validates SSRF risks, and deduplicates/corroborates across users.
        """
        pref = self.comm_repo.get_user_preferences(user_id)
        if not pref or not pref.is_sharing_enabled:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="User has not opted in to community threat intelligence sharing. Please enable threat sharing in settings."
            )

        indicator_type = report_data.indicator_type.upper()
        raw_val = report_data.indicator_value.strip()

        if indicator_type == "URL":
            norm = SafeUrlNormalizer.normalize_url(raw_val)
            if not norm.is_valid:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Invalid URL indicator: {norm.error}"
                )
            if norm.is_ssrf_risk:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Rejected dangerous private/loopback URL: {norm.ssrf_reason}"
                )
            indicator_val = norm.normalized_url
            indicator_hash = norm.canonical_hash
        else:
            indicator_val = raw_val.lower()
            indicator_hash = hashlib.sha256(indicator_val.encode("utf-8")).hexdigest()

        # Mask IP address to prevent user tracing
        masked_ip = None
        if ip_address:
            parts = ip_address.split(".")
            if len(parts) == 4:
                masked_ip = f"{parts[0]}.{parts[1]}.0.0/16"
            else:
                masked_ip = "ANONYMIZED_ORIGIN"

        report, is_new = self.comm_repo.submit_report(
            indicator_type=indicator_type,
            indicator_value=indicator_val,
            indicator_hash=indicator_hash,
            threat_category=report_data.threat_category.upper(),
            user_risk_score=report_data.user_risk_score,
            reporter_user_id=user_id,
            model_verdicts=report_data.model_verdicts,
            context_metadata=report_data.context_metadata,
            ip_origin_masked=masked_ip
        )

        # Audit logging (with anonymous submission ID)
        self.audit_repo.log(
            action="COMMUNITY_THREAT_REPORT_SUBMITTED" if is_new else "COMMUNITY_THREAT_REPORT_CORROBORATED",
            resource_type="COMMUNITY_THREAT_REPORT",
            actor_user_id=user_id,
            resource_id=report.id,
            metadata={
                "anonymous_submission_id": report.anonymous_submission_id,
                "indicator_type": indicator_type,
                "corroboration_count": report.corroboration_count,
                "consensus_score": report.consensus_score
            }
        )

        return report

    def triage_community_report(
        self,
        report_id: str,
        admin_user_id: str,
        triage_data: CommunityReportTriageRequest
    ) -> CommunityThreatReport:
        """
        SOC Admin triage workflow:
        - APPROVE_AND_PROMOTE: Promotes community report to verified ThreatIntel indicator.
        - REJECT / MARK_DUPLICATE: Rejects report with documentation.
        """
        report = self.comm_repo.get_report_by_id(report_id)
        if not report:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Community threat report '{report_id}' not found"
            )

        promoted_id = None
        if triage_data.action == "APPROVE_AND_PROMOTE":
            now = datetime.now(timezone.utc)
            expires_at = now + timedelta(days=triage_data.expires_in_days)
            threat_type = triage_data.assigned_threat_type or report.threat_category
            promoted_ind, _ = self.repo.upsert_indicator(
                indicator_type=report.indicator_type,
                indicator_value=report.indicator_value,
                threat_type=threat_type,
                confidence=triage_data.assigned_confidence,
                source="COMMUNITY",
                status="ACTIVE",
                verification_status="VERIFIED",
                expires_at=expires_at,
                promoted_from_report_id=report.id,
                metadata_json={
                    "promoted_by_admin": admin_user_id,
                    "promoted_at": now.isoformat(),
                    "source_community_report_id": report.id,
                    "source_consensus_score": report.consensus_score,
                    "source_corroboration_count": report.corroboration_count,
                    "admin_notes": triage_data.notes
                }
            )
            promoted_id = promoted_ind.id

            # Clear cache for this indicator so immediately live
            cache_key = report.indicator_hash
            if cache_key in self._reputation_cache:
                del self._reputation_cache[cache_key]

        updated_report = self.comm_repo.triage_report(
            report_id=report_id,
            admin_user_id=admin_user_id,
            action=triage_data.action,
            admin_notes=triage_data.notes,
            promoted_indicator_id=promoted_id
        )

        # Audit log
        self.audit_repo.log(
            action=f"COMMUNITY_REPORT_{triage_data.action}",
            resource_type="COMMUNITY_THREAT_REPORT",
            actor_user_id=admin_user_id,
            resource_id=report_id,
            metadata={
                "action": triage_data.action,
                "promoted_indicator_id": promoted_id,
                "notes": triage_data.notes
            }
        )

        return updated_report

    def update_indicator_lifecycle(
        self,
        indicator_id: str,
        admin_user_id: str,
        status_update: IndicatorStatusUpdateRequest
    ) -> ThreatIntel:
        """
        Admin indicator status lifecycle transitions (VERIFIED, EXPIRED, REVOKED).
        """
        valid_statuses = {"VERIFIED", "EXPIRED", "REVOKED"}
        target_status = status_update.status.upper()
        if target_status not in valid_statuses:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid lifecycle status '{status_update.status}'. Must be one of {valid_statuses}"
            )

        indicator = self.repo.update_indicator_status(
            indicator_id=indicator_id,
            status=target_status,
            reason=status_update.reason
        )
        if not indicator:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Threat indicator '{indicator_id}' not found"
            )

        # Invalidate in-memory cache
        self.clear_cache()

        # Audit log
        self.audit_repo.log(
            action=f"THREAT_INDICATOR_{target_status}",
            resource_type="THREAT_INTEL",
            actor_user_id=admin_user_id,
            resource_id=indicator_id,
            metadata={"status": target_status, "reason": status_update.reason}
        )

        return indicator

    def get_threat_intel_health(self) -> Dict[str, Any]:
        """
        Comprehensive operational health and metric report across feeds, indicators, and community reports.
        """
        feed_stats = self.repo.get_feed_statistics()
        comm_stats = self.comm_repo.get_community_statistics()
        feeds = self.get_feeds_status()

        # Calculate overall status
        any_active_feeds = any(f["is_active"] for f in feeds)
        overall_status = "HEALTHY" if any_active_feeds else "DEGRADED"

        return {
            "status": overall_status,
            "total_verified_indicators": feed_stats.get("total_indicators", 0),
            "active_indicators": feed_stats.get("active_indicators", 0),
            "revoked_indicators": feed_stats.get("revoked_indicators", 0),
            "expired_indicators": feed_stats.get("expired_indicators", 0),
            "total_community_reports": comm_stats.get("total_community_reports", 0),
            "pending_community_reports": comm_stats.get("pending_community_reports", 0),
            "approved_community_reports": comm_stats.get("approved_community_reports", 0),
            "rejected_community_reports": comm_stats.get("rejected_community_reports", 0),
            "feeds": feeds
        }
