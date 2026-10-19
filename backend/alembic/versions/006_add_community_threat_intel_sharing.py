"""006_add_community_threat_intel_sharing

Revision ID: 006_add_community_threat_intel_sharing
Revises: 005_add_incident_management
Create Date: 2026-10-15 13:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from app.database.types import JSONBCompat

# revision identifiers, used by Alembic.
revision: str = '006_add_community_threat_intel_sharing'
down_revision: Union[str, None] = '005_add_incident_management'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. Add verification and lifecycle columns to threat_intel table
    with op.batch_alter_table('threat_intel') as batch_op:
        batch_op.add_column(sa.Column('verification_status', sa.String(length=32), nullable=False, server_default='VERIFIED'))
        batch_op.add_column(sa.Column('revocation_reason', sa.String(length=512), nullable=True))
        batch_op.add_column(sa.Column('promoted_from_report_id', sa.String(length=36), nullable=True))
        batch_op.create_index('ix_threat_intel_verification_status', ['verification_status'])

    # 2. Create user_sharing_preferences table (opt-in by default)
    op.create_table(
        'user_sharing_preferences',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('user_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='CASCADE'), nullable=False),
        sa.Column('is_sharing_enabled', sa.Boolean(), nullable=False, server_default=sa.text('0')),
        sa.Column('consent_version', sa.String(length=16), nullable=False, server_default='v1.0'),
        sa.Column('consent_timestamp', sa.DateTime(timezone=True), nullable=True),
        sa.Column('allow_campaign_correlation', sa.Boolean(), nullable=False, server_default=sa.text('1')),
        sa.Column('withdrawn_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint('user_id', name='uq_user_sharing_preferences_user_id')
    )
    op.create_index('ix_user_sharing_preferences_user_id', 'user_sharing_preferences', ['user_id'])

    # 3. Create community_threat_reports table
    op.create_table(
        'community_threat_reports',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('reporter_user_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('anonymous_submission_id', sa.String(length=36), nullable=False),
        sa.Column('indicator_type', sa.String(length=32), nullable=False),
        sa.Column('indicator_value', sa.String(length=512), nullable=False),
        sa.Column('indicator_hash', sa.String(length=64), nullable=False),
        sa.Column('threat_category', sa.String(length=32), nullable=False),
        sa.Column('user_risk_score', sa.Integer(), nullable=False),
        sa.Column('model_verdicts', JSONBCompat, nullable=True),
        sa.Column('context_metadata', JSONBCompat, nullable=True),
        sa.Column('status', sa.String(length=32), nullable=False, server_default='PENDING_REVIEW'),
        sa.Column('consensus_score', sa.Float(), nullable=False, server_default='0.0'),
        sa.Column('corroboration_count', sa.Integer(), nullable=False, server_default='1'),
        sa.Column('admin_notes', sa.Text(), nullable=True),
        sa.Column('reviewed_by_admin_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('reviewed_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('promoted_indicator_id', sa.String(length=36), sa.ForeignKey('threat_intel.id', ondelete='SET NULL'), nullable=True),
        sa.Column('submitted_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('ip_origin_masked', sa.String(length=64), nullable=True),
        sa.UniqueConstraint('anonymous_submission_id', name='uq_community_reports_anon_id')
    )
    op.create_index('ix_community_threat_reports_reporter_user_id', 'community_threat_reports', ['reporter_user_id'])
    op.create_index('ix_community_threat_reports_anon_id', 'community_threat_reports', ['anonymous_submission_id'])
    op.create_index('ix_community_threat_reports_indicator_type', 'community_threat_reports', ['indicator_type'])
    op.create_index('ix_community_threat_reports_indicator_value', 'community_threat_reports', ['indicator_value'])
    op.create_index('ix_community_threat_reports_indicator_hash', 'community_threat_reports', ['indicator_hash'])
    op.create_index('ix_community_threat_reports_threat_category', 'community_threat_reports', ['threat_category'])
    op.create_index('ix_community_threat_reports_status', 'community_threat_reports', ['status'])


def downgrade() -> None:
    # Drop community_threat_reports
    op.drop_index('ix_community_threat_reports_status', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_threat_category', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_indicator_hash', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_indicator_value', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_indicator_type', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_anon_id', table_name='community_threat_reports')
    op.drop_index('ix_community_threat_reports_reporter_user_id', table_name='community_threat_reports')
    op.drop_table('community_threat_reports')

    # Drop user_sharing_preferences
    op.drop_index('ix_user_sharing_preferences_user_id', table_name='user_sharing_preferences')
    op.drop_table('user_sharing_preferences')

    # Drop threat_intel added columns
    with op.batch_alter_table('threat_intel') as batch_op:
        batch_op.drop_index('ix_threat_intel_verification_status')
        batch_op.drop_column('promoted_from_report_id')
        batch_op.drop_column('revocation_reason')
        batch_op.drop_column('verification_status')
