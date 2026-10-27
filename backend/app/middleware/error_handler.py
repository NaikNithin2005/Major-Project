import logging
from fastapi import Request, status
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from sqlalchemy.exc import SQLAlchemyError

logger = logging.getLogger("squish_shield.error_handler")


def _get_correlation_id(request: Request) -> str | None:
    return getattr(request.state, "correlation_id", None)


async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    """Handle standard Starlette / FastAPI HTTPExceptions with standard envelope."""
    status_code = exc.status_code
    correlation_id = _get_correlation_id(request)

    # Default code name based on HTTP status
    if status_code == status.HTTP_401_UNAUTHORIZED:
        code = "UNAUTHORIZED"
    elif status_code == status.HTTP_403_FORBIDDEN:
        code = "FORBIDDEN"
    elif status_code == status.HTTP_404_NOT_FOUND:
        code = "NOT_FOUND"
    elif status_code == status.HTTP_429_TOO_MANY_REQUESTS:
        code = "RATE_LIMIT_EXCEEDED"
    elif status_code == status.HTTP_400_BAD_REQUEST:
        code = "BAD_REQUEST"
    else:
        code = f"HTTP_{status_code}"

    # Extract message / details / specific code override if provided
    if isinstance(exc.detail, dict):
        code = exc.detail.get("code", code)
        message = exc.detail.get("message", "An error occurred.")
        details = exc.detail.get("details", None)
    elif isinstance(exc.detail, str):
        message = exc.detail
        details = None
    else:
        message = str(exc.detail)
        details = None

    headers = dict(getattr(exc, "headers", None) or {})
    if correlation_id:
        headers["X-Correlation-ID"] = correlation_id

    error_body = {
        "code": code,
        "message": message,
        "details": details,
    }
    if correlation_id:
        error_body["correlation_id"] = correlation_id

    return JSONResponse(
        status_code=status_code,
        content={"error": error_body},
        headers=headers,
    )


async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """Handle Pydantic schema validation errors without leaking internal details."""
    correlation_id = _get_correlation_id(request)
    errors = exc.errors()
    simplified_errors = []
    for err in errors:
        loc = " -> ".join(str(l) for l in err.get("loc", []))
        msg = err.get("msg", "Validation error")
        simplified_errors.append({"field": loc, "message": msg})

    headers = {}
    if correlation_id:
        headers["X-Correlation-ID"] = correlation_id

    error_body = {
        "code": "VALIDATION_ERROR",
        "message": "Invalid request payload or query parameters.",
        "details": simplified_errors,
    }
    if correlation_id:
        error_body["correlation_id"] = correlation_id

    return JSONResponse(
        status_code=422,
        content={"error": error_body, "detail": errors},
        headers=headers,
    )


async def sqlalchemy_exception_handler(request: Request, exc: SQLAlchemyError) -> JSONResponse:
    """Handle database errors securely without leaking SQL, schema details, or stack traces."""
    correlation_id = _get_correlation_id(request)
    logger.error(
        "Database error during request %s [correlation_id=%s]: %s",
        request.url.path,
        correlation_id,
        str(exc),
        exc_info=True,
    )

    headers = {}
    if correlation_id:
        headers["X-Correlation-ID"] = correlation_id

    error_body = {
        "code": "DATABASE_ERROR",
        "message": "A database operation failed. The request could not be completed.",
        "details": None,
    }
    if correlation_id:
        error_body["correlation_id"] = correlation_id

    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": error_body},
        headers=headers,
    )


async def generic_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Catch-all for unhandled unexpected exceptions."""
    correlation_id = _get_correlation_id(request)
    logger.error(
        "Unhandled exception processing %s [correlation_id=%s]: %s",
        request.url.path,
        correlation_id,
        str(exc),
        exc_info=True,
    )

    headers = {}
    if correlation_id:
        headers["X-Correlation-ID"] = correlation_id

    error_body = {
        "code": "INTERNAL_SERVER_ERROR",
        "message": "An unexpected server error occurred. Please try again later.",
        "details": None,
    }
    if correlation_id:
        error_body["correlation_id"] = correlation_id

    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": error_body},
        headers=headers,
    )
