"""
Incident Repository for Phase 15.
Handles database persistence, queries, updates, metrics, and threat correlation for Security Incidents.
"""

import math
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional, Tuple
from sqlalchemy import or_, and_, func, desc
from sqlalchemy.orm import Session, joinedload

from app.models.incident import Incident, IncidentThreatLink, IncidentNote, IncidentEvent
from app.models.threat_event import ThreatEvent
from app.models.user import User
from app.schemas.incident import (
    LEGAL_STATUS_TRANSITIONS,
    VALID_SEVERITIES,
    VALID_STATUSES,
    CorrelationSuggestionItem
)


class IncidentRepository:
    def __init__(self, db: Session):
        self.db = db

    def create_incident(
        self,
        title: str,
        description: Optional[str] = None,
        severity: str = "MEDIUM",
        category: str = "UNKNOWN",
        assigned_admin_id: Optional[str] = None,
        created_by_admin_id: Optional[str] = None,
        metadata_json: Optional[Dict[str, Any]] = None
    ) -> Incident:
        now = datetime.now(timezone.utc)
        incident = Incident(
            title=title.strip(),
            description=description.strip() if description else None,
            severity=severity.upper(),
            status="NEW",
            category=category.upper(),
            assigned_admin_id=assigned_admin_id,
            created_by_admin_id=created_by_admin_id,
            created_at=now,
            updated_at=now,
            metadata_json=metadata_json or {}
        )
        self.db.add(incident)
        self.db.flush()

        # Log initial creation event
        self.log_event(
            incident_id=incident.id,
            actor_user_id=created_by_admin_id,
            event_type="INCIDENT_CREATED",
            summary=f"Incident '{incident.title}' created with severity {incident.severity}.",
            new_state="NEW",
            details_json={"category": incident.category, "severity": incident.severity}
        )
        self.db.commit()
        self.db.refresh(incident)
        return incident

    def get_by_id(self, incident_id: str) -> Optional[Incident]:
        return (
            self.db.query(Incident)
            .options(
                joinedload(Incident.assigned_admin),
                joinedload(Incident.threat_links).joinedload(IncidentThreatLink.threat_event),
                joinedload(Incident.notes).joinedload(IncidentNote.author),
                joinedload(Incident.events).joinedload(IncidentEvent.actor)
            )
            .filter(Incident.id == incident_id)
            .first()
        )

    def list_incidents(
        self,
        skip: int = 0,
        limit: int = 20,
        status: Optional[str] = None,
        severity: Optional[str] = None,
        category: Optional[str] = None,
        assigned_admin_id: Optional[str] = None,
        search: Optional[str] = None,
        start_date: Optional[datetime] = None,
        end_date: Optional[datetime] = None
    ) -> Tuple[List[Incident], int]:
        query = (
            self.db.query(Incident)
            .options(
                joinedload(Incident.assigned_admin),
                joinedload(Incident.threat_links)
            )
        )

        if status:
            query = query.filter(Incident.status == status.upper())
        if severity:
            query = query.filter(Incident.severity == severity.upper())
        if category:
            query = query.filter(Incident.category == category.upper())
        if assigned_admin_id:
            query = query.filter(Incident.assigned_admin_id == assigned_admin_id)
        if start_date:
            query = query.filter(Incident.created_at >= start_date)
        if end_date:
            query = query.filter(Incident.created_at <= end_date)
        if search:
            pattern = f"%{search.strip()}%"
            query = query.filter(
                or_(
                    Incident.title.ilike(pattern),
                    Incident.description.ilike(pattern),
                    Incident.id.ilike(pattern)
                )
            )

        total = query.count()
        items = query.order_by(desc(Incident.created_at)).offset(skip).limit(limit).all()
        return items, total

    def update_status(
        self,
        incident: Incident,
        new_status: str,
        actor_admin_id: Optional[str] = None,
        resolution_summary: Optional[str] = None,
        reason: Optional[str] = None
    ) -> Incident:
        now = datetime.now(timezone.utc)
        prev_status = incident.status
        target_status = new_status.upper()

        incident.status = target_status
        incident.updated_at = now

        # State timestamps
        if target_status == "TRIAGED" and not incident.triaged_at:
            incident.triaged_at = now
        elif target_status == "CONTAINED" and not incident.contained_at:
            incident.contained_at = now
        elif target_status == "RESOLVED":
            incident.resolved_at = now
            if resolution_summary:
                incident.resolution_summary = resolution_summary.strip()
        elif target_status == "CLOSED":
            incident.closed_at = now
        elif prev_status in ("RESOLVED", "CLOSED") and target_status == "INVESTIGATING":
            # Reopening
            incident.resolved_at = None
            incident.closed_at = None

        event_type = "REOPENED" if prev_status in ("RESOLVED", "CLOSED") and target_status == "INVESTIGATING" else "STATUS_CHANGE"
        summary_text = f"Status changed from {prev_status} to {target_status}."
        if reason:
            summary_text += f" Reason: {reason}"
        if resolution_summary:
            summary_text += f" Resolution: {resolution_summary}"

        self.log_event(
            incident_id=incident.id,
            actor_user_id=actor_admin_id,
            event_type=event_type,
            summary=summary_text,
            previous_state=prev_status,
            new_state=target_status,
            details_json={"reason": reason, "resolution_summary": resolution_summary}
        )

        self.db.commit()
        self.db.refresh(incident)
        return incident

    def assign_admin(
        self,
        incident: Incident,
        target_admin_id: Optional[str],
        actor_admin_id: Optional[str] = None,
        note: Optional[str] = None
    ) -> Incident:
        now = datetime.now(timezone.utc)
        prev_admin_id = incident.assigned_admin_id
        incident.assigned_admin_id = target_admin_id
        incident.updated_at = now

        admin_user = self.db.query(User).filter(User.id == target_admin_id).first() if target_admin_id else None
        target_name = admin_user.email if admin_user else "Unassigned"

        self.log_event(
            incident_id=incident.id,
            actor_user_id=actor_admin_id,
            event_type="ASSIGNMENT_CHANGE",
            summary=f"Incident assigned to {target_name}." if target_admin_id else "Incident unassigned.",
            previous_state=prev_admin_id,
            new_state=target_admin_id,
            details_json={"note": note, "assignee_email": target_name}
        )

        self.db.commit()
        self.db.refresh(incident)
        return incident

    def update_severity(
        self,
        incident: Incident,
        new_severity: str,
        actor_admin_id: Optional[str] = None,
        reason: Optional[str] = None
    ) -> Incident:
        now = datetime.now(timezone.utc)
        prev_severity = incident.severity
        incident.severity = new_severity.upper()
        incident.updated_at = now

        summary = f"Severity adjusted from {prev_severity} to {incident.severity}."
        if reason:
            summary += f" Reason: {reason}"

        self.log_event(
            incident_id=incident.id,
            actor_user_id=actor_admin_id,
            event_type="SEVERITY_CHANGE",
            summary=summary,
            previous_state=prev_severity,
            new_state=incident.severity,
            details_json={"reason": reason}
        )

        self.db.commit()
        self.db.refresh(incident)
        return incident

    def link_threat(
        self,
        incident_id: str,
        threat_event_id: str,
        relationship_type: str = "LIKELY",
        correlation_signal: Optional[str] = None,
        actor_admin_id: Optional[str] = None
    ) -> IncidentThreatLink:
        # Check if already linked
        existing = (
            self.db.query(IncidentThreatLink)
            .filter(
                IncidentThreatLink.incident_id == incident_id,
                IncidentThreatLink.threat_event_id == threat_event_id
            )
            .first()
        )
        if existing:
            return existing

        now = datetime.now(timezone.utc)
        link = IncidentThreatLink(
            incident_id=incident_id,
            threat_event_id=threat_event_id,
            relationship_type=relationship_type.upper(),
            correlation_signal=correlation_signal,
            linked_at=now,
            linked_by_admin_id=actor_admin_id
        )
        self.db.add(link)
        self.db.flush()

        # Update incident updated_at
        incident = self.db.query(Incident).filter(Incident.id == incident_id).first()
        if incident:
            incident.updated_at = now

        self.log_event(
            incident_id=incident_id,
            actor_user_id=actor_admin_id,
            event_type="THREAT_LINKED",
            summary=f"Linked threat event {threat_event_id} ({relationship_type.upper()}).",
            details_json={"threat_event_id": threat_event_id, "relationship_type": relationship_type, "signal": correlation_signal}
        )

        self.db.commit()
        self.db.refresh(link)
        return link

    def unlink_threat(
        self,
        incident_id: str,
        threat_event_id: str,
        actor_admin_id: Optional[str] = None
    ) -> bool:
        link = (
            self.db.query(IncidentThreatLink)
            .filter(
                IncidentThreatLink.incident_id == incident_id,
                IncidentThreatLink.threat_event_id == threat_event_id
            )
            .first()
        )
        if not link:
            return False

        self.db.delete(link)
        now = datetime.now(timezone.utc)
        incident = self.db.query(Incident).filter(Incident.id == incident_id).first()
        if incident:
            incident.updated_at = now

        self.log_event(
            incident_id=incident_id,
            actor_user_id=actor_admin_id,
            event_type="THREAT_UNLINKED",
            summary=f"Unlinked threat event {threat_event_id}.",
            details_json={"threat_event_id": threat_event_id}
        )

        self.db.commit()
        return True

    def add_note(
        self,
        incident_id: str,
        author_user_id: str,
        content: str,
        note_type: str = "OBSERVATION"
    ) -> IncidentNote:
        now = datetime.now(timezone.utc)
        note = IncidentNote(
            incident_id=incident_id,
            author_user_id=author_user_id,
            note_type=note_type.upper(),
            content=content.strip(),
            created_at=now,
            updated_at=now
        )
        self.db.add(note)
        self.db.flush()

        incident = self.db.query(Incident).filter(Incident.id == incident_id).first()
        if incident:
            incident.updated_at = now

        self.log_event(
            incident_id=incident_id,
            actor_user_id=author_user_id,
            event_type="NOTE_ADDED",
            summary=f"Added {note_type.upper()} note.",
            details_json={"note_type": note_type, "note_id": note.id}
        )

        self.db.commit()
        self.db.refresh(note)
        return note

    def log_event(
        self,
        incident_id: str,
        actor_user_id: Optional[str],
        event_type: str,
        summary: str,
        previous_state: Optional[str] = None,
        new_state: Optional[str] = None,
        details_json: Optional[Dict[str, Any]] = None
    ) -> IncidentEvent:
        event = IncidentEvent(
            incident_id=incident_id,
            actor_user_id=actor_user_id,
            event_type=event_type,
            summary=summary,
            previous_state=previous_state,
            new_state=new_state,
            details_json=details_json or {},
            timestamp=datetime.now(timezone.utc)
        )
        self.db.add(event)
        return event

    def get_events(self, incident_id: str) -> List[IncidentEvent]:
        return (
            self.db.query(IncidentEvent)
            .options(joinedload(IncidentEvent.actor))
            .filter(IncidentEvent.incident_id == incident_id)
            .order_by(desc(IncidentEvent.timestamp))
            .all()
        )

    def calculate_metrics(self) -> Dict[str, Any]:
        """
        Compute high-fidelity SOC Incident metrics:
        - MTTT (Mean Time to Triage)
        - MTTR (Mean Time to Resolution)
        - Active count breakdown
        - Trend analysis
        """
        total = self.db.query(Incident).count()
        open_count = self.db.query(Incident).filter(Incident.status.notin_(["RESOLVED", "CLOSED"])).count()
        critical_count = self.db.query(Incident).filter(Incident.severity == "CRITICAL", Incident.status.notin_(["RESOLVED", "CLOSED"])).count()
        high_count = self.db.query(Incident).filter(Incident.severity == "HIGH", Incident.status.notin_(["RESOLVED", "CLOSED"])).count()
        awaiting_triage = self.db.query(Incident).filter(Incident.status == "NEW").count()
        investigating = self.db.query(Incident).filter(Incident.status == "INVESTIGATING").count()
        contained = self.db.query(Incident).filter(Incident.status == "CONTAINED").count()
        resolved = self.db.query(Incident).filter(Incident.status == "RESOLVED").count()
        closed = self.db.query(Incident).filter(Incident.status == "CLOSED").count()

        # Reopened count
        reopened_count = (
            self.db.query(IncidentEvent)
            .filter(IncidentEvent.event_type == "REOPENED")
            .count()
        )

        # Status & Severity & Category breakdowns
        status_rows = self.db.query(Incident.status, func.count(Incident.id)).group_by(Incident.status).all()
        status_breakdown = {s: cnt for s, cnt in status_rows}

        severity_rows = self.db.query(Incident.severity, func.count(Incident.id)).group_by(Incident.severity).all()
        severity_breakdown = {sev: cnt for sev, cnt in severity_rows}

        category_rows = self.db.query(Incident.category, func.count(Incident.id)).group_by(Incident.category).all()
        category_breakdown = {c: cnt for c, cnt in category_rows}

        # MTTT: Triaged incidents (time difference in hours between created_at and triaged_at)
        triaged_incidents = (
            self.db.query(Incident)
            .filter(Incident.triaged_at.isnot(None))
            .all()
        )
        mttt_hours = None
        if triaged_incidents:
            triage_durations = [
                max(0.0, (inc.triaged_at - inc.created_at).total_seconds() / 3600.0)
                for inc in triaged_incidents
                if inc.triaged_at and inc.created_at
            ]
            if triage_durations:
                mttt_hours = round(sum(triage_durations) / len(triage_durations), 2)

        # MTTR: Resolved incidents (time difference in hours between created_at and resolved_at)
        resolved_incidents = (
            self.db.query(Incident)
            .filter(Incident.resolved_at.isnot(None))
            .all()
        )
        mttr_hours = None
        if resolved_incidents:
            resolve_durations = [
                max(0.0, (inc.resolved_at - inc.created_at).total_seconds() / 3600.0)
                for inc in resolved_incidents
                if inc.resolved_at and inc.created_at
            ]
            if resolve_durations:
                mttr_hours = round(sum(resolve_durations) / len(resolve_durations), 2)

        # Daily trend (last 14 days)
        now = datetime.now(timezone.utc)
        fourteen_days_ago = now - timedelta(days=14)
        daily_trend = []
        for i in range(14):
            day_start = (fourteen_days_ago + timedelta(days=i)).replace(hour=0, minute=0, second=0, microsecond=0)
            day_end = day_start + timedelta(days=1)
            count = (
                self.db.query(Incident)
                .filter(Incident.created_at >= day_start, Incident.created_at < day_end)
                .count()
            )
            daily_trend.append({
                "date": day_start.strftime("%Y-%m-%d"),
                "count": count
            })

        return {
            "total_incidents": total,
            "open_incidents": open_count,
            "critical_incidents": critical_count,
            "high_incidents": high_count,
            "awaiting_triage_incidents": awaiting_triage,
            "investigating_incidents": investigating,
            "contained_incidents": contained,
            "resolved_incidents": resolved,
            "closed_incidents": closed,
            "reopened_incidents": reopened_count,
            "mean_time_to_triage_hours": mttt_hours,
            "mean_time_to_resolve_hours": mttr_hours,
            "severity_breakdown": severity_breakdown,
            "status_breakdown": status_breakdown,
            "category_breakdown": category_breakdown,
            "daily_trend": daily_trend
        }

    def find_correlation_candidates(self, incident_id: str) -> List[CorrelationSuggestionItem]:
        """
        Conservative Threat Correlation:
        Extract indicators (domains, URLs, campaign IDs) from currently linked threats,
        and find unlinked threat events sharing those specific indicators.
        """
        incident = self.get_by_id(incident_id)
        if not incident:
            return []

        # Gather indicators from already linked threats
        linked_threat_ids = {link.threat_event_id for link in incident.threat_links}
        known_urls = set()
        known_domains = set()
        known_campaigns = set()

        for link in incident.threat_links:
            t = link.threat_event
            if not t:
                continue
            if t.campaign_id:
                known_campaigns.add(t.campaign_id)
            if t.url_metadata:
                if t.url_metadata.get("url"):
                    known_urls.add(t.url_metadata.get("url").lower().strip())
                if t.url_metadata.get("domain"):
                    known_domains.add(t.url_metadata.get("domain").lower().strip())
            if t.qr_metadata and t.qr_metadata.get("raw_text"):
                raw = t.qr_metadata.get("raw_text").strip()
                if raw.startswith("http://") or raw.startswith("https://"):
                    known_urls.add(raw.lower())

        if not known_urls and not known_domains and not known_campaigns:
            return []

        # Query candidate threat events (not already linked, created within last 60 days)
        sixty_days_ago = datetime.now(timezone.utc) - timedelta(days=60)
        candidates = (
            self.db.query(ThreatEvent)
            .filter(
                ThreatEvent.id.notin_(linked_threat_ids),
                ThreatEvent.timestamp >= sixty_days_ago
            )
            .order_by(desc(ThreatEvent.timestamp))
            .limit(100)
            .all()
        )

        suggestions: List[CorrelationSuggestionItem] = []
        for cand in candidates:
            cand_url = None
            cand_domain = None
            if cand.url_metadata:
                cand_url = (cand.url_metadata.get("url") or "").lower().strip()
                cand_domain = (cand.url_metadata.get("domain") or "").lower().strip()

            # Signal 1: Exact matching URL
            if cand_url and cand_url in known_urls:
                suggestions.append(CorrelationSuggestionItem(
                    threat_event_id=cand.id,
                    event_type=cand.event_type,
                    risk_score=cand.risk_score,
                    threat_category=cand.threat_category,
                    timestamp=cand.timestamp,
                    matched_indicator=cand_url,
                    signal="MATCHING_URL",
                    confidence="CONFIRMED",
                    url=cand_url,
                    domain=cand_domain
                ))
                continue

            # Signal 2: Campaign ID match
            if cand.campaign_id and cand.campaign_id in known_campaigns:
                suggestions.append(CorrelationSuggestionItem(
                    threat_event_id=cand.id,
                    event_type=cand.event_type,
                    risk_score=cand.risk_score,
                    threat_category=cand.threat_category,
                    timestamp=cand.timestamp,
                    matched_indicator=cand.campaign_id,
                    signal="MATCHING_CAMPAIGN",
                    confidence="CONFIRMED",
                    url=cand_url,
                    domain=cand_domain
                ))
                continue

            # Signal 3: Matching malicious domain
            if cand_domain and cand_domain in known_domains:
                suggestions.append(CorrelationSuggestionItem(
                    threat_event_id=cand.id,
                    event_type=cand.event_type,
                    risk_score=cand.risk_score,
                    threat_category=cand.threat_category,
                    timestamp=cand.timestamp,
                    matched_indicator=cand_domain,
                    signal="MATCHING_DOMAIN",
                    confidence="LIKELY",
                    url=cand_url,
                    domain=cand_domain
                ))
                continue

        return suggestions
