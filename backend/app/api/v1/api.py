from fastapi import APIRouter
from app.api.v1.auth import router as auth_router
from app.api.v1.users import router as users_router
from app.api.v1.detections import router as detections_router
from app.api.v1.feedback import router as feedback_router
from app.api.v1.analytics import router as analytics_router
from app.api.v1.models import router as models_router
from app.api.v1.admin import router as admin_router

api_v1_router = APIRouter(prefix="/v1")

api_v1_router.include_router(auth_router)
api_v1_router.include_router(users_router)
api_v1_router.include_router(detections_router)
api_v1_router.include_router(feedback_router)
api_v1_router.include_router(analytics_router)
api_v1_router.include_router(models_router)
api_v1_router.include_router(admin_router)
