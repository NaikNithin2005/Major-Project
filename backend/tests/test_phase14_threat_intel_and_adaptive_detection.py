"""
Automated Test Suite for Phase 14:
Advanced Threat Intelligence, Safe URL Reputation Enrichment & Adaptive Detection.
"""

import pytest
from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.main import app
from app.database.session import get_db
from app.models.user import User
from app.models.threat_intel import ThreatIntel
from app.services.url_normalizer import SafeUrlNormalizer
from app.services.threat_intel_providers import (
    BaseThreatIntelProvider,
    ProviderLookupResult,
    ReputationStatus
)
from app.services.threat_intel_service import ThreatIntelService
from app.services.evidence_aggregation_service import EvidenceAggregationService
from app.repositories.threat_intel_repo import ThreatIntelRepository
from app.auth.dependencies import get_current_user, require_admin


# --- FIXTURES ---


@pytest.fixture
def mock_normal_user(db_session: Session):
    existing = db_session.query(User).filter(User.id == "user_phase14_regular").first()
    if existing:
        return existing
    user = User(
        id="user_phase14_regular",
        firebase_uid="user_phase14_regular_fb",
        email="user14@example.com",
        display_name="Phase14 Normal User",
        role="USER",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def mock_admin_user(db_session: Session):
    existing = db_session.query(User).filter(User.id == "admin_phase14_soc").first()
    if existing:
        return existing
    user = User(
        id="admin_phase14_soc",
        firebase_uid="admin_phase14_soc_fb",
        email="admin14@squishshield.org",
        display_name="Phase14 SOC Analyst",
        role="ADMIN",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


# --- 1. SAFE URL NORMALIZER TESTS ---

def test_url_normalizer_punycode_and_ports():
    # IDN domain should be converted to ASCII Punycode
    res = SafeUrlNormalizer.normalize_url("https://xn--pypal-4ve.com:443/login//verify?session=12345&user=alice")
    assert res.is_valid is True
    assert res.scheme == "https"
    assert res.port is None  # default 443 stripped
    assert res.hostname == "xn--pypal-4ve.com"
    assert "//" not in res.path
    # Privacy scrubbing: session parameter should be redacted
    assert "session=%5BREDACTED%5D" in res.normalized_url or "session=[REDACTED]" in res.normalized_url
    assert "user=alice" in res.normalized_url


def test_url_normalizer_ssrf_detection():
    # Loopback IP
    res_loopback = SafeUrlNormalizer.normalize_url("http://127.0.0.1:8000/internal/admin")
    assert res_loopback.is_valid is True
    assert res_loopback.is_ssrf_risk is True
    assert "Loopback" in res_loopback.ssrf_reason

    # Private RFC 1918 range
    res_private = SafeUrlNormalizer.normalize_url("http://192.168.1.1/router-login")
    assert res_private.is_valid is True
    assert res_private.is_ssrf_risk is True
    assert "Private" in res_private.ssrf_reason

    # Localhost keyword
    res_local = SafeUrlNormalizer.normalize_url("http://localhost:3000/api")
    assert res_local.is_valid is True
    assert res_local.is_ssrf_risk is True


def test_url_normalizer_dangerous_schemes():
    res_js = SafeUrlNormalizer.normalize_url("javascript:alert(1)")
    assert res_js.is_valid is False
    assert "Dangerous" in res_js.error

    res_data = SafeUrlNormalizer.normalize_url("data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==")
    assert res_data.is_valid is False


# --- 2. THREAT INTEL REPOSITORY TESTS ---

def test_threat_intel_repository_crud(db_session: Session):
    repo = ThreatIntelRepository(db_session)
    indicator_val = f"https://evil-phish-{datetime.now().timestamp()}.com/login"

    # Upsert new indicator
    ind, created = repo.upsert_indicator(
        indicator_type="URL",
        indicator_value=indicator_val,
        threat_type="PHISHING",
        confidence=0.99,
        source="TEST_FEED",
        metadata_json={"tags": ["bank_impersonation"]}
    )
    assert created is True
    assert ind.id is not None
    assert ind.confidence == 0.99

    # Find by value
    found = repo.find_by_value(indicator_val)
    assert found is not None
    assert found.indicator_value == indicator_val

    # Test TTL Expiration filtering
    ind.expires_at = datetime.now(timezone.utc) - timedelta(hours=1)
    db_session.commit()
    # Expired should not be returned by default
    assert repo.find_by_value(indicator_val) is None
    # But returned if include_expired=True
    assert repo.find_by_value(indicator_val, include_expired=True) is not None

    # Revoke
    ind.expires_at = None
    db_session.commit()
    revoked = repo.revoke_indicator(ind.id)
    assert revoked is True
    assert repo.find_by_value(indicator_val) is None


def test_threat_intel_bulk_upsert_deduplication(db_session: Session):
    repo = ThreatIntelRepository(db_session)
    val = f"https://bulk-dedup-{datetime.now().timestamp()}.org"

    batch = [
        {"indicator_type": "URL", "indicator_value": val, "threat_type": "MALWARE", "source": "FEED_A", "confidence": 0.8},
        {"indicator_type": "URL", "indicator_value": val, "threat_type": "MALWARE", "source": "FEED_B", "confidence": 0.95}
    ]
    res = repo.bulk_upsert(batch)
    assert res["inserted"] == 1
    assert res["updated"] == 1
    assert res["rejected"] == 0

    record = repo.find_by_value(val)
    assert record.confidence == 0.95


# --- 3. THREAT INTEL SERVICE & CACHING TESTS ---

def test_threat_intel_service_lookup_and_cache(db_session: Session):
    repo = ThreatIntelRepository(db_session)
    service = ThreatIntelService(db_session, providers=[])
    service.clear_cache()

    test_url = f"https://known-malicious-{datetime.now().timestamp()}.net/auth"
    norm = SafeUrlNormalizer.normalize_url(test_url)
    repo.upsert_indicator(
        indicator_type="URL",
        indicator_value=norm.normalized_url,
        threat_type="QUISHING",
        confidence=0.98,
        source="INTERNAL"
    )

    # First lookup: from database (cached=False)
    res1 = service.lookup_url_reputation(test_url)
    assert res1["reputation_status"] == ReputationStatus.KNOWN_MALICIOUS
    assert res1["is_malicious"] is True
    assert res1["cached"] is False
    assert res1["source"] == "INTERNAL"

    # Second lookup: should hit in-memory cache (cached=True)
    res2 = service.lookup_url_reputation(test_url)
    assert res2["reputation_status"] == ReputationStatus.KNOWN_MALICIOUS
    assert res2["cached"] is True


def test_threat_intel_provider_failure_isolation(db_session: Session):
    # Mock a failing provider that throws unexpected network exception
    class FailingProvider(BaseThreatIntelProvider):
        @property
        def name(self) -> str:
            return "FAILING_PROVIDER"
        @property
        def is_configured(self) -> bool:
            return True
        def check_url(self, url: str, canonical_url: str, hostname: str) -> ProviderLookupResult:
            raise ConnectionResetError("Connection abruptly terminated by remote peer")

    service = ThreatIntelService(db_session, providers=[FailingProvider()])
    # Must not crash; returns REPUTATION_UNAVAILABLE
    res = service.lookup_url_reputation("https://somewhere-unlisted.io/test")
    assert res["reputation_status"] in {ReputationStatus.REPUTATION_UNAVAILABLE, ReputationStatus.NO_KNOWN_THREAT}
    assert res["is_malicious"] is False


# --- 4. EXPLAINABLE EVIDENCE AGGREGATION & RISK SCORING TESTS ---

def test_evidence_aggregation_clamps_to_high_on_threat_intel_match():
    # When threat intel confirms known malicious, score must clamp to HIGH (>= 90)
    assessment = EvidenceAggregationService.aggregate_evidence(
        model_results={"xgboost_prob": 0.30, "tinybert_prob": 0.0, "is_anomaly": False},
        url_metadata={"is_ip_hostname": False, "is_punycode": False},
        threat_intel={
            "reputation_status": ReputationStatus.KNOWN_MALICIOUS,
            "is_malicious": True,
            "confidence": 0.98,
            "threat_type": "PHISHING",
            "source": "URLHAUS"
        }
    )
    assert assessment.final_risk_score >= 90
    assert assessment.threat_category == "HIGH"
    assert assessment.recommended_action == "Block or strongly warn"
    # Verify evidence item added
    intel_items = [e for e in assessment.evidence_items if e.category == "THREAT_INTEL"]
    assert len(intel_items) >= 1
    assert "URLHAUS" in intel_items[0].source
    assert intel_items[0].severity == "CRITICAL"


def test_evidence_aggregation_never_reduces_score_on_missing_intel():
    # If model flags high risk (0.92) but threat intel is offline/unlisted, score remains HIGH
    assessment = EvidenceAggregationService.aggregate_evidence(
        model_results={"xgboost_prob": 0.92, "is_anomaly": False},
        url_metadata={},
        threat_intel={"reputation_status": ReputationStatus.REPUTATION_UNAVAILABLE, "is_malicious": False}
    )
    assert assessment.final_risk_score >= 70
    assert assessment.threat_category in {"MEDIUM", "HIGH"}
    assert any(e.category == "AI_MODEL" for e in assessment.evidence_items)


# --- 5. REST API ENDPOINT TESTS ---

def test_threat_intel_lookup_endpoint(client: TestClient, mock_normal_user: User):
    app.dependency_overrides[get_current_user] = lambda: mock_normal_user

    try:
        response = client.post(
            "/api/v1/threat-intel/lookup",
            json={"url": "https://www.paypal.com/signin"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "normalized_url" in data
        assert "domain" in data
        assert "reputation_status" in data
        assert data["domain"] == "paypal.com"
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_admin_threat_intel_endpoints_rbac(client: TestClient, mock_normal_user: User, mock_admin_user: User):
    # Non-admin user should get 403 Forbidden on admin feeds endpoint
    app.dependency_overrides[get_current_user] = lambda: mock_normal_user

    try:
        res_unauth = client.get("/api/v1/admin/threat-intel/feeds")
        assert res_unauth.status_code == 403

        # Admin user should get 200 OK
        app.dependency_overrides[require_admin] = lambda: mock_admin_user
        res_admin = client.get("/api/v1/admin/threat-intel/feeds")
        assert res_admin.status_code == 200
        feeds = res_admin.json()
        assert len(feeds) >= 1
        assert any(f["feed_name"] == "INTERNAL_SOC_FEED" for f in feeds)
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(require_admin, None)


def test_admin_feed_ingest_and_indicator_lifecycle(client: TestClient, mock_admin_user: User):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user

    try:
        test_val = f"https://api-ingest-test-{datetime.now().timestamp()}.com/verify"

        # 1. Ingest batch feed with dry_run
        ingest_dry = client.post(
            "/api/v1/admin/threat-intel/ingest",
            json={
                "source": "URLHAUS",
                "indicators": [{"indicator_type": "URL", "indicator_value": test_val, "threat_type": "PHISHING"}],
                "dry_run": True
            }
        )
        assert ingest_dry.status_code == 200
        assert ingest_dry.json()["status"] == "DRY_RUN_COMPLETED"

        # 2. Ingest real batch
        ingest_real = client.post(
            "/api/v1/admin/threat-intel/ingest",
            json={
                "source": "URLHAUS",
                "indicators": [{"indicator_type": "URL", "indicator_value": test_val, "threat_type": "PHISHING"}],
                "dry_run": False
            }
        )
        assert ingest_real.status_code == 200
        assert ingest_real.json()["inserted"] == 1

        # 3. List indicators and find inserted
        list_res = client.get(f"/api/v1/admin/threat-intel/indicators?search=api-ingest-test")
        assert list_res.status_code == 200
        items = list_res.json()["items"]
        assert len(items) >= 1
        ind_id = items[0]["id"]

        # 4. Revoke indicator
        del_res = client.delete(f"/api/v1/admin/threat-intel/indicators/{ind_id}")
        assert del_res.status_code == 200
        assert del_res.json()["status"] == "REVOKED"
    finally:
        app.dependency_overrides.pop(require_admin, None)
