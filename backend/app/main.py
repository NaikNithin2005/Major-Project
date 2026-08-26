from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings

app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="Backend API service for Real-Time AI/ML-Based Quishing & Smishing Detection System"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": settings.app_name,
        "version": settings.version,
        "phase": "Phase 0 - Foundation"
    }

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "environment": settings.environment
    }
