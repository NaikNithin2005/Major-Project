from fastapi import FastAPI
from fastapi.testclient import TestClient
from app.middleware.rate_limiter import InMemoryRateLimiterMiddleware


def test_rate_limiter_exceeded():
    test_app = FastAPI()
    test_app.add_middleware(InMemoryRateLimiterMiddleware, requests_per_minute=3)

    @test_app.get("/api/test-endpoint")
    def test_endpoint():
        return {"ok": True}

    client = TestClient(test_app)

    # First 3 requests succeed
    for _ in range(3):
        resp = client.get("/api/test-endpoint")
        assert resp.status_code == 200

    # 4th request exceeds limit and triggers 429
    excess_resp = client.get("/api/test-endpoint")
    assert excess_resp.status_code == 429
    data = excess_resp.json()
    assert data["error"]["code"] == "RATE_LIMIT_EXCEEDED"
    assert "Retry-After" in excess_resp.headers


def test_rate_limiter_exempts_health():
    test_app = FastAPI()
    test_app.add_middleware(InMemoryRateLimiterMiddleware, requests_per_minute=2)

    @test_app.get("/health")
    def health():
        return {"status": "ok"}

    client = TestClient(test_app)

    # Health check should not be throttled
    for _ in range(5):
        resp = client.get("/health")
        assert resp.status_code == 200
