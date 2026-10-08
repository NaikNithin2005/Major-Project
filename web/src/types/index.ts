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
  created_at: string;
  updated_at?: string | null;
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
  threat_event_id: string;
  user_id: string;
  classification: string;
  comment: string | null;
  created_at: string;
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
