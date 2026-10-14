from contextlib import asynccontextmanager
from fastapi import FastAPI, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from sqlalchemy.exc import SQLAlchemyError

from app.config import settings
from app.api import api_v1_router
from app.database.session import check_db_health, ensure_tables, get_db_pool_status
from app.auth.firebase import get_auth_system_status
from app.services.model_integrity_service import ModelIntegrityService
from app.middleware import (
    CorrelationIdMiddleware,
    InMemoryRateLimiterMiddleware,
    SecurityHeadersMiddleware,
    RequestSizeLimitMiddleware,
    http_exception_handler,
    validation_exception_handler,
    sqlalchemy_exception_handler,
    generic_exception_handler,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Ensure tables and seed initial development data
    try:
        ensure_tables()
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning(f"Startup ensure_tables warning: {e}")
    yield


app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="Squish Shield Centralized Backend & Telemetry API service for Quishing & Smishing Detection",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
    lifespan=lifespan,
)

# 1. Correlation ID & Structured Tracing Middleware
app.add_middleware(CorrelationIdMiddleware)

# 2. Security Headers Middleware (OWASP recommended defense-in-depth headers)
app.add_middleware(SecurityHeadersMiddleware)

# 3. Request Body Size Limit Middleware (DoS / memory exhaustion prevention)
app.add_middleware(RequestSizeLimitMiddleware)

# 4. CORS Configuration
# In production, specific origins must be defined in CORS_ORIGINS environment variable.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:[0-9]+)?$" if settings.environment == "development" else None,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# 5. Rate Limiting Middleware
app.add_middleware(
    InMemoryRateLimiterMiddleware,
    requests_per_minute=settings.rate_limit_per_minute
)

# 6. Secure Error Handling Handlers
app.add_exception_handler(StarletteHTTPException, http_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(SQLAlchemyError, sqlalchemy_exception_handler)
app.add_exception_handler(Exception, generic_exception_handler)


# 7. Root & Health Check Endpoints
@app.get("/", tags=["System"])
def read_root():
    return {
        "status": "ok",
        "service": "squish-shield-backend",
        "version": settings.version,
        "phase": "Phase 13 - Post-Release Operations & Maintenance"
    }


@app.get("/health", tags=["System"])
def health_check():
    """Lightweight legacy health probe."""
    return {
        "status": "ok",
        "service": "squish-shield-backend"
    }


@app.get("/health/live", tags=["System"])
def liveness_probe():
    """Kubernetes / Cloud liveness probe indicating whether the process is alive."""
    return {
        "status": "alive",
        "service": "squish-shield-backend",
        "version": settings.version
    }


@app.get("/health/ready", tags=["System"])
def readiness_probe():
    """
    Kubernetes / Cloud readiness probe checking database connectivity, auth readiness,
    and AI model artifact availability. Omits credentials, passwords, and sensitive internal details.
    """
    db_connected = check_db_health()
    auth_status = get_auth_system_status()
    pool_status = get_db_pool_status()
    model_status = ModelIntegrityService.verify_all_models()

    is_ready = db_connected and auth_status.get("production_ready", True) and model_status.get("all_healthy", True)

    payload = {
        "status": "ready" if is_ready else "degraded",
        "service": "squish-shield-backend",
        "version": settings.version,
        "database": {
            "status": "connected" if db_connected else "unavailable",
            "dialect": pool_status.get("dialect"),
            "wal_enabled": pool_status.get("wal_enabled"),
        },
        "auth": {
            "firebase_initialized": auth_status.get("firebase_initialized"),
            "init_mode": auth_status.get("init_mode"),
            "mock_auth_active": auth_status.get("mock_auth_active"),
        },
        "models": {
            "status": model_status.get("status"),
            "verified_count": model_status.get("verified_count"),
            "total_models": model_status.get("total_models"),
        }
    }

    if not is_ready:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, content=payload)
    return payload


@app.get("/health/db", tags=["System"])
def db_health_check():
    """Readiness probe checking database connectivity."""
    db_connected = check_db_health()
    if not db_connected:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "status": "degraded",
                "database": "unavailable"
            }
        )
    return {
        "status": "ok",
        "database": "connected"
    }


# 8. API Routers
app.include_router(api_v1_router, prefix=settings.api_prefix)
