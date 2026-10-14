import logging
from typing import Optional
from fastapi import Request, status
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse
from app.config import settings

logger = logging.getLogger(__name__)

# Maximum allowed JSON request payload size: 2 Megabytes (2 * 1024 * 1024 bytes)
DEFAULT_MAX_REQUEST_SIZE_BYTES = 2 * 1024 * 1024


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """
    Applies defense-in-depth OWASP recommended security headers to all HTTP responses.
    Prevents MIME sniffing, clickjacking, XSS exploitation, and unauthorized embedding.
    """
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)

        # 1. Prevent MIME-type sniffing
        response.headers["X-Content-Type-Options"] = "nosniff"

        # 2. Prevent clickjacking / framing
        response.headers["X-Frame-Options"] = "DENY"

        # 3. Legacy XSS filter activation
        response.headers["X-XSS-Protection"] = "1; mode=block"

        # 4. Strict referrer policy
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"

        # 5. Disable unused browser device features for API
        response.headers["Permissions-Policy"] = "geolocation=(), camera=(), microphone=(), payment=()"

        # 6. Content Security Policy for API surface
        response.headers["Content-Security-Policy"] = "default-src 'self'; frame-ancestors 'none';"

        # 7. Strict Transport Security (HSTS) in production or over HTTPS
        is_https = request.url.scheme == "https" or request.headers.get("x-forwarded-proto") == "https"
        if is_https or settings.environment == "production":
            response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains; preload"

        return response


class RequestSizeLimitMiddleware(BaseHTTPMiddleware):
    """
    Protects API against memory exhaustion and Denial-of-Service by rejecting oversized request bodies.
    """
    def __init__(self, app, max_size_bytes: int = DEFAULT_MAX_REQUEST_SIZE_BYTES):
        super().__init__(app)
        self.max_size_bytes = max_size_bytes

    async def dispatch(self, request: Request, call_next):
        content_length_header: Optional[str] = request.headers.get("content-length")
        if content_length_header:
            try:
                content_length = int(content_length_header)
                if content_length > self.max_size_bytes:
                    logger.warning(
                        f"Blocked oversized request to {request.url.path} from "
                        f"{request.client.host if request.client else 'unknown'}: {content_length} bytes"
                    )
                    status_code = getattr(status, "HTTP_413_CONTENT_TOO_LARGE", 413)
                    return JSONResponse(
                        status_code=status_code,
                        content={
                            "error": {
                                "code": "PAYLOAD_TOO_LARGE",
                                "message": f"Request payload ({content_length} bytes) exceeds maximum permitted size of {self.max_size_bytes} bytes.",
                                "details": None
                            }
                        }
                    )
            except ValueError:
                pass

        return await call_next(request)
