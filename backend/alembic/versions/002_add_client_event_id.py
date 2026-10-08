"""002_add_client_event_id

Revision ID: 002_add_client_event_id
Revises: 001_phase7_initial_schema
Create Date: 2026-10-07 21:35:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = '002_add_client_event_id'
down_revision: Union[str, None] = '001_phase7_initial_schema'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('threat_events', sa.Column('client_event_id', sa.String(length=64), nullable=True))
    op.create_index('ix_threat_events_client_event_id', 'threat_events', ['client_event_id'])


def downgrade() -> None:
    op.drop_index('ix_threat_events_client_event_id', table_name='threat_events')
    op.drop_column('threat_events', 'client_event_id')
