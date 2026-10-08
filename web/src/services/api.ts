import {
  ThreatEvent,
  PaginatedResponse,
  ThreatFilterParams,
  AnalyticsSummary,
  ThreatTrendsResponse,
  User,
  UserStats,
  ModelInfo,
  FeedbackItem,
  AuditLogItem,
  ApiError,
} from '@/types';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8000/api/v1';

let authToken: string | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function getAuthToken(): string | null {
  return authToken;
}

export function setOnUnauthorizedCallback(cb: () => void) {
  onUnauthorizedCallback = cb;
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');

  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`);
  }

  try {
    const res = await fetch(url, {
      ...options,
      headers,
    });

    if (res.status === 401) {
      if (onUnauthorizedCallback) {
        onUnauthorizedCallback();
      }
      throw {
        status: 401,
        code: 'UNAUTHORIZED',
        message: 'Session expired or invalid credentials. Please sign in again.',
      } as ApiError;
    }

    if (res.status === 403) {
      let msg = 'Administrative privileges required to access this resource.';
      try {
        const body = await res.json();
        if (body.detail?.message) msg = body.detail.message;
      } catch {}
      throw {
        status: 403,
        code: 'FORBIDDEN',
        message: msg,
      } as ApiError;
    }

    if (res.status === 429) {
      throw {
        status: 429,
        code: 'RATE_LIMITED',
        message: 'Rate limit exceeded. Please wait a few seconds before retrying.',
      } as ApiError;
    }

    if (res.status === 503) {
      throw {
        status: 503,
        code: 'SERVICE_UNAVAILABLE',
        message: 'Backend database or service is unavailable. Please check backend server.',
      } as ApiError;
    }

    if (!res.ok) {
      let code = 'ERROR';
      let message = `Request failed with status ${res.status}`;
      let details: any = undefined;
      try {
        const errJson = await res.json();
        const payload = errJson.error || errJson.detail;
        if (payload) {
          if (typeof payload === 'string') {
            message = payload;
          } else if (typeof payload === 'object') {
            code = payload.code || code;
            message = payload.message || message;
            details = payload.details !== undefined ? payload.details : payload;
          }
        }
      } catch {}

      throw {
        status: res.status,
        code,
        message,
        details,
      } as ApiError;
    }

    return (await res.json()) as T;
  } catch (err: any) {
    if (err.status && err.code) {
      throw err;
    }
    // Network / offline error
    throw {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Unable to connect to the backend server. Please verify network or API status.',
      details: err.message,
    } as ApiError;
  }
}

export const api = {
  // Auth
  auth: {
    verify: () => request<{ valid: boolean; user: User }>('/auth/verify', { method: 'POST' }),
    getMe: () => request<User>('/auth/me'),
  },

  // User-Owned Threats & Telemetry (Self-Service)
  userThreats: {
    list: (params: ThreatFilterParams = {}) => {
      const q = new URLSearchParams();
      if (params.page) q.set('page', params.page.toString());
      if (params.page_size) q.set('page_size', params.page_size.toString());
      if (params.event_type && params.event_type !== 'ALL') q.set('event_type', params.event_type);
      if (params.threat_category && params.threat_category !== 'ALL') q.set('threat_category', params.threat_category);
      if (params.min_risk_score !== undefined) q.set('min_risk_score', params.min_risk_score.toString());
      if (params.max_risk_score !== undefined) q.set('max_risk_score', params.max_risk_score.toString());

      const qs = q.toString();
      return request<PaginatedResponse<ThreatEvent>>(`/detections${qs ? `?${qs}` : ''}`);
    },

    get: (id: string) => request<ThreatEvent>(`/detections/${id}`),

    takeAction: (id: string, action: string, notes?: string) =>
      request<ThreatEvent>(`/detections/${id}/action`, {
        method: 'POST',
        body: JSON.stringify({ action, notes }),
      }),

    delete: (id: string) =>
      request<{ success: boolean; message: string }>(`/detections/${id}`, {
        method: 'DELETE',
      }),

    getStats: () => request<UserStats>('/detections/stats'),

    create: (data: Partial<ThreatEvent>) =>
      request<ThreatEvent>('/detections', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
  },

  // Admin Threats / Telemetry
  threats: {
    list: (params: ThreatFilterParams = {}) => {
      const q = new URLSearchParams();
      if (params.page) q.set('page', params.page.toString());
      if (params.page_size) q.set('page_size', params.page_size.toString());
      if (params.event_type && params.event_type !== 'ALL') q.set('event_type', params.event_type);
      if (params.threat_category && params.threat_category !== 'ALL') q.set('threat_category', params.threat_category);
      if (params.min_risk_score !== undefined) q.set('min_risk_score', params.min_risk_score.toString());
      if (params.max_risk_score !== undefined) q.set('max_risk_score', params.max_risk_score.toString());
      if (params.status && params.status !== 'ALL') q.set('status', params.status);
      if (params.search) q.set('search', params.search);
      if (params.start_date) q.set('start_date', params.start_date);
      if (params.end_date) q.set('end_date', params.end_date);

      const qs = q.toString();
      return request<PaginatedResponse<ThreatEvent>>(`/admin/threat-events${qs ? `?${qs}` : ''}`);
    },

    get: (id: string) => request<ThreatEvent>(`/admin/threat-events/${id}`),

    updateStatus: (id: string, status: string) =>
      request<ThreatEvent>(`/admin/threat-events/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      }),
  },

  // Analytics
  analytics: {
    getSummary: () => request<AnalyticsSummary>('/admin/analytics/summary'),
    getTrends: (days: number = 7) =>
      request<ThreatTrendsResponse>(`/analytics/trends?days=${days}`),
  },

  // Users
  users: {
    list: (page: number = 1, pageSize: number = 20) =>
      request<PaginatedResponse<User>>(`/admin/users?page=${page}&page_size=${pageSize}`),

    updateStatus: (userId: string, isActive: boolean) =>
      request<User>(`/admin/users/${userId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ is_active: isActive }),
      }),

    updateRole: (userId: string, role: 'USER' | 'ADMIN') =>
      request<User>(`/admin/users/${userId}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role }),
      }),
  },

  // Models
  models: {
    list: () => request<ModelInfo[]>('/models'),
    get: (id: string) => request<ModelInfo>(`/models/${id}`),
  },

  // Feedback
  feedback: {
    list: (page: number = 1, pageSize: number = 20) =>
      request<PaginatedResponse<FeedbackItem>>(`/feedback?page=${page}&page_size=${pageSize}`),
  },

  // Audit Logs
  auditLogs: {
    list: (
      page: number = 1,
      pageSize: number = 20,
      action?: string,
      resourceType?: string
    ) => {
      const q = new URLSearchParams();
      q.set('page', page.toString());
      q.set('page_size', pageSize.toString());
      if (action) q.set('action', action);
      if (resourceType) q.set('resource_type', resourceType);
      return request<PaginatedResponse<AuditLogItem>>(`/admin/audit-logs?${q.toString()}`);
    },
  },
};
