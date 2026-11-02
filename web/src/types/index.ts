export type ThreatCategory = 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH';

export type ThreatEventType = 'SMS' | 'QR';

export type ThreatStatus = 'NEW' | 'REPORTED' | 'REVIEWED' | 'RESOLVED' | 'FALSE_POSITIVE' | 'BLOCKED';

export type UserRole = 'USER' | 'ADMIN';



export interface User {

  id: string;

  firebase_uid: string;

  email: string;

  display_name: string | null;

  phone_number?: string | null;

  role: UserRole;

  is_active: boolean;

  created_at: string;

  updated_at: string;

  last_seen_at: string | null;

}



export interface ThreatEvent {

  id: string;

  client_event_id: string | null;

  user_id: string | null;

  device_id: string | null;

  event_type: ThreatEventType;

  risk_score: number;

  threat_category: ThreatCategory;

  recommended_action: string;

  timestamp: string;

  model_versions: Record<string, any> | null;

  model_results: Record<string, any> | null;

  rule_results: Array<Record<string, any>> | null;

  context_results: Record<string, any> | null;

  reasons: string[] | null;

  url_metadata: Record<string, any> | null;

  qr_metadata: Record<string, any> | null;

  related_threat_id: string | null;

  campaign_id: string | null;

  status: ThreatStatus | string;

  user_action?: string | null;

  sanitized_preview?: string | null;

  content_retention_mode?: string | null;

  decoded_url?: string | null;

  created_at: string;

  updated_at?: string | null;

  sender?: string | null;

  raw_content?: string | null;

  explanation?: string | null;

}



export interface UserStats {

  total_scans: number;

  threats_detected: number;

  threats_blocked: number;

  safe_detections: number;

  avg_risk_score: number;

  high_risk_count: number;

  medium_risk_count: number;

  low_risk_count: number;

  safe_count: number;

  recent_qr_count: number;

  recent_sms_count: number;

  false_positives_reported?: number;

  unresolved_high_risk?: number;

  sms_total?: number;

  sms_safe?: number;

  sms_attention?: number;

  sms_dangerous?: number;

  qr_total?: number;

  qr_safe?: number;

  qr_attention?: number;

  qr_dangerous?: number;

}



export interface UserActionRequest {

  action: 'BLOCK' | 'REPORT' | 'IGNORE' | 'MARK_SAFE' | 'RESOLVED' | 'FALSE_POSITIVE';

  notes?: string;

}



export interface AnalyticsSummary {

  total_detections: number;

  safe_count: number;

  low_count: number;

  medium_count: number;

  high_count: number;

  sms_count: number;

  qr_count: number;

  average_risk_score: number;

  total_feedback_count: number;

  false_positive_count: number;

  total_users: number | null;

  active_users: number | null;

}



export interface ThreatTrendPoint {

  date: string;

  count: number;

  avg_risk: number;

  high_risk_count: number;

}



export interface ThreatTrendsResponse {

  period: string;

  data_points: ThreatTrendPoint[];

  category_distribution: Record<string, number>;

  channel_distribution: Record<string, number>;

}



export interface ModelInfo {

  id: string;

  name: string;

  version: string;

  type: string;

  status: string;

  artifact_hash: string | null;

  metadata_json: Record<string, any> | null;

  created_at: string;

  updated_at: string;

}



export interface FeedbackItem {
  id: string;
  threat_event_id: string | null;
  user_id: string;
  classification: string;
  comment: string | null;
  created_at: string;
  message_hash?: string | null;
  sender_hash?: string | null;
  original_prediction?: string | null;
  corrected_label?: string | null;
  original_confidence?: number | null;
  feedback_source?: string;
  review_status?: string;
  training_status?: string;
  sender_reputation_score?: number | null;
}

export interface FeedbackStats {
  total_submissions: number;
  safe_corrections: number;
  smishing_corrections: number;
  suspicious_corrections: number;
  pending_reviews: number;
  approved_training_samples: number;
  rejected_samples: number;
  last_retraining_date: string | null;
}

export interface AuditLogItem {

  id: string;

  actor_user_id: string | null;

  action: string;

  resource_type: string;

  resource_id: string | null;

  timestamp: string;

  ip_address: string | null;

  metadata_json: Record<string, any> | null;

}



export interface PaginatedResponse<T> {

  items: T[];

  total: number;

  page: number;

  page_size: number;

  pages: number;

}



export interface ThreatFilterParams {

  page?: number;

  page_size?: number;

  event_type?: string;

  threat_category?: string;

  min_risk_score?: number;

  max_risk_score?: number;

  status?: string;

  search?: string;

  start_date?: string;

  end_date?: string;

}



export interface ApiError {

  status: number;

  code: string;

  message: string;

  details?: any;

}



// --- PHASE 15: SECURITY INCIDENT MANAGEMENT TYPES ---



export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type IncidentStatus = 'NEW' | 'TRIAGED' | 'INVESTIGATING' | 'CONTAINED' | 'RESOLVED' | 'CLOSED';

export type IncidentCategory =

  | 'QUISHING_CAMPAIGN'

  | 'SMISHING_SURGE'

  | 'BRAND_IMPERSONATION'

  | 'MALICIOUS_INFRASTRUCTURE'

  | 'ACCOUNT_TAKEOVER_ATTEMPT'

  | 'UNKNOWN';



export interface Incident {

  id: string;

  title: string;

  description: string | null;

  severity: IncidentSeverity;

  status: IncidentStatus;

  category: IncidentCategory;

  assigned_admin_id: string | null;

  assigned_admin_email: string | null;

  assigned_admin_name: string | null;

  created_by_admin_id: string | null;

  created_at: string;

  updated_at: string;

  triaged_at: string | null;

  contained_at: string | null;

  resolved_at: string | null;

  closed_at: string | null;

  resolution_summary: string | null;

  linked_threats_count: number;

  notes_count: number;

  metadata_json: Record<string, any> | null;

}



export interface LinkedThreatSummary {

  id: string;

  event_type: string;

  risk_score: number;

  threat_category: string;

  recommended_action: string;

  timestamp: string;

  url: string | null;

  domain: string | null;

  reasons: string[] | null;

  user_id: string | null;

  relationship_type: string;

  correlation_signal: string | null;

  linked_at: string;

}



export interface IncidentNote {

  id: string;

  incident_id: string;

  author_user_id: string;

  author_name: string | null;

  note_type: string;

  content: string;

  created_at: string;

  updated_at: string;

}



export interface IncidentEvent {

  id: string;

  incident_id: string;

  actor_user_id: string | null;

  actor_name: string | null;

  event_type: string;

  previous_state: string | null;

  new_state: string | null;

  summary: string;

  details_json: Record<string, any> | null;

  timestamp: string;

}



export interface IncidentDetail extends Incident {

  threat_links: LinkedThreatSummary[];

  notes: IncidentNote[];

  events: IncidentEvent[];

}



export interface CorrelationSuggestion {

  threat_event_id: string;

  event_type: string;

  risk_score: number;

  threat_category: string;

  timestamp: string;

  matched_indicator: string;

  signal: string;

  confidence: string;

  url: string | null;

  domain: string | null;

}



export interface IncidentMetrics {

  total_incidents: number;

  open_incidents: number;

  critical_incidents: number;

  high_incidents: number;

  awaiting_triage_incidents: number;

  investigating_incidents: number;

  contained_incidents: number;

  resolved_incidents: number;

  closed_incidents: number;

  reopened_incidents: number;

  mean_time_to_triage_hours: number | null;

  mean_time_to_resolve_hours: number | null;

  severity_breakdown: Record<string, number>;

  status_breakdown: Record<string, number>;

  category_breakdown: Record<string, number>;

  daily_trend: Array<{ date: string; count: number }>;

}



export interface IncidentFilterParams {

  page?: number;

  page_size?: number;

  status?: string;

  severity?: string;

  category?: string;

  assigned_admin_id?: string;

  search?: string;

  start_date?: string;

  end_date?: string;

}



// =========================================================================

// PHASE 18: THREAT INTELLIGENCE & COLLABORATIVE SHARING

// =========================================================================



export interface ThreatIndicator {

  id: string;

  indicator_type: string;

  indicator_value: string;

  threat_type: string;

  confidence: number;

  source: string;

  first_seen: string;

  last_seen: string;

  status: string;

  verification_status?: string;

  expires_at?: string | null;

  revocation_reason?: string | null;

  metadata_json?: Record<string, any> | null;

}



export interface CommunityThreatReport {

  id: string;

  anonymous_submission_id: string;

  indicator_type: string;

  indicator_value: string;

  threat_category: string;

  user_risk_score: number;

  model_verdicts?: Record<string, any> | null;

  context_metadata?: Record<string, any> | null;

  status: string;

  consensus_score: number;

  corroboration_count: number;

  admin_notes?: string | null;

  reviewed_at?: string | null;

  promoted_indicator_id?: string | null;

  submitted_at: string;

  updated_at: string;

}



export interface FeedStatusItem {

  feed_name: string;

  provider_type: string;

  is_active: boolean;

  is_configured: boolean;

  indicator_count: number;

  last_sync?: string | null;

  terms: string;

}



export interface ThreatIntelHealth {

  status: string;

  total_verified_indicators: number;

  active_indicators: number;

  revoked_indicators: number;

  expired_indicators: number;

  total_community_reports: number;

  pending_community_reports: number;

  approved_community_reports: number;

  rejected_community_reports: number;

  feeds: FeedStatusItem[];

}







export interface SenderSecurityItem {
  id: string;
  user_id: string;
  sender_id: string;
  display_name?: string | null;
  trust_status: 'TRUSTED' | 'UNTRUSTED' | 'BLOCKED' | 'UNVERIFIED';
  verification_status: 'VERIFIED' | 'UNVERIFIED' | 'SUSPICIOUS' | 'CONFIRMED_MALICIOUS';
  reason?: string | null;
  marked_by: string;
  risk_score: number;
  total_messages_count: number;
  legitimate_messages_count: number;
  suspicious_messages_count: number;
  dangerous_messages_count: number;
  last_message_preview?: string | null;
  last_message_date?: string | null;
  is_blocked: boolean;
  blocked_at?: string | null;
  blocked_by?: string | null;
  block_reason?: string | null;
  trusted_at?: string | null;
  created_at: string;
  updated_at: string;
}

export interface QuarantinedSmsItem {
  id: string;
  sender?: string | null;
  raw_content?: string | null;
  risk_score: number;
  threat_category: string;
  recommended_action: string;
  timestamp: string;
  quarantined_at?: string | null;
  quarantine_reason?: string | null;
  is_quarantined: boolean;
  extracted_url?: string | null;
  model_results?: Record<string, any> | null;
  reasons?: string[] | null;
  status: string;
}

export interface BlockedSmsItem {
  id: string;
  sender?: string | null;
  raw_content?: string | null;
  risk_score: number;
  threat_category: string;
  blocked_at?: string | null;
  block_reason?: string | null;
  is_blocked: boolean;
  model_results?: Record<string, any> | null;
  user_action?: string | null;
}

export interface SafetyAlertItem {
  id: string;
  user_id: string;
  threat_event_id?: string | null;
  title: string;
  sender?: string | null;
  message_preview?: string | null;
  threat_category: string;
  severity: string;
  risk_score: number;
  detection_reason?: string | null;
  status: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED';
  resolved_at?: string | null;
  resolved_by?: string | null;
  resolution_notes?: string | null;
  created_at: string;
  updated_at: string;
}

export interface SenderDetailsData {
  sender: SenderSecurityItem;
  message_history: any[];
  threat_history: any[];
  feedback_history: any[];
  audit_trail: any[];
}

export interface SmsDetailsData {
  threat_event: any;
  sender_info?: SenderSecurityItem | null;
  audit_trail: any[];
  feedback_history: any[];
}
