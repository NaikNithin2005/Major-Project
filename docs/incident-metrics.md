# Security Operations Center (SOC) Incident Metrics & Measurement Guide

## 1. Overview

In Phase 15, the backend computes real-time, deterministic incident response performance metrics via `GET /api/v1/admin/incidents/metrics`. These metrics provide quantitative visibility into detection velocity, operational workload, and investigation timeliness without fabricating data or reporting biased averages.

---

## 2. Metric Definitions & Formulas

### 2.1 Mean Time to Triage (MTTT)
- **Definition:** The average elapsed time (in hours) from the moment a security incident record is created (`created_at`) until an operator formally conducts initial triage and transitions the incident to `TRIAGED` (`triaged_at`).
- **Mathematical Formula:**
  $$\text{MTTT} = \frac{1}{N_{\text{triaged}}} \sum_{i=1}^{N_{\text{triaged}}} \max\left(0, \frac{\text{triaged\_at}_i - \text{created\_at}_i}{3600}\right)$$
- **Handling Incomplete Data:** If an incident has not yet reached the `TRIAGED` state (`triaged_at IS NULL`), it is excluded from the MTTT calculation. If zero incidents have been triaged ($N_{\text{triaged}} = 0$), the API returns `null` (rendered as `N/A` in the dashboard) rather than a fabricated `0.0h`.

### 2.2 Mean Time to Resolution (MTTR)
- **Definition:** The average elapsed time (in hours) from incident creation (`created_at`) until formal resolution (`resolved_at`) with a documented containment and remediation summary.
- **Mathematical Formula:**
  $$\text{MTTR} = \frac{1}{N_{\text{resolved}}} \sum_{j=1}^{N_{\text{resolved}}} \max\left(0, \frac{\text{resolved\_at}_j - \text{created\_at}_j}{3600}\right)$$
- **Handling Incomplete Data:** If an incident has not reached `RESOLVED` status (`resolved_at IS NULL`), it is excluded from the calculation. If zero incidents are resolved, the API returns `null`.

### 2.3 Active Incident Workload Metrics
- **`total_incidents`:** Total count of all incidents ever recorded in `incidents` table.
- **`open_incidents`:** Total incidents whose status is NOT in `['RESOLVED', 'CLOSED']`.
- **`critical_incidents`:** Total open incidents with `severity == 'CRITICAL'`.
- **`high_incidents`:** Total open incidents with `severity == 'HIGH'`.
- **`awaiting_triage_incidents`:** Total incidents currently in `NEW` status.
- **`investigating_incidents`:** Total incidents currently in `INVESTIGATING` status.
- **`contained_incidents`:** Total incidents currently in `CONTAINED` status.
- **`resolved_incidents`:** Total incidents in `RESOLVED` status.
- **`closed_incidents`:** Total incidents in `CLOSED` status.
- **`reopened_incidents`:** Cumulative count of `REOPENED` audit events recorded in `incident_events`.

### 2.4 Distributions & Trends
- **`severity_breakdown`:** Grouped incident counts by severity level (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
- **`status_breakdown`:** Grouped incident counts across all 6 lifecycle statuses.
- **`category_breakdown`:** Grouped incident counts across threat categories (`QUISHING_CAMPAIGN`, `SMISHING_SURGE`, `BRAND_IMPERSONATION`, `MALICIOUS_INFRASTRUCTURE`, `ACCOUNT_TAKEOVER_ATTEMPT`, `UNKNOWN`).
- **`daily_trend`:** A 14-day rolling window array of daily incident creation counts:
  $$\text{daily\_trend} = [\{\text{"date"}: \text{"YYYY-MM-DD"}, \text{"count"}: N\}, \dots]$$

---

## 3. Data Integrity & Edge Cases

1. **Reopened Incidents:** When an incident is reopened from `RESOLVED` back to `INVESTIGATING`, its original `resolved_at` is preserved for historical auditing, while an explicit `REOPENED` event is appended to the audit ledger.
2. **Clock Skew Defense:** If system clock adjustments produce a negative delta ($\text{triaged\_at} < \text{created\_at}$), the calculation clamps the duration to `0.0` hours to prevent nonsensical negative values.
3. **Decoupled Detection Time:** Individual edge detection timestamps on Android devices (`threat_events.timestamp`) are distinguished from backend incident creation timestamps (`incidents.created_at`). An incident created today investigating a 3-day-old campaign accurately calculates operator response time based on administrative visibility.
