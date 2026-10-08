from typing import List, Optional
from sqlalchemy.orm import Session
from app.models.model_info import ModelInfo
from app.schemas.model_info import ModelRegisterRequest
from app.repositories.model_repo import ModelRepository
from app.repositories.audit_repo import AuditRepository


class ModelService:
    def __init__(self, db: Session):
        self.db = db
        self.model_repo = ModelRepository(db)
        self.audit_repo = AuditRepository(db)

    def list_active_models(self) -> List[ModelInfo]:
        return self.model_repo.list_active()

    def get_model(self, model_id: str) -> Optional[ModelInfo]:
        return self.model_repo.get_by_id(model_id)

    def register_model(
        self,
        request: ModelRegisterRequest,
        actor_user_id: Optional[str] = None,
        ip_address: Optional[str] = None
    ) -> ModelInfo:
        existing = self.model_repo.get_by_name_and_version(request.name, request.version)
        if existing:
            existing.status = request.status
            existing.artifact_hash = request.artifact_hash
            existing.metadata_json = request.metadata_json
            self.db.flush()
            model = existing
        else:
            model = ModelInfo(
                name=request.name,
                version=request.version,
                type=request.type,
                status=request.status,
                artifact_hash=request.artifact_hash,
                metadata_json=request.metadata_json
            )
            self.model_repo.create(model)

        self.audit_repo.log(
            action="MODEL_REGISTERED",
            resource_type="MODEL",
            actor_user_id=actor_user_id,
            resource_id=model.id,
            ip_address=ip_address,
            metadata={"name": model.name, "version": model.version, "status": model.status}
        )
        return model
