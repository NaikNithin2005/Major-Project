# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

# Architecture Document

**Version:** 1.0\
**Source:** Derived from `PRD.md`\
**Purpose:** Implementation architecture for an AI coding/development
agent

------------------------------------------------------------------------

## 1. Architecture Overview

The system is a privacy-first Android cybersecurity platform that
detects and prevents **Smishing** (SMS phishing) and **Quishing**
(malicious QR-code phishing).

The architecture is divided into four major applications/subsystems:

1.  **Android Mobile Application**
2.  **AI/ML Detection Engine**
3.  **Backend/API + Centralized Threat Intelligence Database**
4.  **Web Admin Security Dashboard**

The core detection path is **offline-first**. SMS and QR analysis should
continue to work when the backend or internet is unavailable.
Cloud/backend communication is used for authentication, synchronization,
feedback, analytics, threat intelligence, and model updates.

The PRD explicitly defines the Android application around Clean
Architecture + MVVM, local AI inference, a FastAPI backend, Firebase
Authentication, PostgreSQL, and a separate React/Next.js admin
dashboard.

------------------------------------------------------------------------

# 2. High-Level System Architecture

``` text
                         ┌──────────────────────────────┐
                         │          END USER            │
                         │        Android Device        │
                         └──────────────┬───────────────┘
                                        │
                     ┌──────────────────┴──────────────────┐
                     │                                     │
                 Incoming SMS                         QR Scanner
                     │                                     │
                     ▼                                     ▼
             ┌───────────────┐                    ┌───────────────┐
             │ SMS Receiver  │                    │ CameraX/ZXing │
             └───────┬───────┘                    └───────┬───────┘
                     │                                     │
                     ▼                                     ▼
             ┌───────────────┐                    ┌───────────────┐
             │ SMS Parser /  │                    │ QR Decoder /  │
             │ NLP Preprocess│                    │ URL Extractor │
             └───────┬───────┘                    └───────┬───────┘
                     │                                     │
                     ▼                                     ▼
             ┌───────────────┐                    ┌───────────────┐
             │   TinyBERT    │                    │    XGBoost    │
             │ Smishing ML   │                    │ URL/Quishing  │
             └───────┬───────┘                    └───────┬───────┘
                     │                                     │
                     └────────────────┬────────────────────┘
                                      ▼
                           ┌─────────────────────┐
                           │ Cross Verification  │
                           │ / Context Engine    │
                           └──────────┬──────────┘
                                      ▼
                           ┌─────────────────────┐
                           │ Risk Scoring Engine │
                           └──────────┬──────────┘
                                      ▼
                           ┌─────────────────────┐
                           │ Explainable AI      │
                           │ + Decision Engine   │
                           └──────────┬──────────┘
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
                  User Alert                 Local History
                         │                         │
                         └────────────┬────────────┘
                                      │
                           Async / Optional Sync
                                      │
                                      ▼
                              HTTPS REST API
                                      │
                                      ▼
                              ┌───────────────┐
                              │    FastAPI    │
                              │ Backend/API   │
                              └───────┬───────┘
                                      │
             ┌────────────────────────┼────────────────────────┐
             ▼                        ▼                        ▼
      Firebase Auth            Business Services         PostgreSQL
      Identity/Roles           Threat/Feedback/          Centralized
                               Analytics/Models          Threat DB
             │                        │                        │
             └────────────────────────┼────────────────────────┘
                                      ▼
                              Web Admin Dashboard
                              React / Next.js
```

The PRD specifies that client applications must never directly
manipulate the entire database; Android and the web dashboard
communicate through the authenticated backend API.
fileciteturn2file2L250-L279

------------------------------------------------------------------------

# 3. System Components

## 3.1 Android Application

The Android application is the primary user-facing component.

Responsibilities:

-   User registration/login/logout
-   Onboarding
-   Permission management
-   Background SMS monitoring
-   QR scanning
-   SMS preprocessing
-   QR decoding
-   Local AI inference
-   URL feature extraction
-   Cross-verification
-   Risk scoring
-   Explainable threat reports
-   Alerts/notifications
-   Threat history
-   User feedback
-   Settings
-   Offline operation
-   Background synchronization

The PRD defines the Android architecture as **Clean Architecture +
MVVM** and includes Presentation, ViewModel, Domain, Repository, Data,
AI, and Storage responsibilities. fileciteturn1file1L240-L300

------------------------------------------------------------------------

## 3.2 AI/ML Engine

The AI layer contains specialized detection engines:

### Smishing

``` text
SMS
 ↓
Cleaning
 ↓
Tokenization / Normalization
 ↓
TinyBERT
 ↓
Probability
 ↓
Risk
```

### Quishing

``` text
QR Image
 ↓
ZXing Decode
 ↓
Extract URL
 ↓
URL Feature Engineering
 ↓
XGBoost
 ↓
Probability
 ↓
Risk
```

### Zero-Day Detection

``` text
Features
 ↓
Isolation Forest
 ↓
Anomaly Score
 ↓
Risk Engine
```

### Cross Verification

``` text
SMS Context
     +
QR / URL
     +
Sender
     +
Brand
     ↓
Context Engine
     ↓
Combined Risk
```

The PRD specifies TinyBERT for SMS classification, XGBoost for URL
classification, Isolation Forest for anomaly detection, and an
explainable risk/decision pipeline.

------------------------------------------------------------------------

# 4. Application Flow

## 4.1 First Launch Flow

``` text
App Launch
   ↓
Splash Screen
   ↓
Initialize Services
   ↓
Load / Verify AI Models
   ↓
Check Authentication
   │
   ├── Not Authenticated
   │       ↓
   │    Onboarding
   │       ↓
   │    Login/Register
   │
   └── Authenticated
           ↓
       Check Permissions
           ↓
       Dashboard
```

The PRD defines Splash → Login → Permissions → Dashboard and includes
onboarding for first-time users. fileciteturn1file4L761-L834

------------------------------------------------------------------------

# 5. Authentication Flow

## 5.1 User Registration

``` text
Register
   ↓
Firebase Authentication
   ↓
Create UID
   ↓
Create User Profile Metadata
   ↓
Assign USER Role
   ↓
Persistent Session
   ↓
Dashboard
```

Supported authentication methods from the PRD:

-   Google Sign-In
-   Email + Password
-   Optional Guest Mode
-   Future: Phone OTP / Passkeys

Authentication credentials must be handled by the identity provider.
Passwords must never be stored in plaintext in the application database.

------------------------------------------------------------------------

## 5.2 Login

``` text
Login
  ↓
Firebase Authentication
  ↓
Authenticated Identity
  ↓
Signed Token
  ↓
Android App
  ↓
Backend Token Verification
  ↓
Authorized API Access
```

The PRD specifies Firebase Authentication with JWT/token verification
between Android and the backend. fileciteturn2file4L560-L601

------------------------------------------------------------------------

# 6. Role-Based Access Control

Two primary roles are supported:

``` text
USER
ADMIN
```

### USER

Can:

-   Access own profile
-   Scan QR
-   Analyze SMS
-   View own threat history
-   Submit feedback
-   View own threat reports
-   Configure settings

### ADMIN

Can:

-   Access admin dashboard
-   View authorized system-wide threat data
-   Search/filter threats
-   View threat details
-   View aggregate statistics
-   Review security events
-   Manage authorized threat intelligence operations

``` text
                    Authenticated User
                           │
                           ▼
                     Verify Role
                       /       \
                      /         \
                   USER         ADMIN
                    │             │
                    ▼             ▼
              Mobile App     Admin Dashboard
```

Every `/api/admin/*` endpoint must verify the administrator role.
Unauthorized requests must return HTTP 403.
fileciteturn2file2L300-L306

------------------------------------------------------------------------

# 7. Android User Navigation

``` text
Splash
  ↓
Onboarding (first launch only)
  ↓
Login / Register
  ↓
Permissions
  ↓
Home Dashboard
```

Bottom navigation:

``` text
┌─────────┬─────────┬─────────┬─────────┬──────────┐
│  Home   │ History │ Scanner │ Alerts  │ Settings │
└─────────┴─────────┴─────────┴─────────┴──────────┘
```

Main screens:

-   Splash
-   Onboarding
-   Login
-   Register
-   Dashboard
-   QR Scanner
-   SMS Analysis
-   Threat Report
-   Threat History
-   Notification Center
-   Feedback
-   Settings
-   About

The screen inventory and bottom-navigation structure are defined in the
PRD. fileciteturn2file0L61-L74 fileciteturn1file4L761-L806

------------------------------------------------------------------------

# 8. SMS Detection Flow

``` text
Incoming SMS
    ↓
Broadcast Receiver
    ↓
Permission Check
    ↓
SMS Parser
    ↓
Extract:
    - Sender
    - Timestamp
    - Body
    - URLs
    - Phone numbers
    - OTP indicators
    - Bank/brand names
    - Language
    ↓
NLP Preprocessing
    ↓
TinyBERT
    ↓
Smishing Probability
    ↓
Feature / Context Analysis
    ↓
Risk Scoring
    ↓
Explainable AI
    ↓
Decision Engine
    ↓
┌───────────┬────────────┬─────────────┐
│   SAFE    │ SUSPICIOUS │  MALICIOUS  │
└─────┬─────┴──────┬─────┴──────┬──────┘
      │            │             │
      ▼            ▼             ▼
   Allow        Warn          Block/Alert
      │            │             │
      └────────────┴─────────────┘
                   ↓
             Local History
                   ↓
          Optional Backend Sync
```

Core SMS detection is local/offline-first. Backend synchronization must
not block detection.

------------------------------------------------------------------------

# 9. QR / Quishing Detection Flow

``` text
Open QR Scanner
      ↓
CameraX
      ↓
ZXing
      ↓
Decode QR
      ↓
Extract Payload
      ↓
Is URL?
   /       \
 No         Yes
 |           |
Analyze      URL Feature Extraction
payload             ↓
                    ├── URL Length
                    ├── Domain Length
                    ├── HTTPS
                    ├── SSL
                    ├── Port
                    ├── Entropy
                    ├── Subdomains
                    ├── IP Usage
                    ├── Unicode/Homograph
                    ├── Shortener
                    └── Redirects
                           ↓
                        XGBoost
                           ↓
                    Risk Score Engine
                           ↓
                    Explainable AI
                           ↓
                    Decision Engine
                           ↓
                    Threat Report
```

------------------------------------------------------------------------

# 10. Cross-Verification Flow

The system must correlate related SMS and QR/URL information.

Example:

``` text
SMS:
"Your SBI KYC has expired.
Scan this QR immediately."

        +

QR:
paypal-secure.xyz

        ↓

Extract:
- Bank/brand from SMS = SBI
- Domain from QR = paypal-secure.xyz
- Context mismatch
- Urgency
- Suspicious domain

        ↓

Cross Verification Engine
        ↓
High Risk
        ↓
Explainable Warning
```

Related threats can be linked using:

``` text
relatedThreatId
campaignId
```

This allows analysts to identify coordinated phishing campaigns.
fileciteturn2file5L643-L663

------------------------------------------------------------------------

# 11. Risk Scoring

Every detection receives a score from 0--100.

``` text
0–25     SAFE
26–50    LOW RISK
51–75    MEDIUM RISK
76–100   HIGH RISK
```

The final risk score may combine:

``` text
SMS Model Score
        +
URL Model Score
        +
Anomaly Score
        +
Context Correlation
        +
Rule-Based Indicators
        ↓
Final Risk Score
```

The application must also produce human-readable reasons, for example:

``` text
Risk: 87/100

Reasons:
✓ Suspicious domain
✓ Banking keyword detected
✓ Unknown sender
✓ High urgency
✓ HTTPS missing
```

------------------------------------------------------------------------

# 12. Local Storage Architecture

Use Room for local structured data and secure Android storage for
sensitive preferences/tokens.

``` text
Room Database
├── User
├── ThreatHistory
├── SMSAnalysis
├── QRAnalysis
├── Feedback
├── Settings
└── ModelVersion
```

Other local storage:

-   Jetpack DataStore
-   EncryptedSharedPreferences where required
-   Android Keystore for cryptographic secrets

The PRD explicitly defines Room, encrypted storage, DataStore, and local
history as part of the Android architecture.
fileciteturn1file1L285-L300 fileciteturn1file9L1576-L1603

------------------------------------------------------------------------

# 13. Backend Architecture

The backend is an asynchronous FastAPI REST service.

``` text
                    HTTPS Request
                          ↓
                    Nginx / Proxy
                          ↓
                       FastAPI
                          ↓
                    API Middleware
                          ↓
              ┌───────────┴───────────┐
              │                       │
       Authentication           Validation
              │                       │
              └───────────┬───────────┘
                          ↓
                  Business Services
                          │
       ┌──────────────────┼──────────────────┐
       ↓                  ↓                  ↓
 Authentication      Threat Service     Feedback Service
       │                  │                  │
       └──────────────────┼──────────────────┘
                          ↓
                     Repository Layer
                          ↓
                      PostgreSQL
```

The backend must remain optional for core detection. If unavailable, the
Android app continues local analysis and queues synchronization tasks.
fileciteturn2file0L137-L151

------------------------------------------------------------------------

# 14. Backend Services

``` text
backend/
├── Authentication Service
├── Threat Intelligence Service
├── AI Model Service
├── Feedback Service
├── Notification Service
├── Synchronization Service
├── Analytics Service
└── API Gateway
```

### API Gateway

Responsibilities:

-   Request routing
-   Authentication
-   Authorization
-   Validation
-   Rate limiting
-   Logging
-   API versioning

### Authentication Service

Responsibilities:

-   Firebase token verification
-   Session validation
-   Role verification
-   User profile integration

### Threat Intelligence Service

Responsibilities:

-   Threat event storage
-   Threat statistics
-   Domain/URL intelligence
-   Threat correlation
-   Future threat feeds

### Feedback Service

Responsibilities:

-   False positives
-   False negatives
-   Correct detections
-   User reports
-   Dataset/retraining pipeline

### Model Service

Responsibilities:

-   Model metadata
-   Versioning
-   Model distribution
-   Integrity verification
-   Rollback

The PRD defines these backend services and their responsibilities.
fileciteturn2file4L456-L501

------------------------------------------------------------------------

# 15. REST API Architecture

Base path:

``` text
/api/v1
```

## Authentication

``` text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
```

## User

``` text
GET  /api/v1/users/profile
PUT  /api/v1/users/profile
```

## Detection

``` text
POST /api/v1/detections/sms
POST /api/v1/detections/qr
GET  /api/v1/detections/history
```

## Feedback

``` text
POST /api/v1/feedback
```

## Admin

``` text
GET /api/v1/admin/dashboard
GET /api/v1/admin/threats
GET /api/v1/admin/threats/{id}
GET /api/v1/admin/statistics
```

The PRD's defined API responsibilities include authenticated detection
telemetry, personal history, and protected admin endpoints.
fileciteturn2file2L282-L306

------------------------------------------------------------------------

# 16. Data Flow to Central Threat Database

``` text
Local Detection
      ↓
Create Security Event
      ↓
Check Network
      │
      ├── Offline
      │     ↓
      │   Queue Event
      │     ↓
      │   Local Storage
      │
      └── Online
            ↓
       HTTPS REST API
            ↓
       Firebase/JWT Verify
            ↓
       API Validation
            ↓
       Authorization
            ↓
       Threat Service
            ↓
       PostgreSQL
```

The central database should be named:

**Centralized Threat Intelligence and Security Event Database**

It stores security telemetry, detection logs, threat intelligence
metrics, feedback, and user profile metadata---not plaintext
credentials. Authentication credentials/tokens remain under the
dedicated identity/authentication system.
fileciteturn1file3L719-L724

------------------------------------------------------------------------

# 17. Threat Database Concept

Main entities:

``` text
User
  │
  └────< ThreatEvent
             │
             ├── SMSAnalysis
             ├── QRAnalysis
             ├── URLAnalysis
             ├── RiskAssessment
             ├── Explanation
             ├── Feedback
             └── RelatedThreat/Campaign
```

Recommended logical fields for `ThreatEvent`:

``` text
threatId
userId
threatType
inputType
riskScore
classification
detectedUrl
domain
modelUsed
modelVersion
detectionReasons
actionTaken
status
relatedThreatId
campaignId
createdAt
```

Do not store unnecessary private SMS content. Store the minimum data
required for detection history, security telemetry, analytics, and
research.

------------------------------------------------------------------------

# 18. Background Synchronization

``` text
WorkManager
    ↓
Check Network
    ↓
Internet Available?
   /          \
 No            Yes
 |              |
Retry         Upload Queue
Later            ↓
             Feedback
             Threat Events
             History Sync
             Model Check
                  ↓
               Complete
```

Use exponential backoff for retries.

Synchronization must never block local threat detection.

------------------------------------------------------------------------

# 19. Admin Web Dashboard Flow

``` text
Admin Opens Web Dashboard
          ↓
Admin Login
          ↓
Firebase Authentication
          ↓
Verify ADMIN Role
          ↓
Dashboard
          │
   ┌──────┼────────┬────────────┐
   ↓      ↓        ↓            ↓
Overview Threats Analytics    Users
          │
          ↓
   Threat Details
```

## Dashboard pages

``` text
/admin/login
/admin/dashboard
/admin/threats
/admin/threats/:id
/admin/analytics
/admin/users
/admin/settings
```

The PRD defines a separate administrator dashboard for system-wide
threat monitoring and analytics. fileciteturn2file5L666-L712

------------------------------------------------------------------------

# 20. Admin Dashboard Features

## Overview

Display:

-   Total users
-   Total scans
-   Total threats
-   Smishing threats
-   Quishing threats
-   Malicious threats
-   Suspicious threats
-   Safe detections

## Threat Monitoring

Table:

``` text
Time | Type | Input | Risk | Classification | Status
```

Features:

-   Search
-   Threat type filter
-   Risk filter
-   Date filter
-   Classification filter
-   Sort by risk
-   Sort by timestamp
-   Pagination
-   Threat detail inspector

## Threat Details

Display:

-   Threat ID
-   Threat type
-   Risk score
-   Classification
-   URL/domain
-   Detection model
-   Model version
-   Detection time
-   Detection reasons
-   Action/status
-   Related threat/campaign

Sensitive user information must be masked or restricted according to
role permissions. fileciteturn2file5L715-L753

------------------------------------------------------------------------

# 21. Complete Repository / Folder Structure

The repository should be organized as a monorepo so the Android,
backend, AI, and web components remain independent but share one project
root.

``` text
RealTime-Phishing-Detection/
│
├── android/
│   ├── app/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/example/phishingdetection/
│   │       │   │
│   │       │   ├── ui/
│   │       │   │   ├── auth/
│   │       │   │   ├── dashboard/
│   │       │   │   ├── scanner/
│   │       │   │   ├── sms/
│   │       │   │   ├── threat/
│   │       │   │   ├── history/
│   │       │   │   ├── notifications/
│   │       │   │   ├── feedback/
│   │       │   │   ├── settings/
│   │       │   │   └── components/
│   │       │   │
│   │       │   ├── navigation/
│   │       │   │
│   │       │   ├── domain/
│   │       │   │   ├── model/
│   │       │   │   └── usecase/
│   │       │   │
│   │       │   ├── data/
│   │       │   │   ├── local/
│   │       │   │   │   ├── dao/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── database/
│   │       │   │   ├── remote/
│   │       │   │   │   ├── api/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── auth/
│   │       │   │   └── repository/
│   │       │   │
│   │       │   ├── sms/
│   │       │   │   ├── receiver/
│   │       │   │   ├── parser/
│   │       │   │   └── preprocessing/
│   │       │   │
│   │       │   ├── qr/
│   │       │   │   ├── scanner/
│   │       │   │   ├── decoder/
│   │       │   │   └── url/
│   │       │   │
│   │       │   ├── ai/
│   │       │   │   ├── tinybert/
│   │       │   │   ├── xgboost/
│   │       │   │   ├── anomaly/
│   │       │   │   ├── risk/
│   │       │   │   └── explainability/
│   │       │   │
│   │       │   ├── services/
│   │       │   │   ├── notification/
│   │       │   │   ├── synchronization/
│   │       │   │   └── model/
│   │       │   │
│   │       │   ├── workers/
│   │       │   ├── security/
│   │       │   ├── di/
│   │       │   └── utils/
│   │       │
│   │       └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── backend/
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   │   ├── auth.py
│   │   │   ├── detections.py
│   │   │   ├── feedback.py
│   │   │   ├── users.py
│   │   │   └── admin.py
│   │   │
│   │   ├── auth/
│   │   │   ├── firebase.py
│   │   │   ├── dependencies.py
│   │   │   └── rbac.py
│   │   │
│   │   ├── models/
│   │   │   ├── user.py
│   │   │   ├── threat.py
│   │   │   ├── feedback.py
│   │   │   └── model_version.py
│   │   │
│   │   ├── schemas/
│   │   │   ├── auth.py
│   │   │   ├── detection.py
│   │   │   ├── threat.py
│   │   │   ├── feedback.py
│   │   │   └── dashboard.py
│   │   │
│   │   ├── repositories/
│   │   │   ├── user_repository.py
│   │   │   ├── threat_repository.py
│   │   │   └── feedback_repository.py
│   │   │
│   │   ├── services/
│   │   │   ├── threat_service.py
│   │   │   ├── feedback_service.py
│   │   │   ├── analytics_service.py
│   │   │   ├── model_service.py
│   │   │   └── sync_service.py
│   │   │
│   │   ├── middleware/
│   │   │   ├── auth.py
│   │   │   ├── rate_limit.py
│   │   │   └── logging.py
│   │   │
│   │   ├── database/
│   │   │   ├── session.py
│   │   │   └── base.py
│   │   │
│   │   ├── ai/
│   │   │   ├── inference.py
│   │   │   └── model_registry.py
│   │   │
│   │   ├── config/
│   │   │   └── settings.py
│   │   └── utils/
│   │
│   ├── migrations/
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
│
├── web/
│   ├── src/
│   │   ├── app/
│   │   │   ├── login/
│   │   │   ├── dashboard/
│   │   │   ├── threats/
│   │   │   ├── analytics/
│   │   │   ├── users/
│   │   │   └── settings/
│   │   │
│   │   ├── components/
│   │   │   ├── dashboard/
│   │   │   ├── threats/
│   │   │   ├── charts/
│   │   │   ├── tables/
│   │   │   └── common/
│   │   │
│   │   ├── services/
│   │   │   ├── api.ts
│   │   │   ├── auth.ts
│   │   │   └── threats.ts
│   │   │
│   │   ├── hooks/
│   │   ├── types/
│   │   ├── utils/
│   │   └── middleware.ts
│   │
│   ├── package.json
│   └── Dockerfile
│
├── ai/
│   ├── preprocessing/
│   ├── feature_engineering/
│   ├── tinybert/
│   │   ├── training/
│   │   ├── evaluation/
│   │   └── export/
│   ├── xgboost/
│   │   ├── training/
│   │   ├── evaluation/
│   │   └── export/
│   ├── anomaly/
│   ├── explainable_ai/
│   ├── training/
│   ├── evaluation/
│   └── deployment/
│
├── datasets/
│   ├── sms/
│   ├── urls/
│   └── qr/
│
├── models/
│   ├── tinybert/
│   ├── xgboost/
│   └── isolation_forest/
│
├── docker/
│   ├── docker-compose.yml
│   └── nginx/
│       └── nginx.conf
│
├── deployment/
│   ├── development/
│   ├── staging/
│   └── production/
│
├── scripts/
│
├── tests/
│   ├── android/
│   ├── backend/
│   ├── ai/
│   └── integration/
│
├── diagrams/
│   ├── system-architecture.md
│   ├── data-flow.md
│   └── sequence-diagrams.md
│
├── docs/
│   ├── PRD.md
│   ├── Architecture.md
│   ├── API.md
│   ├── Database.md
│   └── Deployment.md
│
└── README.md
```

The PRD already establishes the top-level repository as Android,
backend, AI, datasets, models, docs, scripts, Docker, tests, deployment,
and diagrams, with separate Android/backend/AI module structures.
fileciteturn1file7L1329-L1468

------------------------------------------------------------------------

# 22. Android Folder Responsibilities

  Directory       Responsibility
  --------------- ------------------------------------------------
  `ui/`           Compose screens and UI components
  `navigation/`   Navigation graph and routes
  `domain/`       Business rules and use cases
  `data/`         Local/remote data access
  `sms/`          SMS acquisition and parsing
  `qr/`           QR scanning and decoding
  `ai/`           On-device inference
  `services/`     Notifications, synchronization, model services
  `workers/`      WorkManager background jobs
  `security/`     Secure storage and integrity checks
  `di/`           Hilt dependency injection
  `utils/`        Shared utilities

Each Android module should be independently testable.

------------------------------------------------------------------------

# 23. Backend Folder Responsibilities

  Directory         Responsibility
  ----------------- ----------------------------------------
  `api/`            REST endpoints
  `auth/`           Firebase/JWT authentication and RBAC
  `models/`         SQLAlchemy database models
  `schemas/`        Pydantic request/response models
  `repositories/`   Database access
  `services/`       Business logic
  `middleware/`     Authentication, rate limiting, logging
  `database/`       PostgreSQL session/configuration
  `ai/`             Backend AI/model integration
  `config/`         Environment/configuration
  `tests/`          Backend unit/integration tests

------------------------------------------------------------------------

# 24. AI Folder Responsibilities

``` text
ai/
├── preprocessing/
│   ├── sms_cleaner.py
│   └── url_cleaner.py
│
├── feature_engineering/
│   ├── sms_features.py
│   ├── url_features.py
│   └── domain_features.py
│
├── tinybert/
│   ├── train.py
│   ├── evaluate.py
│   └── export.py
│
├── xgboost/
│   ├── train.py
│   ├── evaluate.py
│   └── export.py
│
├── anomaly/
│   └── isolation_forest.py
│
├── explainable_ai/
│   └── explanation_engine.py
│
├── training/
├── evaluation/
└── deployment/
```

------------------------------------------------------------------------

# 25. Technology Stack

## Android

  Layer                   Technology
  ----------------------- -----------------------------------------------
  Language                Kotlin
  UI                      Jetpack Compose
  Design                  Material Design 3
  Architecture            Clean Architecture + MVVM
  DI                      Hilt
  Local DB                Room
  Preferences             Jetpack DataStore
  Secure Storage          EncryptedSharedPreferences / Android Keystore
  Networking              Retrofit + OkHttp
  Camera                  CameraX
  QR Decoder              ZXing
  Background Work         WorkManager
  Background Monitoring   Foreground Service
  Async                   Kotlin Coroutines + StateFlow
  Authentication          Firebase Authentication
  AI Runtime              ONNX Runtime
  Optional AI Runtime     TensorFlow Lite

The PRD lists these technologies for the mobile stack.
fileciteturn1file1L301-L354

------------------------------------------------------------------------

## AI/ML

  Component                    Technology
  ---------------------------- ---------------------------------
  SMS Model                    TinyBERT
  URL/Quishing Model           XGBoost
  Zero-Day Anomaly Detection   Isolation Forest
  Inference                    ONNX Runtime
  Optional Mobile Runtime      TensorFlow Lite
  NLP                          Transformer-based preprocessing
  Explainability               Custom XAI explanation engine
  Training                     Python
  Experimentation              Jupyter Notebook

------------------------------------------------------------------------

## Backend

  Layer                Technology
  -------------------- -------------------------
  Framework            FastAPI
  Language             Python
  Authentication       Firebase Authentication
  Token Verification   Firebase/JWT
  Database             PostgreSQL
  ORM                  SQLAlchemy
  Migrations           Alembic
  Validation           Pydantic
  API                  REST
  Logging              Python Logging / Loguru
  Containerization     Docker
  Reverse Proxy        Nginx
  Monitoring           Prometheus + Grafana
  CI/CD                GitHub Actions
  Object Storage       S3-compatible storage

The PRD's backend stack specifies FastAPI, Firebase Auth, PostgreSQL,
SQLAlchemy, Alembic, Pydantic, Docker, Nginx, Prometheus/Grafana, GitHub
Actions, and S3-compatible storage. fileciteturn1file0L63-L77

------------------------------------------------------------------------

## Web Admin Dashboard

  Layer               Technology
  ------------------- ---------------------------
  Framework           Next.js / React
  Language            TypeScript
  Styling             Tailwind CSS
  Charts              Chart.js
  Authentication      Firebase Authentication
  API Communication   REST
  State/Data          React Query or equivalent
  Deployment          Docker / Cloud

The PRD recommends React.js/Next.js, Tailwind CSS, and Chart.js for the
web administrator dashboard. fileciteturn1file3L685-L694

------------------------------------------------------------------------

# 26. Security Architecture

The system follows defense-in-depth.

``` text
                 Security
                    │
       ┌────────────┼────────────┐
       ↓            ↓            ↓
 Device Security  Network     Backend/API
       │          Security      Security
       ↓            ↓            ↓
 Keystore       TLS 1.3       RBAC
 Encrypted DB   HTTPS         Validation
 Integrity      Rate Limit    Audit Logs
       │            │            │
       └────────────┼────────────┘
                    ↓
              Data Security
                    │
             Minimal Collection
             Encryption
             Access Control
```

Mandatory controls:

-   HTTPS/TLS
-   Firebase authentication
-   RBAC
-   API token validation
-   Pydantic validation
-   Rate limiting
-   Secure token handling
-   No plaintext passwords
-   No unnecessary SMS storage
-   Audit logging
-   Encrypted local storage
-   AI model integrity checks

The PRD explicitly requires RBAC, strict API validation, data
minimization, TLS 1.3, rate limiting, audit logging, and protection
against unauthorized database access. fileciteturn1file3L677-L682

------------------------------------------------------------------------

# 27. Offline-First Architecture

The application must not depend on the backend for core detection.

## Available Offline

-   SMS detection
-   QR detection
-   URL analysis
-   Risk scoring
-   Threat explanations
-   Threat history
-   Alerts

## Requires Connectivity

-   Model downloads
-   Analytics upload
-   Feedback synchronization
-   Cloud synchronization
-   Backend dashboard data
-   Model update checks

``` text
             Internet Available?
                    │
             ┌──────┴──────┐
             │             │
            YES            NO
             │             │
             ▼             ▼
       Backend Sync     Local Queue
             │             │
             ▼             ▼
          PostgreSQL    Retry Later
```

The PRD explicitly requires offline SMS/QR detection, risk scoring,
history, and alerts, while cloud synchronization and analytics are
unavailable offline. fileciteturn2file7L880-L902

------------------------------------------------------------------------

# 28. Deployment Architecture

## Development

``` text
Android Emulator/Device
        │
        ├── Local AI Models
        │
        └── Local FastAPI
                 │
             PostgreSQL
                 │
          Firebase Emulator
```

## Production

``` text
Android Application
        │
       HTTPS
        ↓
Load Balancer / Nginx
        ↓
FastAPI Containers
        ↓
PostgreSQL
        │
        ├── Cloud/Object Storage
        └── Monitoring
                │
                ↓
        Admin Web Dashboard
```

The PRD specifies Docker/Nginx/FastAPI/PostgreSQL deployment and
requires the Android app to remain functional if the backend is
unavailable. fileciteturn1file2L463-L495

------------------------------------------------------------------------

# 29. CI/CD Flow

``` text
Developer
   ↓
Git Push / Pull Request
   ↓
GitHub Actions
   ↓
Unit Tests
   ↓
Integration Tests
   ↓
Security Scan
   ↓
Docker Build
   ↓
Deploy
```

CI/CD should cover:

-   Android tests
-   Backend tests
-   AI tests
-   Integration tests
-   Security checks
-   Docker build validation
-   Deployment

------------------------------------------------------------------------

# 30. Error and Failure Handling

## No Internet

``` text
Continue Local Detection
        ↓
Store/Queue Synchronization
        ↓
Retry When Network Returns
```

## Backend Unavailable

``` text
Local Detection Continues
        ↓
Queue Events
        ↓
Retry With Exponential Backoff
```

## AI Model Missing

``` text
Try Fallback Model
        ↓
If Online → Download/Update
        ↓
If Offline → Continue With Available Model
```

## Camera Failure

``` text
Camera Failure
      ↓
Offer Gallery Import
```

## SMS Permission Denied

``` text
Permission Denied
      ↓
Explain Permission
      ↓
Retry
      ↓
If Still Denied → Disable SMS Module
```

The PRD defines these fault-tolerance behaviors explicitly.
fileciteturn1file2L574-L590

------------------------------------------------------------------------

# 31. Performance Targets

The architecture must preserve the PRD targets:

  Metric                                                  Target
  --------------------- ----------------------------------------
  App cold start                                    \< 2 seconds
  SMS analysis                                         \< 500 ms
  QR analysis                                          \< 800 ms
  Model loading                                     \< 3 seconds
  Memory                                               \< 250 MB
  Average CPU                                             \< 15%
  Battery drain                                      \< 5% daily
  App size                \< 80 MB excluding downloadable models
  Crash-free sessions                                   \> 99.5%

These targets are defined in the PRD's Android performance requirements.
fileciteturn2file7L1020-L1029

------------------------------------------------------------------------

# 32. Coding Rules for the AI Development Agent

The implementation agent must follow these rules:

1.  Preserve the Clean Architecture + MVVM separation.
2.  Do not place business logic directly inside Compose UI.
3.  UI must communicate with ViewModels.
4.  ViewModels must call domain use cases.
5.  Use cases must communicate through repository interfaces.
6.  Repositories must abstract local and remote data sources.
7.  AI models must be accessed through dedicated interfaces/services.
8.  Do not couple the Android UI directly to PostgreSQL.
9.  Do not expose database credentials in Android or web code.
10. Never store plaintext passwords.
11. Never hard-code API secrets.
12. Use environment variables/secrets for backend credentials.
13. All admin API routes must enforce ADMIN authorization.
14. A normal user must only receive their own private detection history.
15. Backend failure must never crash the local detection pipeline.
16. Synchronization must be asynchronous.
17. Use WorkManager for deferred synchronization.
18. Keep SMS and QR detection modules independently testable.
19. Keep model implementations replaceable through interfaces.
20. Do not introduce a new framework unless required by the PRD or
    necessary to satisfy an implementation constraint.
21. Prefer the technology stack specified in this document.
22. Do not silently change the architecture; document any required
    architectural deviation.

------------------------------------------------------------------------

# 33. Implementation Priority

The AI coding agent should implement in this order:

``` text
Phase 1
Project Structure
    ↓
Android Base Architecture
    ↓
Authentication
    ↓
Local Database
    ↓
Dashboard
```

``` text
Phase 2
SMS Monitoring
    ↓
NLP Preprocessing
    ↓
TinyBERT Integration
    ↓
Risk Engine
    ↓
Alerts
```

``` text
Phase 3
QR Scanner
    ↓
ZXing
    ↓
URL Feature Extraction
    ↓
XGBoost
    ↓
Risk Engine
```

``` text
Phase 4
Cross Verification
    ↓
Explainable AI
    ↓
Threat History
    ↓
Feedback
```

``` text
Phase 5
FastAPI Backend
    ↓
Firebase Authentication Verification
    ↓
PostgreSQL
    ↓
REST APIs
    ↓
Synchronization
```

``` text
Phase 6
Admin Web Dashboard
    ↓
Admin Authentication
    ↓
Threat Monitoring
    ↓
Threat Details
    ↓
Analytics
```

``` text
Phase 7
Testing
    ↓
Security Testing
    ↓
Performance Optimization
    ↓
Docker
    ↓
CI/CD
    ↓
Deployment
```

------------------------------------------------------------------------

# 34. Final Architecture Contract

The AI development agent should treat the following as the authoritative
architecture:

``` text
ANDROID
Kotlin + Jetpack Compose
        ↓
Clean Architecture + MVVM
        ↓
Room + DataStore
        ↓
ONNX Runtime
        ↓
TinyBERT + XGBoost + Isolation Forest
        ↓
Local Risk/Decision Engine
        ↓
Offline-first detection

              │
              │ HTTPS / REST
              ▼

BACKEND
FastAPI
        ↓
Firebase Authentication Verification
        ↓
RBAC + Validation + Rate Limiting
        ↓
Business Services
        ↓
SQLAlchemy
        ↓
PostgreSQL

              │
              │ REST API
              ▼

WEB ADMIN
Next.js / React
        ↓
Firebase Authentication
        ↓
ADMIN Role Verification
        ↓
Threat Dashboard
        ↓
Threat Monitoring
        ↓
Threat Details
        ↓
Analytics
```

The core architectural principle is:

> **Detection happens locally first; cloud services extend the system
> rather than becoming a dependency for core protection.**

This preserves the PRD's privacy-first, low-latency, offline-capable
design while providing the centralized threat intelligence and
administrator monitoring capability. fileciteturn2file0L137-L151

------------------------------------------------------------------------

## 35. Source Alignment

This Architecture document is derived from the supplied `PRD.md`,
particularly its sections covering:

-   Android architecture and mobile technology stack
-   Backend architecture and infrastructure
-   Authentication and threat management
-   Admin dashboard
-   AI/ML pipelines
-   Complete repository structure
-   Security requirements
-   Deployment and CI/CD

The PRD defines the overall product as a real-time AI/ML Android
cybersecurity system for Smishing and Quishing detection.
fileciteturn1file6L1275-L1292
