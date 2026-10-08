from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.model_info import ModelInfoResponse
from app.services.model_service import ModelService

router = APIRouter(prefix="/models", tags=["AI Models Metadata"])


@router.get("", response_model=List[ModelInfoResponse])
def list_models(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    List active AI model metadata versions (e.g. TinyBERT, XGBoost, Isolation Forest).
    Read-only for authenticated clients.
    """
    service = ModelService(db)
    models = service.list_active_models()
    return [ModelInfoResponse.model_validate(m) for m in models]


@router.get("/{model_id}", response_model=ModelInfoResponse)
def get_model_metadata(
    model_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get metadata for a specific model version by ID.
    """
    service = ModelService(db)
    model = service.get_model(model_id)
    if not model:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "MODEL_NOT_FOUND", "message": f"Model metadata for {model_id} not found."}
        )
    return ModelInfoResponse.model_validate(model)
