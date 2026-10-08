from contextlib import asynccontextmanager
from fastapi import FastAPI, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from sqlalchemy.exc import SQLAlchemyError

from app.config import settings
from app.api import api_v1_router
from app.database.session import check_db_health, ensure_tables
from app.middleware import (
    InMemoryRateLimiterMiddleware,
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

# 1. CORS Configuration
# In production, specific origins must be defined in CORS_ORIGINS environment variable.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:[0-9]+)?$" if settings.environment == "development" else None,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 2. Rate Limiting Middleware
app.add_middleware(
    InMemoryRateLimiterMiddleware,
    requests_per_minute=settings.rate_limit_per_minute
)

# 3. Secure Error Handling Handlers
app.add_exception_handler(StarletteHTTPException, http_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(SQLAlchemyError, sqlalchemy_exception_handler)
app.add_exception_handler(Exception, generic_exception_handler)

# 4. Root & Health Check Endpoints
@app.get("/", tags=["System"])
def read_root():
    return {
        "status": "ok",
        "service": "squish-shield-backend",
        "version": settings.version,
        "phase": "Phase 7 - Backend & PostgreSQL"
    }


@app.get("/health", tags=["System"])
def health_check():
    """Lightweight liveness probe."""
    return {
        "status": "ok",
        "service": "squish-shield-backend"
    }


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


# 5. API Routers
app.include_router(api_v1_router, prefix=settings.api_prefix)
