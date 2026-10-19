"""005_add_incident_management

Revision ID: 005_add_incident_management
Revises: 004_add_threat_intel_expires_at
Create Date: 2026-10-14 16:30:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from app.database.types import JSONBCompat

# revision identifiers, used by Alembic.
revision: str = '005_add_incident_management'
down_revision: Union[str, None] = '004_add_threat_intel_expires_at'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. Create incidents table
    op.create_table(
        'incidents',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('title', sa.String(length=255), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('severity', sa.String(length=16), nullable=False, server_default='MEDIUM'),
        sa.Column('status', sa.String(length=32), nullable=False, server_default='NEW'),
        sa.Column('category', sa.String(length=32), nullable=False, server_default='UNKNOWN'),
        sa.Column('assigned_admin_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('created_by_admin_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('triaged_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('contained_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('resolved_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('closed_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('resolution_summary', sa.Text(), nullable=True),
        sa.Column('metadata_json', JSONBCompat, nullable=True),
    )
    op.create_index('ix_incidents_title', 'incidents', ['title'])
    op.create_index('ix_incidents_severity', 'incidents', ['severity'])
    op.create_index('ix_incidents_status', 'incidents', ['status'])
    op.create_index('ix_incidents_category', 'incidents', ['category'])
    op.create_index('ix_incidents_assigned_admin_id', 'incidents', ['assigned_admin_id'])
    op.create_index('ix_incidents_created_at', 'incidents', ['created_at'])

    # 2. Create incident_threat_links table
    op.create_table(
        'incident_threat_links',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('incident_id', sa.String(length=36), sa.ForeignKey('incidents.id', ondelete='CASCADE'), nullable=False),
        sa.Column('threat_event_id', sa.String(length=36), sa.ForeignKey('threat_events.id', ondelete='CASCADE'), nullable=False),
        sa.Column('relationship_type', sa.String(length=32), nullable=False, server_default='LIKELY'),
        sa.Column('correlation_signal', sa.String(length=64), nullable=True),
        sa.Column('linked_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('linked_by_admin_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.UniqueConstraint('incident_id', 'threat_event_id', name='uq_incident_threat_link')
    )
    op.create_index('ix_incident_threat_links_incident_id', 'incident_threat_links', ['incident_id'])
    op.create_index('ix_incident_threat_links_threat_event_id', 'incident_threat_links', ['threat_event_id'])

    # 3. Create incident_notes table
    op.create_table(
        'incident_notes',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('incident_id', sa.String(length=36), sa.ForeignKey('incidents.id', ondelete='CASCADE'), nullable=False),
        sa.Column('author_user_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='CASCADE'), nullable=False),
        sa.Column('note_type', sa.String(length=32), nullable=False, server_default='OBSERVATION'),
        sa.Column('content', sa.Text(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('ix_incident_notes_incident_id', 'incident_notes', ['incident_id'])
    op.create_index('ix_incident_notes_author_user_id', 'incident_notes', ['author_user_id'])

    # 4. Create incident_events table
    op.create_table(
        'incident_events',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('incident_id', sa.String(length=36), sa.ForeignKey('incidents.id', ondelete='CASCADE'), nullable=False),
        sa.Column('actor_user_id', sa.String(length=36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('event_type', sa.String(length=64), nullable=False),
        sa.Column('previous_state', sa.String(length=64), nullable=True),
        sa.Column('new_state', sa.String(length=64), nullable=True),
        sa.Column('summary', sa.String(length=512), nullable=False),
        sa.Column('details_json', JSONBCompat, nullable=True),
        sa.Column('timestamp', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('ix_incident_events_incident_id', 'incident_events', ['incident_id'])
    op.create_index('ix_incident_events_event_type', 'incident_events', ['event_type'])
    op.create_index('ix_incident_events_timestamp', 'incident_events', ['timestamp'])


def downgrade() -> None:
    op.drop_table('incident_events')
    op.drop_table('incident_notes')
    op.drop_table('incident_threat_links')
    op.drop_table('incidents')
