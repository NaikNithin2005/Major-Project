"""003_add_user_action_and_phone

Revision ID: 003_add_user_action_and_phone
Revises: 002_add_client_event_id
Create Date: 2026-10-13 14:30:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = '003_add_user_action_and_phone'
down_revision: Union[str, None] = '002_add_client_event_id'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. Add phone_number to users table
    op.add_column('users', sa.Column('phone_number', sa.String(length=32), nullable=True))

    # 2. Add user_action and updated_at to threat_events table
    op.add_column('threat_events', sa.Column('user_action', sa.String(length=32), nullable=True))
    op.add_column('threat_events', sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True))
    op.create_index('ix_threat_events_user_action', 'threat_events', ['user_action'])


def downgrade() -> None:
    op.drop_index('ix_threat_events_user_action', table_name='threat_events')
    op.drop_column('threat_events', 'updated_at')
    op.drop_column('threat_events', 'user_action')
    op.drop_column('users', 'phone_number')
