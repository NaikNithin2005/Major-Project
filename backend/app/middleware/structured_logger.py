import time
import uuid
import json
import logging
from typing import Callable
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("squish_shield.structured")


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    """
    Middleware that establishes an end-to-end correlation ID for request tracing.
    - Reads incoming X-Correlation-ID or X-Request-ID (or generates a fresh UUIDv4).
    - Sets request.state.correlation_id for downstream route and error handlers.
    - Emits structured operational logs (omitting secrets and PII).
    - Injects X-Correlation-ID into every HTTP response.
    """

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        # Extract or generate correlation ID
        correlation_id = (
            request.headers.get("x-correlation-id")
            or request.headers.get("x-request-id")
            or str(uuid.uuid4())
        )
        request.state.correlation_id = correlation_id

        start_time = time.perf_counter()
        client_ip = request.client.host if request.client else "unknown"

        try:
            response = await call_next(request)
            duration_ms = round((time.perf_counter() - start_time) * 1000, 2)

            # Inbound correlation tracing header
            response.headers["X-Correlation-ID"] = correlation_id

            # Emit privacy-conscious structured log
            log_payload = {
                "correlation_id": correlation_id,
                "method": request.method,
                "path": request.url.path,
                "status_code": response.status_code,
                "duration_ms": duration_ms,
                "client_ip": client_ip,
            }

            # Filter out noisy repetitive probes from log floods
            if request.url.path not in ("/health/live", "/health"):
                if response.status_code >= 500:
                    logger.error(json.dumps(log_payload))
                elif response.status_code >= 400:
                    logger.warning(json.dumps(log_payload))
                else:
                    logger.info(json.dumps(log_payload))

            return response
        except Exception as exc:
            duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
            log_payload = {
                "correlation_id": correlation_id,
                "method": request.method,
                "path": request.url.path,
                "status_code": 500,
                "duration_ms": duration_ms,
                "client_ip": client_ip,
                "exception_type": type(exc).__name__,
            }
            logger.error(json.dumps(log_payload))
            raise exc
