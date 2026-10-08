from typing import List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import select
from app.models.model_info import ModelInfo
from app.repositories.base import BaseRepository


class ModelRepository(BaseRepository[ModelInfo]):
    def __init__(self, db: Session):
        super().__init__(ModelInfo, db)

    def get_by_name_and_version(self, name: str, version: str) -> Optional[ModelInfo]:
        stmt = select(ModelInfo).where(
            ModelInfo.name == name,
            ModelInfo.version == version
        )
        return self.db.scalars(stmt).first()

    def list_active(self) -> List[ModelInfo]:
        stmt = select(ModelInfo).where(ModelInfo.status == "ACTIVE").order_by(ModelInfo.name)
        return list(self.db.scalars(stmt).all())
