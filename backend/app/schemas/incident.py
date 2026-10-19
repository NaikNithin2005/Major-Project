"""
Pydantic Schemas for Phase 15: Security Incidents & SOC Investigation.
"""

from datetime import datetime
from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field, ConfigDict


# Enums and Constants
LEGAL_STATUS_TRANSITIONS = {
    "NEW": {"TRIAGED", "INVESTIGATING", "CLOSED"},
    "TRIAGED": {"INVESTIGATING", "CLOSED"},
    "INVESTIGATING": {"CONTAINED", "RESOLVED", "CLOSED"},
    "CONTAINED": {"RESOLVED", "INVESTIGATING"},
    "RESOLVED": {"CLOSED", "INVESTIGATING"},
    "CLOSED": {"INVESTIGATING"}
}

VALID_SEVERITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
VALID_STATUSES = {"NEW", "TRIAGED", "INVESTIGATING", "CONTAINED", "RESOLVED", "CLOSED"}
VALID_CATEGORIES = {
    "QUISHING_CAMPAIGN",
    "SMISHING_SURGE",
    "BRAND_IMPERSONATION",
    "MALICIOUS_INFRASTRUCTURE",
    "ACCOUNT_TAKEOVER_ATTEMPT",
    "UNKNOWN"
}
VALID_RELATIONSHIPS = {"CONFIRMED", "LIKELY", "UNVERIFIED"}


class IncidentCreateRequest(BaseModel):
    title: str = Field(..., min_length=3, max_length=255, description="Descriptive summary of the security incident")
    description: Optional[str] = Field(default=None, description="Detailed initial findings and context")
    severity: str = Field(default="MEDIUM", description="LOW, MEDIUM, HIGH, or CRITICAL")
    category: str = Field(default="UNKNOWN", description="Incident category")
    assigned_admin_id: Optional[str] = Field(default=None, description="Optional ID of assigned administrator")
    seed_threat_event_id: Optional[str] = Field(default=None, description="Optional initial threat event to link to this incident")
    metadata_json: Optional[Dict[str, Any]] = Field(default=None, description="Arbitrary threat tags or campaign context")


class IncidentStatusUpdateRequest(BaseModel):
    status: str = Field(..., description="Target status: TRIAGED, INVESTIGATING, CONTAINED, RESOLVED, CLOSED")
    resolution_summary: Optional[str] = Field(default=None, description="Required when moving to RESOLVED")
    reason: Optional[str] = Field(default=None, description="Explanation for transition or reopening")


class IncidentAssignRequest(BaseModel):
    assigned_admin_id: Optional[str] = Field(default=None, description="Target administrator ID, or null to unassign")
    note: Optional[str] = Field(default=None, description="Assignment guidance or priority instruction")


class IncidentSeverityUpdateRequest(BaseModel):
    severity: str = Field(..., description="LOW, MEDIUM, HIGH, or CRITICAL")
    reason: Optional[str] = Field(default=None, description="Rationale for severity adjustment")


class IncidentNoteCreateRequest(BaseModel):
    content: str = Field(..., min_length=1, description="Investigation findings, analysis, or remediation notes")
    note_type: str = Field(default="OBSERVATION", description="OBSERVATION, ANALYSIS, REMEDIATION, or EVIDENCE_NOTE")


class IncidentThreatLinkRequest(BaseModel):
    threat_event_id: str = Field(..., description="ID of the threat event to link")
    relationship_type: str = Field(default="LIKELY", description="CONFIRMED, LIKELY, or UNVERIFIED")
    correlation_signal: Optional[str] = Field(default=None, description="Signal type (e.g. MATCHING_DOMAIN, MATCHING_URL)")


class AdminUserBrief(BaseModel):
    id: str
    email: str
    display_name: Optional[str] = None
    model_config = ConfigDict(from_attributes=True)


class IncidentNoteResponse(BaseModel):
    id: str
    incident_id: str
    author_user_id: str
    author_name: Optional[str] = None
    note_type: str
    content: str
    created_at: datetime
    updated_at: datetime
    model_config = ConfigDict(from_attributes=True)


class IncidentEventResponse(BaseModel):
    id: str
    incident_id: str
    actor_user_id: Optional[str] = None
    actor_name: Optional[str] = None
    event_type: str
    previous_state: Optional[str] = None
    new_state: Optional[str] = None
    summary: str
    details_json: Optional[Dict[str, Any]] = None
    timestamp: datetime
    model_config = ConfigDict(from_attributes=True)


class LinkedThreatSummary(BaseModel):
    id: str
    event_type: str
    risk_score: int
    threat_category: str
    recommended_action: str
    timestamp: datetime
    url: Optional[str] = None
    domain: Optional[str] = None
    reasons: Optional[List[str]] = None
    user_id: Optional[str] = None
    relationship_type: str
    correlation_signal: Optional[str] = None
    linked_at: datetime


class IncidentResponse(BaseModel):
    id: str
    title: str
    description: Optional[str] = None
    severity: str
    status: str
    category: str
    assigned_admin_id: Optional[str] = None
    assigned_admin_email: Optional[str] = None
    assigned_admin_name: Optional[str] = None
    created_by_admin_id: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    triaged_at: Optional[datetime] = None
    contained_at: Optional[datetime] = None
    resolved_at: Optional[datetime] = None
    closed_at: Optional[datetime] = None
    resolution_summary: Optional[str] = None
    linked_threats_count: int = 0
    notes_count: int = 0
    metadata_json: Optional[Dict[str, Any]] = None
    model_config = ConfigDict(from_attributes=True)


class IncidentDetailResponse(IncidentResponse):
    threat_links: List[LinkedThreatSummary] = []
    notes: List[IncidentNoteResponse] = []
    events: List[IncidentEventResponse] = []


class CorrelationSuggestionItem(BaseModel):
    threat_event_id: str
    event_type: str
    risk_score: int
    threat_category: str
    timestamp: datetime
    matched_indicator: str
    signal: str
    confidence: str  # CONFIRMED, LIKELY, UNVERIFIED
    url: Optional[str] = None
    domain: Optional[str] = None


class CorrelationSuggestionsResponse(BaseModel):
    incident_id: str
    suggestions: List[CorrelationSuggestionItem]
    total_suggested: int


class IncidentMetricsResponse(BaseModel):
    total_incidents: int
    open_incidents: int
    critical_incidents: int
    high_incidents: int
    awaiting_triage_incidents: int
    investigating_incidents: int
    contained_incidents: int
    resolved_incidents: int
    closed_incidents: int
    reopened_incidents: int
    mean_time_to_triage_hours: Optional[float] = None
    mean_time_to_resolve_hours: Optional[float] = None
    severity_breakdown: Dict[str, int]
    status_breakdown: Dict[str, int]
    category_breakdown: Dict[str, int]
    daily_trend: List[Dict[str, Any]]
