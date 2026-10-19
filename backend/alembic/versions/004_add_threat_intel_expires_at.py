"""004_add_threat_intel_expires_at

Revision ID: 004_add_threat_intel_expires_at
Revises: 003_add_user_action_and_phone
Create Date: 2026-10-14 14:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = '004_add_threat_intel_expires_at'
down_revision: Union[str, None] = '003_add_user_action_and_phone'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add expires_at column and index to threat_intel table for TTL expiration
    op.add_column('threat_intel', sa.Column('expires_at', sa.DateTime(timezone=True), nullable=True))
    op.create_index('ix_threat_intel_expires_at', 'threat_intel', ['expires_at'])


def downgrade() -> None:
    op.drop_index('ix_threat_intel_expires_at', table_name='threat_intel')
    op.drop_column('threat_intel', 'expires_at')
