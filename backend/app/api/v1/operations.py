import time
import os
import psutil
from typing import Dict, Any
from fastapi import APIRouter, Depends, Request, status
from sqlalchemy.orm import Session

from app.database.session import get_db, check_db_health, get_db_pool_status
from app.models.user import User
from app.auth.dependencies import require_admin
from app.auth.firebase import get_auth_system_status
from app.services.model_integrity_service import ModelIntegrityService
from app.services.detection_service import DetectionService
from app.services.audit_service import AuditService
from app.config import settings

router = APIRouter(prefix="/admin/operations", tags=["Operations & Maintenance"])

_PROCESS_START_TIME = time.time()


@router.get("/status")
def get_system_operational_status(
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
) -> Dict[str, Any]:
    """
    Comprehensive operational health and maintenance diagnostic report.
    Protected strictly by server-side ADMIN authorization.
    """
    uptime_seconds = round(time.time() - _PROCESS_START_TIME, 1)
    db_connected = check_db_health()
    db_pool = get_db_pool_status()
    auth_status = get_auth_system_status()
    model_status = ModelIntegrityService.verify_all_models()

    detection_service = DetectionService(db)
    sync_metrics = detection_service.get_sync_reliability_metrics()

    audit_service = AuditService(db)
    security_summary = audit_service.get_security_summary()

    # Memory / process stats if psutil available
    proc_info = {}
    try:
        process = psutil.Process()
        proc_info = {
            "memory_rss_mb": round(process.memory_info().rss / (1024 * 1024), 2),
            "cpu_percent": process.cpu_percent(interval=0.0),
            "threads_count": process.num_threads(),
        }
    except Exception:
        proc_info = {"status": "psutil_unavailable"}

    is_healthy = db_connected and model_status.get("all_healthy", True)

    return {
        "status": "HEALTHY" if is_healthy else "DEGRADED",
        "service": settings.app_name,
        "version": settings.version,
        "environment": settings.environment,
        "uptime_seconds": uptime_seconds,
        "process": proc_info,
        "database": {
            "connected": db_connected,
            "pool_status": db_pool,
        },
        "authentication": auth_status,
        "models": {
            "status": model_status.get("status"),
            "all_healthy": model_status.get("all_healthy"),
            "verified_count": model_status.get("verified_count"),
            "total_models": model_status.get("total_models"),
            "details": model_status.get("models"),
        },
        "synchronization": sync_metrics,
        "security_monitoring": security_summary,
    }


@router.post("/verify-models")
def trigger_model_integrity_verification(
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
) -> Dict[str, Any]:
    """
    Trigger manual cryptographic SHA-256 verification of all AI model artifacts on disk.
    Records an administrative audit log.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    audit_service = AuditService(db)
    result = ModelIntegrityService.verify_all_models()

    audit_service.audit_repo.log(
        action="MODEL_INTEGRITY_VERIFIED",
        resource_type="AI_MODELS",
        actor_user_id=admin.id,
        resource_id="MODEL_MANIFEST",
        ip_address=client_ip,
        metadata={
            "all_healthy": result.get("all_healthy"),
            "verified_count": result.get("verified_count"),
            "total_models": result.get("total_models"),
        }
    )
    return result


@router.get("/sync-metrics")
def get_synchronization_reliability_metrics(
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
) -> Dict[str, Any]:
    """
    Fetch granular synchronization reliability and idempotency metrics.
    """
    detection_service = DetectionService(db)
    return detection_service.get_sync_reliability_metrics()
