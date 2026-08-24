# Phases.md

# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

**Version:** 1.0\
**Purpose:** Define the implementation phases, dependencies,
deliverables, milestones, and acceptance criteria for building the
complete project.

This document is intended to be used together with:

1.  `PRD.md` --- Product and functional requirements
2.  `Architecture.md` --- System architecture and project structure
3.  `Rules.md` --- Development rules, approved technologies, error
    handling, security boundaries, and AI boundaries

------------------------------------------------------------------------

# 1. Implementation Strategy

The project must be implemented incrementally.

Do **not** attempt to build Android, backend, database, web dashboard,
and AI simultaneously.

The implementation order is:

``` text
Phase 0
Project Foundation
        ↓
Phase 1
Android Shell + Authentication
        ↓
Phase 2
Local Database + Core Android Modules
        ↓
Phase 3
SMS Monitoring Pipeline
        ↓
Phase 4
QR / Quishing Pipeline
        ↓
Phase 5
AI/ML Detection Engine
        ↓
Phase 6
Risk + Explainable AI
        ↓
Phase 7
Backend + PostgreSQL
        ↓
Phase 8
Authentication + RBAC + Synchronization
        ↓
Phase 9
Admin Web Dashboard
        ↓
Phase 10
Security + Testing + Hardening
        ↓
Phase 11
Integration + Performance + UAT
        ↓
Phase 12
Release + Documentation
```

The core principle is:

``` text
Build → Test → Integrate → Validate → Continue
```

A phase should not be considered complete merely because its code
compiles. Each phase must satisfy its acceptance criteria.

------------------------------------------------------------------------

# 2. Phase 0 --- Project Foundation

## Objective

Create the complete project workspace and establish the development
conventions before implementing application functionality.

## Tasks

### Repository

Create:

``` text
root/
├── android/
├── backend/
├── ai/
├── web/
├── docs/
├── scripts/
├── tests/
├── PRD.md
├── Architecture.md
├── Rules.md
└── Phases.md
```

### Development Configuration

Set up:

-   Git repository
-   Branching strategy
-   `.gitignore`
-   Environment-variable strategy
-   Development configuration
-   README
-   Coding conventions
-   Commit conventions

### Environment

Prepare:

-   Android Studio
-   Kotlin/Android SDK
-   Python environment
-   PostgreSQL development environment
-   Node.js environment
-   Docker
-   GitHub Actions

## Deliverables

``` text
✓ Repository initialized
✓ Project directories created
✓ Android project created
✓ Backend project created
✓ AI project created
✓ Web project created
✓ Documentation connected
✓ Environment configuration documented
```

## Acceptance Criteria

-   All four major application components can be opened independently.
-   No secrets are committed to Git.
-   Android, backend, AI, and web projects have basic build/run
    commands.
-   Documentation files are present at repository root.

------------------------------------------------------------------------

# 3. Phase 1 --- Android Shell and Authentication

## Objective

Create the initial Android application structure and establish the user
authentication flow.

## Tasks

### Android Architecture

Implement:

``` text
Presentation
    ↓
ViewModel
    ↓
Domain
    ↓
Repository
    ↓
Data
```

Configure Hilt.

### UI

Create:

-   Splash screen
-   Onboarding
-   Login
-   Registration
-   Authentication state
-   Home/dashboard shell
-   Navigation
-   Settings shell

### Authentication

Implement:

-   Firebase Authentication
-   Login
-   Registration
-   Logout
-   Session persistence
-   Authentication state
-   Authentication error states

Do not store passwords in the application database.

## Deliverables

``` text
✓ App launches
✓ Splash screen works
✓ Authentication screens work
✓ User can register/login/logout
✓ Session persists
✓ Unauthorized screens are protected
✓ Hilt dependency injection works
```

## Acceptance Criteria

-   An unauthenticated user cannot access protected application screens.
-   A successfully authenticated user reaches the dashboard.
-   Logout clears the authenticated application state.
-   Authentication failures are handled without crashes.

------------------------------------------------------------------------

# 4. Phase 2 --- Local Database and Core Android Modules

## Objective

Implement the local storage layer and core application modules before
connecting AI detection.

## Tasks

### Room

Create entities for:

``` text
User
ThreatHistory
SMSAnalysis
QRAnalysis
Feedback
Settings
ModelVersion
```

### Repository Layer

Create repositories for:

``` text
Authentication
Threat History
SMS Analysis
QR Analysis
Feedback
Settings
Model
```

### Core Modules

Implement:

``` text
Authentication
Dashboard
SMS
QR
AI
History
Settings
Notifications
Feedback
```

Each module should be independently testable.

### DataStore

Use DataStore for application preferences/settings where appropriate.

### Secure Storage

Use Android Keystore / approved secure storage mechanisms for sensitive
local information.

## Deliverables

``` text
✓ Room database
✓ DAOs
✓ Entities
✓ Repositories
✓ DataStore
✓ Secure storage
✓ Local history
✓ Settings persistence
```

## Acceptance Criteria

-   Threat records can be created/read/updated/deleted according to
    requirements.
-   Database operations do not occur directly from UI code.
-   Local data survives application restart.
-   Sensitive data is not stored in plaintext.

------------------------------------------------------------------------

# 5. Phase 3 --- SMS Monitoring and Smishing Pipeline

## Objective

Implement real-time SMS acquisition and local preprocessing before
integrating the final AI models.

The PRD defines SMS monitoring as a core application function.
fileciteturn3file3L641-L659

## Tasks

### Permission Flow

Implement:

``` text
SMS Permission
      ↓
Granted
      ↓
Enable Monitoring
```

If denied:

``` text
Permission Denied
      ↓
Explain Requirement
      ↓
Disable SMS Monitoring
```

### SMS Receiver

Implement:

-   SMS receiver
-   SMS parser
-   Sender extraction
-   Message extraction
-   Timestamp
-   URL extraction

### Preprocessing

Implement:

-   Text normalization
-   Tokenization
-   URL extraction
-   Suspicious keyword extraction
-   Sender pattern extraction
-   Urgency indicators
-   Brand/entity extraction

### Local Storage

Store the minimum required information.

## Deliverables

``` text
✓ SMS permission handling
✓ SMS receiver
✓ SMS parser
✓ URL extraction
✓ NLP preprocessing
✓ Local SMS analysis storage
```

## Acceptance Criteria

-   Incoming SMS can be detected when permission is granted.
-   SMS is parsed safely.
-   Invalid SMS input does not crash the application.
-   SMS monitoring continues to function offline.
-   Raw SMS is not uploaded automatically.

------------------------------------------------------------------------

# 6. Phase 4 --- QR Scanner and Quishing Pipeline

## Objective

Implement QR acquisition, decoding, URL extraction, and preliminary URL
analysis.

## Tasks

### Camera

Implement CameraX:

-   Camera preview
-   Autofocus
-   Flash
-   Zoom
-   Continuous scanning

### Gallery

Implement:

-   Gallery import
-   Image validation
-   QR decoding from image

### QR Decoder

Use ZXing.

Pipeline:

``` text
Camera/Gallery
      ↓
QR Decoder
      ↓
Payload
      ↓
URL Extraction
      ↓
URL Feature Extraction
```

### URL Analysis

Extract features such as:

-   Scheme
-   Domain
-   Hostname
-   Path
-   Query
-   Suspicious URL characteristics

Do not automatically open suspicious URLs.

## Deliverables

``` text
✓ Camera scanner
✓ Gallery scanner
✓ QR decoder
✓ URL extraction
✓ URL feature extraction
✓ QR error handling
```

## Acceptance Criteria

-   Camera QR scanning works.
-   Gallery QR scanning works.
-   Invalid/corrupted QR codes are handled gracefully.
-   QR payloads can be passed to the analysis layer.
-   Suspicious URLs are never automatically opened.

------------------------------------------------------------------------

# 7. Phase 5 --- AI/ML Detection Engine

## Objective

Implement the actual machine-learning detection components.

The PRD defines:

``` text
TinyBERT → SMS classification
XGBoost → URL classification
Isolation Forest → anomaly detection
```

fileciteturn3file8L1350-L1361

## Tasks

### AI Workspace

Create:

``` text
ai/
├── preprocessing/
├── feature_engineering/
├── tinybert/
├── xgboost/
├── anomaly/
├── explainable_ai/
├── training/
├── evaluation/
└── deployment/
```

### Dataset

Prepare:

-   Dataset collection
-   Cleaning
-   Labeling
-   Train/validation/test split

Current PRD split:

``` text
70% Training
15% Validation
15% Testing
```

### Training

Implement:

``` text
Dataset
 ↓
Preprocessing
 ↓
Feature Engineering
 ↓
Training
 ↓
Validation
 ↓
Testing
 ↓
Optimization
 ↓
Export
```

### Model Evaluation

Measure:

-   Accuracy
-   Precision
-   Recall
-   F1-score
-   ROC-AUC
-   Confusion matrix

PRD target metrics:

``` text
Accuracy       ≥95%
Precision      ≥94%
Recall         ≥95%
F1             ≥94%
False Positive ≤3%
```

These are project targets, not guaranteed outcomes.
fileciteturn3file9L1787-L1856

## Deliverables

``` text
✓ Trained TinyBERT model
✓ Trained XGBoost model
✓ Isolation Forest model
✓ Evaluation reports
✓ Model artifacts
✓ ONNX export
✓ Model version metadata
```

## Acceptance Criteria

-   Models can be executed independently.
-   Model outputs have defined schemas.
-   Evaluation results are recorded.
-   Models can be exported to the intended runtime.
-   Model artifacts are versioned.

------------------------------------------------------------------------

# 8. Phase 6 --- Risk Engine and Explainable AI

## Objective

Combine the individual AI outputs into a single risk decision and
produce human-readable explanations.

The PRD defines the conceptual risk engine as a combination of:

``` text
TinyBERT
XGBoost
Isolation Forest
Rule Engine
Context Engine
```

fileciteturn3file9L1720-L1755

## Tasks

### Risk Engine

Implement:

``` text
SMS score
URL score
QR features
Context score
Anomaly score
        ↓
Final Risk Score
```

Categories:

``` text
0–25     Safe
26–50    Low
51–75    Medium
76–100   High
```

### Context Correlation

Correlate:

``` text
SMS
 ↓
Brand/Sender
 ↓
URL
 ↓
QR
 ↓
Context
 ↓
Risk
```

### Explainable AI

Generate evidence-based reasons such as:

``` text
Unknown sender
Urgency language
Suspicious domain
Homograph indicator
Missing HTTPS
Suspicious QR payload
```

Explanations must reflect actual features and model/rule evidence.

## Deliverables

``` text
✓ Risk engine
✓ Threat categories
✓ Context engine
✓ XAI explanation layer
✓ Threat report
✓ Recommended action
```

## Acceptance Criteria

Every analyzed event produces:

``` text
Risk Score
Threat Category
Model Results
Reasons
Recommended Action
Timestamp
```

------------------------------------------------------------------------

# 9. Phase 7 --- Backend and PostgreSQL

## Objective

Create the centralized backend responsible for authentication
verification, synchronization, threat telemetry, analytics, feedback,
and administrative data access.

The PRD specifies FastAPI and PostgreSQL for the backend architecture.
fileciteturn3file0L19-L28

## Tasks

### FastAPI

Create:

``` text
backend/
├── api/
├── auth/
├── services/
├── ai/
├── models/
├── repositories/
├── middleware/
├── schemas/
├── database/
├── config/
└── tests/
```

### Database

Implement PostgreSQL tables for the required server-side data.

Core categories:

``` text
Users
Devices
Threat/Security Events
Analytics
Feedback
Models
Audit Logs
Threat Intelligence
```

### API

Implement versioned endpoints:

``` text
/api/v1/auth/*
/api/v1/users/*
/api/v1/detections/*
/api/v1/feedback/*
/api/v1/analytics/*
/api/v1/models/*
/api/v1/admin/*
```

### Validation

Use Pydantic schemas.

### Security

Implement:

-   JWT/Firebase token verification
-   RBAC
-   Rate limiting
-   Input validation
-   Audit logging
-   HTTPS/TLS

## Deliverables

``` text
✓ FastAPI server
✓ PostgreSQL database
✓ SQLAlchemy models
✓ Migrations
✓ Pydantic schemas
✓ API endpoints
✓ Authentication verification
✓ RBAC middleware
✓ API tests
```

## Acceptance Criteria

-   Backend starts successfully.
-   Database migrations work.
-   Protected endpoints reject unauthenticated requests.
-   Admin endpoints reject non-admin users.
-   Invalid request payloads are rejected.
-   Android is never given direct database access.

------------------------------------------------------------------------

# 10. Phase 8 --- Authentication, RBAC and Synchronization

## Objective

Connect the authenticated Android application to the backend while
preserving local-first detection.

The PRD explicitly requires role verification, strict API validation,
data minimization, TLS, rate limiting, and audit logging.
fileciteturn3file0L11-L16

## Tasks

### Authentication

Implement:

``` text
Android Login
     ↓
Firebase Authentication
     ↓
Session Token
     ↓
Backend Token Verification
     ↓
Authorized API Access
```

### User Roles

Implement:

``` text
USER
ADMIN
```

### Synchronization

Implement:

``` text
Local Detection
      ↓
Local Room
      ↓
Sync Queue
      ↓
Backend API
      ↓
PostgreSQL
```

If offline:

``` text
Local Room
      ↓
Queue
      ↓
Retry when Online
```

### Data Minimization

Send only the telemetry required by the backend.

Do not automatically upload raw SMS content.

## Deliverables

``` text
✓ End-to-end authentication
✓ RBAC
✓ Token verification
✓ Sync queue
✓ Offline synchronization
✓ Retry logic
✓ Conflict handling
```

## Acceptance Criteria

-   User can authenticate on Android.
-   Backend verifies the authentication token.
-   User can access only authorized data.
-   Admin APIs are protected.
-   Offline events synchronize when connectivity returns.
-   Local detection never waits for synchronization.

------------------------------------------------------------------------

# 11. Phase 9 --- Admin Web Dashboard

## Objective

Build the web interface for authorized security administrators.

The PRD defines a web security dashboard for monitoring threat events,
analytics, threat details, and administrative operations.
fileciteturn3file0L41-L50

## Tasks

### Authentication

Implement:

``` text
Admin Login
 ↓
Firebase Authentication
 ↓
Token
 ↓
Backend Verification
 ↓
ADMIN Role
 ↓
Dashboard
```

### Dashboard

Create:

-   Overview
-   Threat monitoring
-   Threat details
-   Analytics
-   Users
-   Model information
-   Feedback
-   Audit logs

### Threat Monitoring

Display:

``` text
Threat Type
Risk Score
Timestamp
Source
Model Version
Status
```

### Filtering

Implement:

-   Threat type filter
-   Risk-level filter
-   Date filter
-   Status filter
-   Search

### Analytics

Implement charts for:

-   Threat counts
-   Threat trends
-   Risk distribution
-   Detection categories
-   Model statistics

## Deliverables

``` text
✓ Admin login
✓ Protected routes
✓ Dashboard
✓ Threat monitoring table
✓ Threat detail page
✓ Analytics
✓ Filtering
✓ Search
✓ Admin actions
✓ Audit log view
```

## Acceptance Criteria

-   Only authorized admins can access the dashboard.
-   Dashboard uses backend APIs.
-   Dashboard does not directly access PostgreSQL.
-   Threat data is displayed correctly.
-   Admin actions generate audit records.

------------------------------------------------------------------------

# 12. Phase 10 --- Security, Privacy and Hardening

## Objective

Perform a dedicated security pass across the entire system.

The PRD follows a defense-in-depth security architecture covering
device, application, AI, network, backend, database, and infrastructure
security. fileciteturn3file4L914-L985

## Tasks

### Android Security

Verify:

-   Secure storage
-   Keystore usage
-   No plaintext secrets
-   Secure authentication
-   Network security
-   Model integrity
-   Permission handling
-   APK integrity

### Backend Security

Verify:

-   Authentication
-   Authorization
-   RBAC
-   Validation
-   Rate limiting
-   Secure headers
-   CORS configuration
-   Error responses
-   Database permissions
-   Audit logging

### AI Security

Verify:

-   Model integrity
-   Model versioning
-   Model verification
-   Model rollback
-   No unauthorized model replacement

### Privacy

Verify:

-   SMS minimization
-   Consent
-   Local-first processing
-   Deletion
-   Anonymized telemetry
-   No sensitive logs

## Security Testing

Use appropriate project-approved security testing tools and methods,
including:

``` text
OWASP ZAP
Nessus
Static analysis
Dependency scanning
API security testing
Authentication testing
RBAC testing
```

## Deliverables

``` text
✓ Security test report
✓ Vulnerability report
✓ Remediation report
✓ Privacy verification
✓ Secure configuration
```

------------------------------------------------------------------------

# 13. Phase 11 --- Complete Integration Testing

## Objective

Validate the complete end-to-end system.

## End-to-End Flow

### Smishing

``` text
Incoming SMS
 ↓
SMS Receiver
 ↓
Preprocessing
 ↓
TinyBERT
 ↓
Risk Engine
 ↓
Threat Decision
 ↓
Notification
 ↓
Room
 ↓
Sync Queue
 ↓
FastAPI
 ↓
PostgreSQL
 ↓
Admin Dashboard
```

### Quishing

``` text
Camera
 ↓
QR Decoder
 ↓
URL Extraction
 ↓
URL Feature Engineering
 ↓
XGBoost
 ↓
Isolation Forest
 ↓
Context Engine
 ↓
Risk Engine
 ↓
Threat Report
 ↓
Room
 ↓
Sync
 ↓
Backend
 ↓
Admin Dashboard
```

## Test Scenarios

Test:

-   Safe SMS
-   Suspicious SMS
-   Malicious SMS
-   Safe QR
-   Suspicious QR
-   Malicious QR
-   Unknown URL
-   Corrupted QR
-   No internet
-   Backend unavailable
-   Model unavailable
-   Model update failure
-   Permission denied
-   Invalid API request
-   Expired authentication
-   Unauthorized admin request
-   Duplicate synchronization
-   Database failure

## Deliverables

``` text
✓ Integration test suite
✓ End-to-end test results
✓ Failure recovery results
✓ Bug report
✓ Fixed defects
```

------------------------------------------------------------------------

# 14. Phase 12 --- Performance and Optimization

## Objective

Optimize the application according to the PRD performance requirements.

PRD targets include:

``` text
SMS Analysis       <500 ms
QR Analysis        <800 ms
App Launch         <2 sec
Model Loading      <3 sec
Memory             <250 MB
```

fileciteturn3file3L695-L702

## Tasks

### Android

Optimize:

-   Startup
-   Model loading
-   Memory
-   Battery
-   Camera lifecycle
-   Database queries
-   Background services

### AI

Optimize:

-   ONNX model size
-   Quantization
-   Inference time
-   Memory
-   Model loading

### Backend

Optimize:

-   Database queries
-   API latency
-   Connection pooling
-   Caching where justified
-   Async processing

### Web

Optimize:

-   API requests
-   Table rendering
-   Charts
-   Initial load

## Acceptance Criteria

The application should meet or approach the defined performance targets
without weakening security or detection quality.

------------------------------------------------------------------------

# 15. Phase 13 --- User Acceptance Testing

## Objective

Validate that the application satisfies the user-facing acceptance
criteria in the PRD.

The PRD acceptance criteria require real-time SMS monitoring, QR
scanning, AI classification, URL analysis, context correlation, risk
scoring, alerts, threat history, feedback, and offline functionality.
fileciteturn3file3L807-L820

## UAT Checklist

``` text
[ ] Registration works
[ ] Login works
[ ] Logout works
[ ] Dashboard works
[ ] SMS monitoring works
[ ] QR scanner works
[ ] Gallery QR import works
[ ] AI classification works
[ ] Risk score is displayed
[ ] Explanation is displayed
[ ] Threat recommendation is displayed
[ ] Threat history works
[ ] Search works
[ ] Filtering works
[ ] Feedback works
[ ] Notifications work
[ ] Offline detection works
[ ] Synchronization works
[ ] Admin dashboard works
[ ] RBAC works
```

## Deliverables

``` text
✓ UAT checklist
✓ UAT results
✓ Known issues list
✓ Final fixes
✓ Acceptance sign-off
```

------------------------------------------------------------------------

# 16. Phase 14 --- Deployment and Release

## Objective

Prepare the complete system for demonstration/deployment.

## Tasks

### Android

Prepare:

-   Release build
-   App signing
-   ProGuard/R8 where appropriate
-   Release configuration
-   Versioning

### Backend

Deploy:

``` text
Docker
 ↓
Nginx
 ↓
FastAPI
 ↓
PostgreSQL
```

### Web

Build and deploy:

``` text
Next.js / React
 ↓
Production environment
```

### AI

Package:

``` text
Model
 ↓
Version
 ↓
Integrity metadata
 ↓
Deployment
```

### CI/CD

Pipeline:

``` text
Git Push
 ↓
Build
 ↓
Unit Tests
 ↓
Integration Tests
 ↓
Security Checks
 ↓
AI Tests
 ↓
Package
 ↓
Deploy
```

## Deliverables

``` text
✓ Release APK/AAB
✓ Production backend
✓ PostgreSQL database
✓ Web dashboard
✓ AI model artifacts
✓ CI/CD pipeline
✓ Deployment documentation
```

------------------------------------------------------------------------

# 17. Phase 15 --- Final Documentation

## Objective

Produce complete technical and academic documentation for the major
project.

## Documentation

Create/update:

``` text
README.md
PRD.md
Architecture.md
Rules.md
Phases.md

docs/
├── API.md
├── Database.md
├── AI-Model.md
├── Security.md
├── Testing.md
├── Deployment.md
├── User-Guide.md
└── Troubleshooting.md
```

## Academic Deliverables

Prepare:

-   Project report
-   System architecture diagram
-   Data-flow diagrams
-   Use-case diagram
-   Sequence diagrams
-   Database ER diagram
-   AI pipeline diagram
-   Screenshots
-   Test results
-   Performance results
-   Security testing results
-   Model evaluation results
-   Future scope

------------------------------------------------------------------------

# 18. Phase Dependency Map

``` text
Phase 0
  │
  ├───────────────┐
  ↓               ↓
Phase 1        Phase 2
  │               │
  └───────┬───────┘
          ↓
       Phase 3
          │
          ↓
       Phase 4
          │
          ↓
       Phase 5
          │
          ↓
       Phase 6
          │
          ├──────────────→ Phase 7
          │                  │
          └──────────────────┘
                             ↓
                          Phase 8
                             │
                             ↓
                          Phase 9
                             │
                             ↓
                         Phase 10
                             │
                             ↓
                         Phase 11
                             │
                             ↓
                         Phase 12
                             │
                             ↓
                         Phase 13
                             │
                             ↓
                         Phase 14
                             │
                             ↓
                         Phase 15
```

------------------------------------------------------------------------

# 19. Recommended Development Order for a Small Team

If the project is being developed by a small student team, divide
responsibilities as follows.

## Developer A --- Android

Own:

``` text
Phase 1
Phase 2
Phase 3
Phase 4
Phase 6
```

## Developer B --- AI/ML

Own:

``` text
Phase 5
AI portion of Phase 6
AI evaluation
Model optimization
```

## Developer C --- Backend/Web

Own:

``` text
Phase 7
Phase 8
Phase 9
```

## Shared

All developers participate in:

``` text
Phase 0
Phase 10
Phase 11
Phase 12
Phase 13
Phase 14
Phase 15
```

------------------------------------------------------------------------

# 20. Suggested Milestones

## Milestone 1 --- Application Skeleton

Completed when:

``` text
Android app launches
Authentication works
Navigation works
Room works
```

Corresponds to:

``` text
Phase 0 → Phase 2
```

------------------------------------------------------------------------

## Milestone 2 --- Local Detection Prototype

Completed when:

``` text
SMS is captured
QR is scanned
Local analysis works
Threat history works
```

Corresponds to:

``` text
Phase 3 → Phase 4
```

------------------------------------------------------------------------

## Milestone 3 --- AI Detection Prototype

Completed when:

``` text
TinyBERT works
XGBoost works
Isolation Forest works
Risk Engine works
XAI works
```

Corresponds to:

``` text
Phase 5 → Phase 6
```

------------------------------------------------------------------------

## Milestone 4 --- Cloud Integration

Completed when:

``` text
Authentication
+
Backend
+
PostgreSQL
+
Synchronization
+
RBAC
```

all work together.

Corresponds to:

``` text
Phase 7 → Phase 8
```

------------------------------------------------------------------------

## Milestone 5 --- Admin Security Dashboard

Completed when:

``` text
Admin Login
 ↓
Threat Monitoring
 ↓
Threat Details
 ↓
Analytics
 ↓
Administrative Actions
```

works through protected APIs.

Corresponds to:

``` text
Phase 9
```

------------------------------------------------------------------------

## Milestone 6 --- Production-Ready Prototype

Completed when:

``` text
Security
+
Testing
+
Performance
+
UAT
+
Deployment
+
Documentation
```

are complete.

Corresponds to:

``` text
Phase 10 → Phase 15
```

------------------------------------------------------------------------

# 21. Definition of Done

A phase is **DONE** only when all of the following are true:

``` text
[ ] Feature implemented
[ ] Unit tests written
[ ] Integration tests where applicable
[ ] Error handling implemented
[ ] Security requirements checked
[ ] Privacy requirements checked
[ ] Performance checked where applicable
[ ] Documentation updated
[ ] No known critical errors
[ ] Architecture boundaries preserved
[ ] Rules.md requirements followed
```

------------------------------------------------------------------------

# 22. AI Coding Agent Execution Rules

The AI coding agent must implement the project phase-by-phase.

For each phase:

``` text
1. Read PRD.md
2. Read Architecture.md
3. Read Rules.md
4. Read this Phases.md
5. Identify the current phase
6. Implement only the current phase
7. Run tests
8. Fix errors
9. Verify acceptance criteria
10. Update documentation
11. Only then proceed to the next phase
```

The agent must not jump directly from the initial project setup to a
complete production system.

------------------------------------------------------------------------

# 23. AI Agent Phase Boundary

The AI coding agent must NOT implement future-phase functionality early
unless explicitly requested.

Example:

If currently implementing Phase 3:

``` text
Allowed:
✓ SMS receiver
✓ SMS parser
✓ SMS preprocessing
✓ Local SMS storage

Not yet required:
✗ Admin dashboard
✗ PostgreSQL synchronization
✗ Production deployment
✗ Advanced threat analytics
```

This keeps the implementation controlled and reduces integration
failures.

------------------------------------------------------------------------

# 24. Phase Completion Report

At the end of every phase, the AI coding agent should report:

``` text
PHASE: <number and name>

Implemented:
- ...

Files Created:
- ...

Files Modified:
- ...

Tests:
- ...

Errors Fixed:
- ...

Acceptance Criteria:
- [x] ...
- [x] ...

Known Limitations:
- ...

Next Phase:
- ...
```

------------------------------------------------------------------------

# 25. Final Implementation Contract

The project should ultimately reach:

``` text
                    ┌──────────────────────┐
                    │   Android App        │
                    │                      │
                    │ SMS + QR Acquisition │
                    │ Local AI Detection   │
                    │ Risk Engine          │
                    │ Alerts + History     │
                    └──────────┬───────────┘
                               │
                         Secure HTTPS
                               │
                               ▼
                    ┌──────────────────────┐
                    │   FastAPI Backend    │
                    │                      │
                    │ Auth Verification    │
                    │ RBAC                 │
                    │ Validation           │
                    │ Sync                 │
                    │ Analytics            │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │     PostgreSQL       │
                    │                      │
                    │ Users                │
                    │ Threat Events        │
                    │ Analytics            │
                    │ Feedback             │
                    │ Models               │
                    │ Audit Logs            │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Admin Web Dashboard  │
                    │                      │
                    │ Monitoring           │
                    │ Threat Details       │
                    │ Analytics            │
                    │ Management           │
                    └──────────────────────┘
```

The final implementation must preserve:

``` text
LOCAL-FIRST DETECTION
        +
SECURE BACKEND SYNCHRONIZATION
        +
RBAC
        +
PRIVACY
        +
EXPLAINABLE AI
        +
FAULT TOLERANCE
        +
TESTABILITY
```

The application must remain capable of performing core local detection
when the backend or internet connection is unavailable, as required by
the PRD. fileciteturn3file8L1397-L1453

------------------------------------------------------------------------

# 26. Final Project Completion Checklist

``` text
FOUNDATION
[ ] Repository
[ ] Android
[ ] Backend
[ ] AI
[ ] Web
[ ] Documentation

ANDROID
[ ] Authentication
[ ] Dashboard
[ ] SMS Monitoring
[ ] QR Scanner
[ ] AI Inference
[ ] Risk Engine
[ ] Notifications
[ ] History
[ ] Feedback
[ ] Settings
[ ] Offline Mode

AI
[ ] Dataset
[ ] Preprocessing
[ ] TinyBERT
[ ] XGBoost
[ ] Isolation Forest
[ ] Risk Engine
[ ] XAI
[ ] Evaluation
[ ] ONNX
[ ] Model Versioning

BACKEND
[ ] FastAPI
[ ] Authentication Verification
[ ] RBAC
[ ] Pydantic Validation
[ ] PostgreSQL
[ ] APIs
[ ] Synchronization
[ ] Audit Logs
[ ] Analytics

WEB
[ ] Admin Login
[ ] Dashboard
[ ] Threat Monitoring
[ ] Threat Details
[ ] Analytics
[ ] Filtering
[ ] Search
[ ] Admin Actions

SECURITY
[ ] Secure Storage
[ ] TLS
[ ] Authentication
[ ] Authorization
[ ] Rate Limiting
[ ] Input Validation
[ ] Model Integrity
[ ] Privacy
[ ] Security Testing

QUALITY
[ ] Unit Tests
[ ] Integration Tests
[ ] AI Tests
[ ] Security Tests
[ ] Performance Tests
[ ] UAT

RELEASE
[ ] Android Release
[ ] Backend Deployment
[ ] Database Deployment
[ ] Web Deployment
[ ] CI/CD
[ ] Documentation
[ ] Final Demonstration
```

# 27. Final Rule

**Do not build everything at once.**

Build the project in this order:

``` text
FOUNDATION
    ↓
ANDROID
    ↓
LOCAL STORAGE
    ↓
SMS
    ↓
QR
    ↓
AI
    ↓
RISK ENGINE
    ↓
BACKEND
    ↓
SYNC + RBAC
    ↓
ADMIN DASHBOARD
    ↓
SECURITY
    ↓
TESTING
    ↓
OPTIMIZATION
    ↓
UAT
    ↓
DEPLOYMENT
    ↓
DOCUMENTATION
```

Each phase must be stable before the next phase begins.
