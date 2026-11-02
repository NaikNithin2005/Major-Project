from typing import List, Optional
from sqlalchemy.orm import Session
from app.models.model_info import ModelInfo
from app.models.user import User
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

    def approve_model_version(self, model_id: str, admin_user: User) -> ModelInfo:
        target = self.get_model(model_id)
        if not target:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "MODEL_NOT_FOUND", "message": f"Model {model_id} not found."}
            )

        # Deprecate current active version of same name
        actives = self.db.query(ModelInfo).filter(
            ModelInfo.name == target.name,
            ModelInfo.status == "ACTIVE",
            ModelInfo.id != target.id
        ).all()
        for a in actives:
            a.status = "SUPERSEDED"

        target.status = "ACTIVE"
        self.db.commit()
        self.db.refresh(target)

        self.audit_repo.log(
            action="MODEL_PROMOTED",
            resource_type="MODEL",
            actor_user_id=admin_user.id,
            resource_id=target.id,
            metadata={"name": target.name, "version": target.version, "promoted_to": "ACTIVE"}
        )
        return target

    def rollback_model(self, model_id: str, admin_user: User) -> ModelInfo:
        current = self.get_model(model_id)
        if not current:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "MODEL_NOT_FOUND", "message": f"Model {model_id} not found."}
            )

        # Find previous version
        prev = self.db.query(ModelInfo).filter(
            ModelInfo.name == current.name,
            ModelInfo.id != current.id
        ).order_by(ModelInfo.created_at.desc()).first()

        if not prev:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "NO_ROLLBACK_CANDIDATE", "message": "No earlier model version available for rollback."}
            )

        current.status = "ROLLED_BACK"
        prev.status = "ACTIVE"
        self.db.commit()
        self.db.refresh(prev)

        self.audit_repo.log(
            action="MODEL_ROLLED_BACK",
            resource_type="MODEL",
            actor_user_id=admin_user.id,
            resource_id=prev.id,
            metadata={"restored_version": prev.version, "demoted_version": current.version}
        )
        return prev
