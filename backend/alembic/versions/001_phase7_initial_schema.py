"""001_phase7_initial_schema

Revision ID: 001_phase7_initial_schema
Revises: 
Create Date: 2026-10-07 19:54:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '001_phase7_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. users table
    op.create_table(
        'users',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('firebase_uid', sa.String(length=128), nullable=False),
        sa.Column('email', sa.String(length=255), nullable=False),
        sa.Column('display_name', sa.String(length=255), nullable=True),
        sa.Column('role', sa.String(length=32), server_default='USER', nullable=False),
        sa.Column('is_active', sa.Boolean(), server_default='true', nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('last_seen_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('email'),
        sa.UniqueConstraint('firebase_uid')
    )
    op.create_index('ix_users_email', 'users', ['email'])
    op.create_index('ix_users_firebase_uid', 'users', ['firebase_uid'])

    # 2. devices table
    op.create_table(
        'devices',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('device_identifier', sa.String(length=128), nullable=False),
        sa.Column('platform', sa.String(length=32), server_default='ANDROID', nullable=False),
        sa.Column('app_version', sa.String(length=32), server_default='1.0.0', nullable=False),
        sa.Column('model_version', sa.String(length=64), server_default='v2.0.0', nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('last_sync_at', sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id', 'device_identifier', name='uq_user_device')
    )
    op.create_index('ix_devices_user_id', 'devices', ['user_id'])
    op.create_index('ix_devices_device_identifier', 'devices', ['device_identifier'])

    # 3. threat_events table
    op.create_table(
        'threat_events',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=True),
        sa.Column('device_id', sa.String(length=36), nullable=True),
        sa.Column('event_type', sa.String(length=16), nullable=False),
        sa.Column('risk_score', sa.Integer(), nullable=False),
        sa.Column('threat_category', sa.String(length=16), nullable=False),
        sa.Column('recommended_action', sa.String(length=32), nullable=False),
        sa.Column('timestamp', sa.DateTime(timezone=True), nullable=False),
        sa.Column('model_versions', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('model_results', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('rule_results', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('context_results', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('reasons', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('url_metadata', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('qr_metadata', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('related_threat_id', sa.String(length=36), nullable=True),
        sa.Column('campaign_id', sa.String(length=64), nullable=True),
        sa.Column('status', sa.String(length=32), server_default='REPORTED', nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint('risk_score >= 0 AND risk_score <= 100', name='chk_threat_event_risk_score'),
        sa.ForeignKeyConstraint(['device_id'], ['devices.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='SET NULL'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('ix_threat_events_user_id', 'threat_events', ['user_id'])
    op.create_index('ix_threat_events_device_id', 'threat_events', ['device_id'])
    op.create_index('ix_threat_events_event_type', 'threat_events', ['event_type'])
    op.create_index('ix_threat_events_threat_category', 'threat_events', ['threat_category'])
    op.create_index('ix_threat_events_risk_score', 'threat_events', ['risk_score'])
    op.create_index('ix_threat_events_timestamp', 'threat_events', ['timestamp'])
    op.create_index('ix_threat_events_status', 'threat_events', ['status'])
    op.create_index('ix_threat_events_campaign_id', 'threat_events', ['campaign_id'])

    # 4. feedback table
    op.create_table(
        'feedback',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('threat_event_id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('classification', sa.String(length=32), nullable=False),
        sa.Column('comment', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['threat_event_id'], ['threat_events.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('ix_feedback_threat_event_id', 'feedback', ['threat_event_id'])
    op.create_index('ix_feedback_user_id', 'feedback', ['user_id'])

    # 5. model_info table
    op.create_table(
        'model_info',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('name', sa.String(length=64), nullable=False),
        sa.Column('version', sa.String(length=32), nullable=False),
        sa.Column('type', sa.String(length=32), nullable=False),
        sa.Column('status', sa.String(length=32), server_default='ACTIVE', nullable=False),
        sa.Column('artifact_hash', sa.String(length=64), nullable=True),
        sa.Column('metadata_json', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('name', 'version', name='uq_model_name_version')
    )
    op.create_index('ix_model_info_name', 'model_info', ['name'])
    op.create_index('ix_model_info_status', 'model_info', ['status'])

    # 6. audit_logs table
    op.create_table(
        'audit_logs',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('actor_user_id', sa.String(length=36), nullable=True),
        sa.Column('action', sa.String(length=64), nullable=False),
        sa.Column('resource_type', sa.String(length=64), nullable=False),
        sa.Column('resource_id', sa.String(length=64), nullable=True),
        sa.Column('timestamp', sa.DateTime(timezone=True), nullable=False),
        sa.Column('ip_address', sa.String(length=45), nullable=True),
        sa.Column('metadata_json', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.ForeignKeyConstraint(['actor_user_id'], ['users.id'], ondelete='SET NULL'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('ix_audit_logs_actor_user_id', 'audit_logs', ['actor_user_id'])
    op.create_index('ix_audit_logs_action', 'audit_logs', ['action'])
    op.create_index('ix_audit_logs_resource_type', 'audit_logs', ['resource_type'])
    op.create_index('ix_audit_logs_resource_id', 'audit_logs', ['resource_id'])
    op.create_index('ix_audit_logs_timestamp', 'audit_logs', ['timestamp'])

    # 7. threat_intel table
    op.create_table(
        'threat_intel',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('indicator_type', sa.String(length=32), nullable=False),
        sa.Column('indicator_value', sa.String(length=512), nullable=False),
        sa.Column('threat_type', sa.String(length=32), nullable=False),
        sa.Column('confidence', sa.Float(), server_default='1.0', nullable=False),
        sa.Column('source', sa.String(length=64), server_default='INTERNAL', nullable=False),
        sa.Column('first_seen', sa.DateTime(timezone=True), nullable=False),
        sa.Column('last_seen', sa.DateTime(timezone=True), nullable=False),
        sa.Column('status', sa.String(length=32), server_default='ACTIVE', nullable=False),
        sa.Column('metadata_json', sa.JSON().with_variant(postgresql.JSONB(), 'postgresql'), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('indicator_type', 'indicator_value', name='uq_intel_indicator')
    )
    op.create_index('ix_threat_intel_indicator_type', 'threat_intel', ['indicator_type'])
    op.create_index('ix_threat_intel_indicator_value', 'threat_intel', ['indicator_value'])
    op.create_index('ix_threat_intel_status', 'threat_intel', ['status'])


def downgrade() -> None:
    op.drop_table('threat_intel')
    op.drop_table('audit_logs')
    op.drop_table('model_info')
    op.drop_table('feedback')
    op.drop_table('threat_events')
    op.drop_table('devices')
    op.drop_table('users')
