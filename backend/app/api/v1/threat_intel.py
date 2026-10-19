"""
Threat Intelligence API Endpoints for Phase 14.
Provides safe URL reputation enrichment and SOC administrative feed management.
"""

from typing import List, Optional
import time
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.auth.dependencies import get_current_user, require_admin
from app.models.user import User
from app.services.threat_intel_service import ThreatIntelService
from app.services.url_normalizer import SafeUrlNormalizer
from app.repositories.audit_repo import AuditRepository
from app.schemas.threat_intel import (
    ThreatIntelLookupRequest,
    ThreatIntelLookupResponse,
    ThreatIndicatorCreate,
    ThreatIndicatorResponse,
    ThreatIndicatorListResponse,
    FeedStatusItem,
    FeedIngestRequest,
    FeedIngestResponse,
    UserSharingPreferencesRequest,
    UserSharingPreferencesResponse,
    CommunityThreatReportCreate,
    CommunityThreatReportResponse,
    CommunityThreatReportListResponse,
    CommunityReportTriageRequest,
    IndicatorStatusUpdateRequest,
    ThreatIntelHealthResponse
)

router = APIRouter(tags=["Threat Intelligence"])


# 1. URL Reputation Lookup (Accessible to authenticated users)
@router.post(
    "/threat-intel/lookup",
    response_model=ThreatIntelLookupResponse,
    summary="Safe Threat Intelligence Reputation Lookup"
)
def lookup_reputation(
    request: ThreatIntelLookupRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Query normalized threat intelligence feeds for a URL.
    SSRF safe, Punycode decoded, and privacy scrubbed.
    """
    service = ThreatIntelService(db)
    result = service.lookup_url_reputation(
        raw_url=request.url,
        user_id=current_user.id if current_user else None
    )
    return ThreatIntelLookupResponse(**result)


# 2. Feed Status Overview (ADMIN only)
@router.get(
    "/admin/threat-intel/feeds",
    response_model=List[FeedStatusItem],
    summary="List Active Threat Intelligence Feeds and Status"
)
def list_feeds(
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    service = ThreatIntelService(db)
    return service.get_feeds_status()


# 3. Batch Feed Ingestion (ADMIN only)
@router.post(
    "/admin/threat-intel/ingest",
    response_model=FeedIngestResponse,
    summary="Ingest Threat Feed Indicators"
)
def ingest_feed(
    request: FeedIngestRequest,
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    start_time = time.time()
    service = ThreatIntelService(db)

    # Normalize URLs before ingestion
    normalized_records = []
    rejected_count = 0

    for item in request.indicators:
        raw_val = item.get("indicator_value")
        itype = (item.get("indicator_type") or "URL").upper()
        if not raw_val:
            rejected_count += 1
            continue

        if itype == "URL":
            norm = SafeUrlNormalizer.normalize_url(raw_val)
            if not norm.is_valid:
                rejected_count += 1
                continue
            val = norm.normalized_url
        else:
            val = str(raw_val).strip().lower()

        normalized_records.append({
            "indicator_type": itype,
            "indicator_value": val,
            "threat_type": (item.get("threat_type") or "PHISHING").upper(),
            "confidence": float(item.get("confidence", 1.0)),
            "source": request.source.upper(),
            "status": "ACTIVE",
            "expires_at": item.get("expires_at"),
            "metadata_json": item.get("metadata_json") or {}
        })

    if request.dry_run:
        elapsed = (time.time() - start_time) * 1000
        return FeedIngestResponse(
            source=request.source,
            received=len(request.indicators),
            inserted=len(normalized_records),
            updated=0,
            rejected=rejected_count,
            duration_ms=round(elapsed, 2),
            status="DRY_RUN_COMPLETED"
        )

    # Commit to DB
    res = service.repo.bulk_upsert(normalized_records)
    elapsed = (time.time() - start_time) * 1000

    # Audit log
    audit_repo = AuditRepository(db)
    audit_repo.log(
        action="THREAT_INTEL_FEED_INGESTED",
        resource_type="THREAT_INTEL",
        actor_user_id=admin_user.id,
        metadata={
            "source": request.source,
            "inserted": res["inserted"],
            "updated": res["updated"],
            "rejected": res["rejected"] + rejected_count
        }
    )

    return FeedIngestResponse(
        source=request.source,
        received=len(request.indicators),
        inserted=res["inserted"],
        updated=res["updated"],
        rejected=res["rejected"] + rejected_count,
        duration_ms=round(elapsed, 2),
        status="COMPLETED"
    )


# 4. List / Search Indicators (ADMIN only)
@router.get(
    "/admin/threat-intel/indicators",
    response_model=ThreatIndicatorListResponse,
    summary="List Threat Indicators"
)
def list_indicators(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
    indicator_type: Optional[str] = Query(None),
    threat_type: Optional[str] = Query(None),
    source: Optional[str] = Query(None),
    status_filter: Optional[str] = Query(None, alias="status"),
    search: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    service = ThreatIntelService(db)
    items, total = service.repo.list_indicators(
        skip=skip,
        limit=limit,
        indicator_type=indicator_type,
        threat_type=threat_type,
        source=source,
        status=status_filter,
        search=search
    )
    return ThreatIndicatorListResponse(
        items=[ThreatIndicatorResponse.model_validate(it) for it in items],
        total=total,
        skip=skip,
        limit=limit
    )


# 5. Add Custom Indicator (ADMIN only)
@router.post(
    "/admin/threat-intel/indicators",
    response_model=ThreatIndicatorResponse,
    summary="Add Threat Indicator Manually"
)
def add_indicator(
    indicator: ThreatIndicatorCreate,
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    service = ThreatIntelService(db)

    # Normalize value if URL
    val = indicator.indicator_value
    if indicator.indicator_type.upper() == "URL":
        norm = SafeUrlNormalizer.normalize_url(val)
        if not norm.is_valid:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Malformed URL indicator: {norm.error}"
            )
        val = norm.normalized_url
    else:
        val = val.strip().lower()

    record, _ = service.repo.upsert_indicator(
        indicator_type=indicator.indicator_type.upper(),
        indicator_value=val,
        threat_type=indicator.threat_type.upper(),
        confidence=indicator.confidence,
        source=indicator.source.upper() or "INTERNAL",
        status=indicator.status.upper(),
        expires_at=indicator.expires_at,
        metadata_json=indicator.metadata_json
    )

    # Audit log
    audit_repo = AuditRepository(db)
    audit_repo.log(
        action="THREAT_INDICATOR_ADDED",
        resource_type="THREAT_INTEL",
        actor_user_id=admin_user.id,
        resource_id=record.id,
        metadata={"indicator_value": val, "source": record.source}
    )

    return ThreatIndicatorResponse.model_validate(record)


# 6. Revoke Indicator (ADMIN only)
@router.delete(
    "/admin/threat-intel/indicators/{indicator_id}",
    summary="Revoke Threat Indicator"
)
def revoke_indicator(
    indicator_id: str,
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    service = ThreatIntelService(db)
    success = service.repo.revoke_indicator(indicator_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Indicator '{indicator_id}' not found"
        )

    # Audit log
    audit_repo = AuditRepository(db)
    audit_repo.log(
        action="THREAT_INDICATOR_REVOKED",
        resource_type="THREAT_INTEL",
        actor_user_id=admin_user.id,
        resource_id=indicator_id
    )

    return {"status": "REVOKED", "indicator_id": indicator_id}


# =========================================================================
# PHASE 18: COLLABORATIVE THREAT INTELLIGENCE SHARING & CONSENT ENDPOINTS
# =========================================================================

# 7. Get User Threat Sharing Consent Preferences
@router.get(
    "/threat-intel/consent",
    response_model=UserSharingPreferencesResponse,
    summary="Get User Threat Sharing Consent Preferences"
)
def get_user_consent_preferences(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Retrieve user threat intelligence sharing preferences.
    Defaults to strict opt-out (is_sharing_enabled=False) if not configured.
    """
    service = ThreatIntelService(db)
    pref = service.comm_repo.get_user_preferences(current_user.id)
    if not pref:
        now = datetime.now(timezone.utc)
        return UserSharingPreferencesResponse(
            user_id=current_user.id,
            is_sharing_enabled=False,
            consent_version="v1.0",
            consent_timestamp=None,
            allow_campaign_correlation=True,
            withdrawn_at=None,
            updated_at=now
        )
    return UserSharingPreferencesResponse.model_validate(pref)


# 8. Update User Threat Sharing Consent Preferences
@router.post(
    "/threat-intel/consent",
    response_model=UserSharingPreferencesResponse,
    summary="Update User Threat Sharing Consent Preferences"
)
def set_user_consent_preferences(
    request: UserSharingPreferencesRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Update voluntary opt-in/opt-out status for community threat intelligence sharing.
    When opting out, marks withdrawn_at and halts automated submissions from client.
    """
    service = ThreatIntelService(db)
    pref = service.comm_repo.set_user_preferences(
        user_id=current_user.id,
        is_sharing_enabled=request.is_sharing_enabled,
        consent_version=request.consent_version,
        allow_campaign_correlation=request.allow_campaign_correlation
    )

    # Audit log
    audit_repo = AuditRepository(db)
    audit_repo.log(
        action="USER_THREAT_SHARING_CONSENT_UPDATED",
        resource_type="USER_SHARING_PREFERENCE",
        actor_user_id=current_user.id,
        metadata={
            "is_sharing_enabled": request.is_sharing_enabled,
            "consent_version": request.consent_version,
            "allow_campaign_correlation": request.allow_campaign_correlation
        }
    )

    return UserSharingPreferencesResponse.model_validate(pref)


# 9. Submit Collaborative Threat Report (Opt-in users only)
@router.post(
    "/threat-intel/community/submit",
    response_model=CommunityThreatReportResponse,
    summary="Submit Community Threat Report"
)
def submit_community_threat_report(
    request: CommunityThreatReportCreate,
    req: Request,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Submit an opt-in, privacy-preserving threat indicator.
    Strictly forbids raw SMS message bodies.
    Enforces SSRF validation, normalization, and dynamic multi-user corroboration.
    """
    service = ThreatIntelService(db)
    client_ip = req.client.host if req.client else None
    report = service.submit_community_report(
        user_id=current_user.id,
        report_data=request,
        ip_address=client_ip
    )
    return CommunityThreatReportResponse.model_validate(report)


# 10. List Authenticated User's Submitted Reports (Privacy Compliance)
@router.get(
    "/threat-intel/community/my-reports",
    response_model=CommunityThreatReportListResponse,
    summary="List My Submitted Threat Reports"
)
def list_my_community_reports(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    View user's own submitted indicators and their review statuses.
    Ensures strict privacy isolation (users cannot see other users' reports).
    """
    service = ThreatIntelService(db)
    items, total = service.comm_repo.get_user_reports(
        user_id=current_user.id,
        skip=skip,
        limit=limit
    )
    return CommunityThreatReportListResponse(
        items=[CommunityThreatReportResponse.model_validate(it) for it in items],
        total=total,
        skip=skip,
        limit=limit
    )


# 11. Withdraw Pending Community Report (Privacy Compliance)
@router.delete(
    "/threat-intel/community/my-reports/{report_id}",
    summary="Withdraw User's Pending Community Report"
)
def withdraw_my_community_report(
    report_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Allows a user to withdraw and permanently delete a pending unverified report they submitted.
    """
    service = ThreatIntelService(db)
    success = service.comm_repo.withdraw_user_report(
        report_id=report_id,
        user_id=current_user.id
    )
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Report not found or cannot be withdrawn (only pending reports can be withdrawn)"
        )

    # Audit log
    audit_repo = AuditRepository(db)
    audit_repo.log(
        action="COMMUNITY_THREAT_REPORT_WITHDRAWN",
        resource_type="COMMUNITY_THREAT_REPORT",
        actor_user_id=current_user.id,
        resource_id=report_id
    )

    return {"status": "WITHDRAWN", "report_id": report_id}


# 12. List Community Reports Queue (ADMIN only)
@router.get(
    "/admin/threat-intel/community-reports",
    response_model=CommunityThreatReportListResponse,
    summary="List Community Threat Reports Queue"
)
def list_community_reports_admin(
    status_filter: Optional[str] = Query(None, alias="status"),
    indicator_type: Optional[str] = Query(None),
    threat_category: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    """
    SOC Admin view of submitted community reports with corroboration counts and consensus scores.
    """
    service = ThreatIntelService(db)
    items, total = service.comm_repo.list_reports(
        status=status_filter,
        indicator_type=indicator_type,
        threat_category=threat_category,
        search=search,
        skip=skip,
        limit=limit
    )
    return CommunityThreatReportListResponse(
        items=[CommunityThreatReportResponse.model_validate(it) for it in items],
        total=total,
        skip=skip,
        limit=limit
    )


# 13. Triage Community Report (ADMIN only)
@router.patch(
    "/admin/threat-intel/community-reports/{report_id}/triage",
    response_model=CommunityThreatReportResponse,
    summary="Triage Community Threat Report"
)
def triage_community_report(
    report_id: str,
    request: CommunityReportTriageRequest,
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    """
    SOC triage action on community submissions:
    - APPROVE_AND_PROMOTE: Verifies and promotes indicator to official ThreatIntel database.
    - REJECT: Declines indicator.
    - MARK_DUPLICATE: Closes redundant reports.
    """
    service = ThreatIntelService(db)
    report = service.triage_community_report(
        report_id=report_id,
        admin_user_id=admin_user.id,
        triage_data=request
    )
    return CommunityThreatReportResponse.model_validate(report)


# 14. Update Indicator Lifecycle Status (ADMIN only)
@router.patch(
    "/admin/threat-intel/indicators/{indicator_id}/status",
    response_model=ThreatIndicatorResponse,
    summary="Update Indicator Lifecycle Status"
)
def update_indicator_lifecycle_status(
    indicator_id: str,
    request: IndicatorStatusUpdateRequest,
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    """
    Transition indicator lifecycle (VERIFIED, EXPIRED, REVOKED) with documented audit reason.
    """
    service = ThreatIntelService(db)
    indicator = service.update_indicator_lifecycle(
        indicator_id=indicator_id,
        admin_user_id=admin_user.id,
        status_update=request
    )
    return ThreatIndicatorResponse.model_validate(indicator)


# 15. Threat Intelligence Health & Overview (ADMIN only)
@router.get(
    "/admin/threat-intel/health",
    response_model=ThreatIntelHealthResponse,
    summary="Get Threat Intelligence Health and Metrics"
)
def get_threat_intel_health_overview(
    db: Session = Depends(get_db),
    admin_user: User = Depends(require_admin)
):
    """
    Comprehensive health overview across registered feeds, verified indicators, and community queues.
    """
    service = ThreatIntelService(db)
    health = service.get_threat_intel_health()
    return ThreatIntelHealthResponse(**health)

