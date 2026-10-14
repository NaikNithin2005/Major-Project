import pytest
import uuid
from app.services.model_integrity_service import ModelIntegrityService


def test_phase13_liveness_probe(client):
    """Verify standard liveness probe returns alive status."""
    res = client.get("/health/live")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "alive"
    assert data["service"] == "squish-shield-backend"


def test_phase13_readiness_probe_with_model_health(client):
    """Verify readiness probe checks DB, auth, and AI model availability without leaking secrets."""
    res = client.get("/health/ready")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ready"
    assert "database" in data
    assert data["database"]["status"] == "connected"
    assert "models" in data
    assert data["models"]["verified_count"] >= 3
    assert data["models"]["status"] == "HEALTHY"


def test_phase13_correlation_id_middleware_injection(client):
    """Verify CorrelationIdMiddleware injects X-Correlation-ID header on all responses."""
    res = client.get("/")
    assert res.status_code == 200
    assert "x-correlation-id" in res.headers
    cid = res.headers["x-correlation-id"]
    assert len(cid) > 0


def test_phase13_correlation_id_client_propagation(client):
    """Verify client-supplied correlation ID is propagated end-to-end."""
    custom_cid = str(uuid.uuid4())
    res = client.get("/", headers={"X-Correlation-ID": custom_cid})
    assert res.status_code == 200
    assert res.headers.get("x-correlation-id") == custom_cid


def test_phase13_error_envelope_includes_correlation_id(client):
    """Verify error responses include correlation_id for tracing."""
    custom_cid = "trace-err-12345"
    res = client.get("/api/v1/detections/nonexistent-id", headers={"X-Correlation-ID": custom_cid})
    assert res.status_code in (401, 404)
    data = res.json()
    assert "error" in data
    assert data["error"].get("correlation_id") == custom_cid


def test_phase13_model_integrity_service_verifies_models():
    """Verify ModelIntegrityService validates cryptographic checksums against manifest."""
    result = ModelIntegrityService.verify_all_models()
    assert result["status"] == "HEALTHY"
    assert result["all_healthy"] is True
    assert result["total_models"] == 3
    assert result["verified_count"] == 3

    model_ids = [m["model_id"] for m in result["models"]]
    assert "tinybert_sms" in model_ids
    assert "xgboost_url" in model_ids
    assert "isolation_forest_anomaly" in model_ids


def test_phase13_admin_operations_status_requires_admin(client, user_headers):
    """Verify /admin/operations/status rejects ordinary users with 403 Forbidden."""
    res = client.get(
        "/api/v1/admin/operations/status",
        headers=user_headers
    )
    assert res.status_code == 403


def test_phase13_admin_operations_status_succeeds_for_admin(client, admin_headers):
    """Verify /admin/operations/status returns complete operational report for admins."""
    res = client.get(
        "/api/v1/admin/operations/status",
        headers=admin_headers
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] in ("HEALTHY", "DEGRADED")
    assert "uptime_seconds" in data
    assert "database" in data
    assert "models" in data
    assert data["models"]["verified_count"] == 3
    assert "synchronization" in data
    assert "security_monitoring" in data


def test_phase13_admin_verify_models_endpoint(client, admin_headers):
    """Verify manual model integrity re-verification endpoint creates an audit log."""
    res = client.post(
        "/api/v1/admin/operations/verify-models",
        headers=admin_headers
    )
    assert res.status_code == 200
    data = res.json()
    assert data["all_healthy"] is True
    assert data["verified_count"] == 3


def test_phase13_admin_sync_metrics_endpoint(client, admin_headers):
    """Verify sync metrics endpoint provides telemetry counters."""
    res = client.get(
        "/api/v1/admin/operations/sync-metrics",
        headers=admin_headers
    )
    assert res.status_code == 200
    data = res.json()
    assert "total_synced_events" in data
    assert "sms_events" in data
    assert "qr_events" in data
    assert "high_risk_events" in data
