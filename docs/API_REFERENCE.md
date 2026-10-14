# REST API Reference & Specification

**Service Name:** Squish Shield Centralized Backend & Telemetry Service  
**Base URL:** `http://<host>:8000/api/v1`  
**OpenAPI Specification:** `http://<host>:8000/openapi.json`  
**Swagger UI:** `http://<host>:8000/docs`  
**ReDoc:** `http://<host>:8000/redoc`

---

## 1. Authentication & Security Headers

All endpoints under `/api/v1` (with the exception of public system health probes) require an authenticated Firebase Bearer ID Token in the `Authorization` header:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

### Role-Based Access Control (RBAC)
- **`USER` Role:** Default permission level. Granted access to register devices, submit threat telemetry, inspect personal detection history, submit false-positive feedback, and read model metadata.
- **`ADMIN` Role:** Security Operations Center (SOC) role. Granted access to view system-wide threat telemetry across all users, inspect user accounts, modify user roles/status, register model versions, and inspect audit logs.

### Security Response Headers
Every response includes OWASP-recommended defense-in-depth headers:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Content-Security-Policy: default-src 'self'`

---

## 2. System Health & Probes

### 2.1 Service Root
- **Method / Path:** `GET /`
- **Auth Required:** None
- **Response:**
  ```json
  {
    "status": "ok",
    "service": "squish-shield-backend",
    "version": "1.0.0",
    "phase": "Phase 12 - Release, Deployment & Documentation"
  }
  ```

### 2.2 Liveness Probe
- **Method / Path:** `GET /health/live`
- **Auth Required:** None
- **Purpose:** Used by container orchestrators (Kubernetes / Docker Swarm) to detect deadlocked processes.
- **Response (200 OK):**
  ```json
  {
    "status": "alive",
    "service": "squish-shield-backend",
    "version": "1.0.0"
  }
  ```

### 2.3 Readiness Probe
- **Method / Path:** `GET /health/ready`
- **Auth Required:** None
- **Purpose:** Verifies whether the service is ready to accept traffic by testing database connectivity and auth initialization without exposing internal credentials.
- **Response (200 OK):**
  ```json
  {
    "status": "ready",
    "service": "squish-shield-backend",
    "version": "1.0.0",
    "database": {
      "status": "connected",
      "dialect": "postgresql",
      "wal_enabled": true
    },
    "auth": {
      "firebase_initialized": true,
      "init_mode": "service_account",
      "mock_auth_active": false
    }
  }
  ```
- **Error Response (503 Service Unavailable):** Returned if the database connection fails or auth is unready.

---

## 3. Authentication Routes (`/api/v1/auth`)

### 3.1 Verify Token
- **Method / Path:** `POST /api/v1/auth/verify`
- **Auth Required:** Bearer Token
- **Role:** Any (`USER` or `ADMIN`)
- **Response (200 OK):**
  ```json
  {
    "valid": true,
    "user": {
      "id": "c4b92b67-d86b-4bb8-8884-601e0d37e283",
      "email": "user@example.com",
      "role": "USER",
      "display_name": "Mobile Tester",
      "is_active": true,
      "created_at": "2026-10-13T10:00:00Z"
    }
  }
  ```

### 3.2 Get Current User Profile
- **Method / Path:** `GET /api/v1/auth/me`
- **Auth Required:** Bearer Token
- **Response (200 OK):** UserProfile object.

---

## 4. User & Device Routes (`/api/v1/users`)

### 4.1 Update Profile
- **Method / Path:** `PATCH /api/v1/users/me`
- **Auth Required:** Bearer Token
- **Request Body:**
  ```json
  {
    "display_name": "Alice Smith"
  }
  ```
- **Response (200 OK):** Updated UserProfile object. Note: role, email, and UID cannot be modified via this endpoint.

### 4.2 Register or Update Device
- **Method / Path:** `POST /api/v1/users/devices`
- **Auth Required:** Bearer Token
- **Request Body:**
  ```json
  {
    "device_identifier": "c9e29a21-4f11-4475-b6d8-2fa5e01b4d08",
    "platform": "Android 15 (Pixel 8)",
    "app_version": "1.0.0",
    "model_version": "tinybert:v2,xgboost:v2"
  }
  ```
- **Response (200 OK / 201 Created):**
  ```json
  {
    "id": "e7b0c3d4-...",
    "user_id": "c4b92b67-...",
    "device_identifier": "c9e29a21-...",
    "platform": "Android 15 (Pixel 8)",
    "app_version": "1.0.0",
    "model_version": "tinybert:v2,xgboost:v2",
    "last_sync_at": "2026-10-13T12:00:00Z"
  }
  ```

### 4.3 List User Devices
- **Method / Path:** `GET /api/v1/users/devices`
- **Auth Required:** Bearer Token
- **Response (200 OK):** Array of DeviceResponse objects belonging exclusively to the caller.

---

## 5. Threat Detections & Telemetry (`/api/v1/detections`)

### 5.1 Submit Threat Detection Telemetry
- **Method / Path:** `POST /api/v1/detections`
- **Auth Required:** Bearer Token
- **Privacy Enforcement:** Raw SMS body text is strictly disallowed; stores only risk assessment outputs and hashes.
- **Request Body:**
  ```json
  {
    "event_type": "SMS",
    "threat_category": "HIGH",
    "risk_score": 88,
    "confidence_score": 0.94,
    "source_identifier": "+18005550199",
    "threat_indicators": ["URGENT_CALL_TO_ACTION", "FINANCIAL_IMPERSONATION"],
    "model_version": "tinybert:v2",
    "inference_latency_ms": 42.5,
    "user_action": "BLOCKED",
    "client_timestamp": "2026-10-13T11:45:00Z"
  }
  ```
- **Response (201 Created):** DetectionResponse object.

### 5.2 List User Detections (Paginated)
- **Method / Path:** `GET /api/v1/detections`
- **Auth Required:** Bearer Token
- **Query Parameters:**
  - `page`: int (default 1)
  - `page_size`: int (default 20, max 100)
  - `event_type`: string ("SMS" or "QR")
  - `threat_category`: string ("SAFE", "LOW", "MEDIUM", "HIGH")
  - `min_risk_score`: int (0-100)
  - `max_risk_score`: int (0-100)
- **Response (200 OK):**
  ```json
  {
    "items": [...],
    "total": 14,
    "page": 1,
    "page_size": 20,
    "pages": 1
  }
  ```

### 5.3 Record User Action on Threat
- **Method / Path:** `POST /api/v1/detections/{detection_id}/action`
- **Auth Required:** Bearer Token
- **Request Body:**
  ```json
  {
    "action": "BLOCKED",
    "notes": "User blocked sender via notification action"
  }
  ```
- **Response (200 OK):** Updated DetectionResponse object.

---

## 6. Feedback & Model Retraining Data (`/api/v1/feedback`)

### 6.1 Submit User Feedback
- **Method / Path:** `POST /api/v1/feedback`
- **Auth Required:** Bearer Token
- **Request Body:**
  ```json
  {
    "threat_event_id": "9a1f28b4-0e31-4a11-b062-817de253a8ef",
    "feedback_type": "FALSE_POSITIVE",
    "user_comments": "Legitimate banking verification code incorrectly classified as smishing"
  }
  ```
- **Response (201 Created):** FeedbackResponse object.

---

## 7. Model Metadata Routes (`/api/v1/models`)

### 7.1 List Active Models
- **Method / Path:** `GET /api/v1/models`
- **Auth Required:** Bearer Token
- **Response (200 OK):** Array of ModelInfoResponse objects describing model architectures, versions, and deployment statuses.

---

## 8. Administrative Routes (`/api/v1/admin`)

*All administrative routes require authentication with an account possessing the `ADMIN` role. Standard users receive `403 Forbidden`.*

### 8.1 List Registered Users
- **Method / Path:** `GET /api/v1/admin/users`
- **Role:** `ADMIN`
- **Query Parameters:** `page`, `page_size`
- **Response (200 OK):** PaginatedResponse of UserResponse objects.

### 8.2 Update User Role
- **Method / Path:** `PATCH /api/v1/admin/users/{user_id}/role`
- **Role:** `ADMIN`
- **Request Body:**
  ```json
  {
    "role": "ADMIN"
  }
  ```
- **Response (200 OK):** Updated UserResponse. An immutable audit log entry is generated.

### 8.3 Global Threat Telemetry Monitoring
- **Method / Path:** `GET /api/v1/admin/threat-events`
- **Role:** `ADMIN`
- **Query Parameters:** `page`, `page_size`, `event_type`, `threat_category`, `search`, `start_date`, `end_date`
- **Response (200 OK):** PaginatedResponse of all system threat telemetry across all registered devices.

### 8.4 Triage Threat Status
- **Method / Path:** `PATCH /api/v1/admin/threat-events/{event_id}/status`
- **Role:** `ADMIN`
- **Request Body:**
  ```json
  {
    "status": "RESOLVED"
  }
  ```
- **Response (200 OK):** Updated DetectionResponse.

### 8.5 Audit Logs Retrieval
- **Method / Path:** `GET /api/v1/admin/audit-logs`
- **Role:** `ADMIN`
- **Query Parameters:** `page`, `page_size`, `action`, `resource_type`
- **Response (200 OK):** Paginated list of AuditLogResponse objects detailing administrative actions.
