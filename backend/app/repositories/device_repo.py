from typing import Optional, List
from sqlalchemy.orm import Session
from sqlalchemy import select
from app.models.device import Device
from app.repositories.base import BaseRepository


class DeviceRepository(BaseRepository[Device]):
    def __init__(self, db: Session):
        super().__init__(Device, db)

    def get_by_identifier(self, user_id: str, device_identifier: str) -> Optional[Device]:
        stmt = select(Device).where(
            Device.user_id == user_id,
            Device.device_identifier == device_identifier
        )
        return self.db.scalars(stmt).first()

    def list_by_user(self, user_id: str) -> List[Device]:
        stmt = select(Device).where(Device.user_id == user_id).order_by(Device.created_at.desc())
        return list(self.db.scalars(stmt).all())
