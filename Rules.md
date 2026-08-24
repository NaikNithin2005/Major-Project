# Rules.md

## Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

**Version:** 1.0\
**Purpose:** Development rules and boundaries for AI coding agents and
human developers.

This document is derived from the project's `PRD.md` and
`Architecture.md`. It defines what technologies and patterns should be
used, what should be avoided, how failures must be handled, and the
boundaries of the AI/ML subsystem.

------------------------------------------------------------------------

# 1. Core Development Rules

## 1.1 Architecture Rules

MUST:

-   Follow **Clean Architecture + MVVM** for Android.
-   Keep Presentation, ViewModel, Domain, Repository, Data, AI, and
    Storage responsibilities separated.
-   Keep modules independently testable.
-   Use repositories as the data-access abstraction.
-   Keep AI model access behind dedicated interfaces/services.
-   Keep acquisition, analysis, and decision layers separate.
-   Keep backend communication asynchronous.
-   Keep core SMS and QR detection functional without the backend.
-   Use dependency injection through Hilt.
-   Keep future modules independently replaceable through shared
    interfaces.

MUST NOT:

-   Put business logic directly inside Compose UI.
-   Make UI components directly access Room, PostgreSQL, or REST
    endpoints.
-   Make Android connect directly to PostgreSQL.
-   Make the web dashboard connect directly to PostgreSQL.
-   Put authentication/database secrets inside Android or web source
    code.
-   Make backend availability a requirement for local threat detection.
-   Mix model training code into the Android application.

The architecture explicitly requires backend-mediated database access
and asynchronous backend communication that never blocks local
detection.

------------------------------------------------------------------------

# 2. What to Use

## 2.1 Android

  Purpose                 Use
  ----------------------- -----------------------------------------------
  Language                Kotlin
  UI                      Jetpack Compose
  UI Design               Material Design 3
  Architecture            Clean Architecture + MVVM
  Dependency Injection    Hilt
  Local Database          Room
  Preferences             Jetpack DataStore
  Secure Local Storage    EncryptedSharedPreferences / Android Keystore
  Authentication          Firebase Authentication
  HTTP Client             Retrofit + OkHttp
  Camera                  CameraX
  QR Decoding             ZXing
  Background Work         WorkManager
  Continuous Monitoring   Foreground Service
  Async Programming       Kotlin Coroutines
  UI State                StateFlow
  Mobile AI               ONNX Runtime
  Optional AI Runtime     TensorFlow Lite

These are the technologies specified by the PRD/architecture.
fileciteturn3file1L227-L280

------------------------------------------------------------------------

## 2.2 AI/ML

Use:

-   TinyBERT for Smishing/SMS classification.
-   XGBoost for URL/Quishing classification.
-   Isolation Forest for anomaly detection.
-   ONNX Runtime for mobile inference.
-   TensorFlow Lite only where the architecture explicitly permits it.
-   Python for model training and evaluation.
-   Jupyter Notebook for experimentation.
-   Feature engineering before model inference.
-   A dedicated Risk Engine for combining model outputs.
-   A dedicated Explainable AI layer for human-readable reasons.

The AI pipeline defined by the PRD is:

``` text
Data
 ↓
Preprocessing
 ↓
Feature Engineering
 ↓
TinyBERT
 ↓
XGBoost
 ↓
Isolation Forest
 ↓
Risk Engine
 ↓
Explainable AI
```

The PRD specifies the individual model responsibilities and the
conceptual final-risk calculation. fileciteturn3file8L1349-L1363
fileciteturn3file9L1720-L1785

------------------------------------------------------------------------

## 2.3 Backend

Use:

-   Python
-   FastAPI
-   Pydantic
-   PostgreSQL
-   SQLAlchemy
-   Alembic
-   Firebase Authentication token verification
-   REST APIs
-   Docker
-   Nginx
-   GitHub Actions
-   Prometheus/Grafana where monitoring is implemented

Backend responsibilities:

-   Authentication verification
-   Authorization/RBAC
-   API validation
-   Threat telemetry
-   Threat history synchronization
-   Feedback
-   Analytics
-   Model metadata/versioning
-   Threat intelligence
-   Admin APIs

------------------------------------------------------------------------

## 2.4 Web Admin Dashboard

Use:

-   React.js / Next.js
-   TypeScript
-   Tailwind CSS
-   Chart.js
-   Firebase Authentication
-   REST API communication

The web dashboard must access protected backend APIs rather than the
database directly.

------------------------------------------------------------------------

# 3. Libraries and Dependency Rules

## 3.1 Approved Libraries

### Android

``` text
Jetpack Compose
Material 3
Hilt
Room
DataStore
Retrofit
OkHttp
CameraX
ZXing
WorkManager
Kotlin Coroutines
StateFlow
Firebase Authentication
ONNX Runtime
```

### Backend

``` text
FastAPI
Pydantic
SQLAlchemy
Alembic
Firebase Admin/Auth integration
PostgreSQL driver
```

### AI

``` text
Python
TinyBERT
XGBoost
Isolation Forest
ONNX
ONNX Runtime
Jupyter
```

### Web

``` text
React / Next.js
TypeScript
Tailwind CSS
Chart.js
```

------------------------------------------------------------------------

# 4. Library Rules: What to Avoid

## 4.1 Do Not Introduce Duplicate Libraries

Do not add another library when an approved project library already
provides the required functionality.

Examples:

``` text
CameraX already exists
→ Do not introduce another camera framework.

ZXing already exists
→ Do not introduce another QR decoder without a documented requirement.

Room already exists
→ Do not add another local database.

Retrofit + OkHttp already exist
→ Do not add another HTTP client for the same API layer.

Hilt already exists
→ Do not introduce another dependency-injection framework.
```

------------------------------------------------------------------------

## 4.2 Do Not Replace the Architecture Without Reason

Do not replace:

``` text
Jetpack Compose
Clean Architecture
MVVM
Room
Hilt
FastAPI
PostgreSQL
ONNX Runtime
Firebase Authentication
```

with alternative frameworks simply because they are familiar or
convenient.

Any architectural replacement requires a documented reason and must
preserve the architecture contract.

------------------------------------------------------------------------

## 4.3 Do Not Add AI Frameworks Unnecessarily

Do not introduce:

-   Large cloud LLM APIs into the core detection path.
-   A second NLP framework when TinyBERT already performs the defined
    SMS classification.
-   A second URL classifier when XGBoost is the defined Quishing model.
-   A second anomaly detector when Isolation Forest is the defined
    anomaly layer.
-   Cloud-only AI inference as a requirement for core protection.

The PRD's core protection model is designed around local/on-device
detection.

------------------------------------------------------------------------

# 5. What to Avoid in Android Code

Avoid:

-   Direct database access from Composables.
-   Network calls directly from Composables.
-   Long-running work on the main thread.
-   Blocking calls during SMS detection.
-   Blocking calls during QR scanning.
-   Storing secrets in source code.
-   Storing sensitive information in plaintext.
-   Excessive background services.
-   Unnecessary wake locks.
-   Repeated model loading.
-   Loading every AI model into memory at application startup if not
    required.
-   Storing unnecessary raw SMS content.
-   Uploading SMS content without user consent.

The PRD emphasizes privacy, local processing, low battery usage, and
offline operation. fileciteturn3file3L719-L729

------------------------------------------------------------------------

# 6. What to Avoid in Backend Code

Never:

-   Allow Android to directly access PostgreSQL.
-   Allow the web dashboard to directly access PostgreSQL.
-   Store passwords in the central threat database.
-   Store authentication tokens in logs.
-   Return another user's detection history.
-   Allow normal users to call admin endpoints.
-   Trust client-provided roles.
-   Trust client-provided risk classifications without validation.
-   Skip Pydantic validation.
-   Skip authentication on protected endpoints.
-   Skip authorization on admin endpoints.
-   Expose database credentials through API responses.
-   Log raw private SMS content unnecessarily.

The central database is intended for profile metadata, security
telemetry, detection results, threat metadata, and threat intelligence
statistics; authentication credentials are handled by the dedicated
authentication provider. fileciteturn3file4L832-L843

------------------------------------------------------------------------

# 7. API Security Rules

Every protected request must follow:

``` text
Request
 ↓
Authentication
 ↓
Authorization
 ↓
Payload Validation
 ↓
Rate Limit
 ↓
Business Logic
 ↓
Database
 ↓
Response
```

## Required

-   HTTPS/TLS.
-   Firebase/JWT verification.
-   RBAC.
-   Pydantic validation.
-   Input sanitization.
-   Rate limiting.
-   Audit logging for administrative/security events.
-   Secure token handling.
-   No sensitive token logging.

The PRD requires RBAC, strict API validation, TLS 1.3, rate limiting,
data minimization, and audit logging. fileciteturn3file0L11-L16

------------------------------------------------------------------------

# 8. API Access Boundaries

## Normal User

A normal user may access:

``` text
/api/v1/users/*
/api/v1/detections/*
/api/v1/feedback/*
```

Only the authenticated user's permitted data may be returned.

## Admin

Admin APIs:

``` text
/api/v1/admin/*
```

must require a verified `ADMIN` role.

``` text
USER
  ↓
Own data only

ADMIN
  ↓
Authorized administrative data
```

Never rely on a role supplied only in the request body or query
parameters.

------------------------------------------------------------------------

# 9. Error Handling Rules

The application must fail gracefully.

## 9.1 No Internet

Expected behavior:

``` text
No Internet
 ↓
Continue Local Detection
 ↓
Store Event Locally
 ↓
Queue Synchronization
 ↓
Retry Later
```

Do not:

``` text
No Internet
 ↓
Disable SMS Detection
```

Core detection must remain available offline where on-device models
support it. fileciteturn3file7L1245-L1257

------------------------------------------------------------------------

## 9.2 Backend Unavailable

``` text
Backend unavailable
 ↓
Do not crash
 ↓
Continue local protection
 ↓
Queue synchronization
 ↓
Retry when connectivity returns
```

The backend is an extension of the detection system, not a dependency
for core detection.

------------------------------------------------------------------------

## 9.3 AI Model Update Failure

``` text
Download New Model
       ↓
Integrity Check
       ↓
Verification Failed
       ↓
Reject New Model
       ↓
Keep Previous Stable Model
```

Never replace a working model with an unverified model.
fileciteturn3file9L1873-L1919

------------------------------------------------------------------------

## 9.4 AI Model Missing

``` text
Model Missing
 ↓
Try Fallback Model
 ↓
If Online → Request Model Download
 ↓
If Offline → Continue With Available Detection Capability
```

The application must not crash because an AI model cannot be loaded.

------------------------------------------------------------------------

## 9.5 Camera Failure

``` text
Camera unavailable
 ↓
Show clear error
 ↓
Offer Gallery Import
```

Gallery QR import is the fallback defined by the architecture.

------------------------------------------------------------------------

## 9.6 QR Decode Failure

``` text
Invalid / Corrupted QR
 ↓
Show:
"Unable to decode QR. Please scan again."
 ↓
Do not crash
```

Never treat a failed decode as a safe QR code.

------------------------------------------------------------------------

## 9.7 SMS Permission Denied

``` text
Permission denied
 ↓
Explain why permission is required
 ↓
Offer retry
 ↓
If still denied
 ↓
Disable SMS monitoring
```

Do not attempt to bypass Android permission controls.

------------------------------------------------------------------------

## 9.8 Timeout

For any timeout:

``` text
Timeout
 ↓
Cancel/terminate operation safely
 ↓
Preserve application state
 ↓
Show meaningful error
 ↓
Retry only when appropriate
```

Do not indefinitely retry a failed operation.

------------------------------------------------------------------------

# 10. Error Handling Principles

Every failure must:

1.  Avoid crashing the application.
2.  Preserve user data.
3.  Preserve local detection capability where possible.
4.  Produce a useful user-facing message when relevant.
5.  Produce a diagnostic log without exposing sensitive information.
6.  Retry only operations that are safe to retry.
7.  Use background retry for synchronization.
8.  Never bypass security controls to recover from an error.

------------------------------------------------------------------------

# 11. AI Boundaries

This section is critical.

## 11.1 What AI Is Allowed to Do

AI may:

-   Analyze SMS text.
-   Extract/consume SMS features.
-   Classify SMS messages.
-   Analyze QR-derived URLs.
-   Analyze URL features.
-   Detect anomalous feature combinations.
-   Produce model probabilities/scores.
-   Contribute to the final risk score.
-   Provide feature-based explanations.
-   Support threat classification.
-   Support threat analytics.
-   Generate feedback signals for future model improvement.

The defined AI components are TinyBERT, XGBoost, Isolation Forest, Risk
Engine, and Explainable AI. fileciteturn3file9L1616-L1662

------------------------------------------------------------------------

# 12. What AI Is NOT Allowed to Do

AI must NOT:

-   Access Firebase credentials.
-   Access PostgreSQL credentials.
-   Modify authentication roles.
-   Bypass Android permissions.
-   Directly modify the database.
-   Directly call admin APIs without authorization.
-   Upload private SMS content without the permitted data flow/consent.
-   Install or update itself without model verification.
-   Replace a working model with an unverified model.
-   Disable application security controls.
-   Change API authorization rules.
-   Make architectural decisions that bypass `PRD.md` or
    `Architecture.md`.
-   Treat its prediction as unquestionable truth.
-   Claim that an unknown item is definitely safe merely because
    confidence is low.
-   Execute arbitrary external URLs.
-   Automatically open suspicious URLs.
-   Automatically interact with suspicious websites.
-   Use external AI services as a mandatory dependency for offline
    protection.

------------------------------------------------------------------------

# 13. AI Is a Detection Component, Not a Security Authority

The architecture separates:

``` text
AI Models
   ↓
Risk Engine
   ↓
Decision Engine
   ↓
Alert / Recommendation
```

The AI model should produce evidence/signals.

The Risk Engine combines:

``` text
TinyBERT
XGBoost
Isolation Forest
Rule Engine
Context Engine
```

into the final risk score.

The PRD defines a conceptual weighted risk calculation and categories:

``` text
0–25     Safe
26–50    Low
51–75    Medium
76–100   High
```

The exact formula must remain configurable rather than being duplicated
throughout the codebase. fileciteturn3file9L1720-L1760

------------------------------------------------------------------------

# 14. AI Explainability Rules

Every high-risk or suspicious result should expose understandable
reasons.

Example:

``` text
Risk Score: 91

Reasons:
✓ Banking urgency
✓ Unknown sender
✓ HTTPS missing
✓ Newly observed domain
✓ Homograph detected
✓ Suspicious QR payload
```

AI explanations must be based on actual model/rule features.

Do NOT generate explanations that are unrelated to the evidence.

Do NOT invent reasons simply to make a detection appear more convincing.

The PRD explicitly requires explainable risk reasons and feature
importance for debugging/model evaluation.
fileciteturn3file9L1761-L1785

------------------------------------------------------------------------

# 15. AI Confidence and Uncertainty Rules

A model score is not proof.

The system should distinguish:

``` text
Model output
      ↓
Risk score
      ↓
Threat category
      ↓
Explanation
      ↓
Recommended action
```

The application must preserve uncertainty where the model is uncertain.

Do not convert:

``` text
Low confidence
```

into:

``` text
Definitely Safe
```

without supporting evidence from the complete detection pipeline.

------------------------------------------------------------------------

# 16. AI Data Privacy Rules

AI processing should follow:

``` text
Input
 ↓
Minimum Required Data
 ↓
Local Processing
 ↓
Risk Result
 ↓
Minimal Telemetry
```

Rules:

-   Keep user data local whenever possible.
-   Do not upload raw SMS without consent.
-   Do not include raw SMS content in anonymized monitoring metrics.
-   Minimize stored private content.
-   Anonymize feedback where applicable.
-   Allow user-controlled deletion.
-   Do not expose private content to administrators unnecessarily.

The PRD explicitly states that SMS should remain local whenever possible
and should not be uploaded without consent.
fileciteturn3file3L719-L731

------------------------------------------------------------------------

# 17. AI Model Lifecycle Rules

All production models must follow:

``` text
Training
 ↓
Validation
 ↓
Testing
 ↓
Optimization
 ↓
Export
 ↓
Versioning
 ↓
Signing
 ↓
Storage
 ↓
Download
 ↓
Integrity Verification
 ↓
Activation
```

Required:

-   Semantic model versioning.
-   Integrity verification.
-   Model validation.
-   Rollback support.
-   Previous stable model retention.
-   Quantization/pruning where required for mobile performance.

The PRD requires signed/versioned models, integrity verification,
rollback support, and rejection of unverified updates.
fileciteturn3file9L1873-L1919

------------------------------------------------------------------------

# 18. AI Training Rules

Training pipeline:

``` text
Dataset
 ↓
Clean
 ↓
Label
 ↓
Split
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

Current PRD split:

``` text
Training      70%
Validation    15%
Testing       15%
```

Required evaluation metrics:

-   Accuracy
-   Precision
-   Recall
-   F1-score
-   ROC-AUC
-   Confusion Matrix

PRD targets:

``` text
Accuracy      ≥95%
Precision     ≥94%
Recall        ≥95%
F1            ≥94%
False Positive ≤3%
```

These are project targets, not guarantees that every future model will
automatically meet them. fileciteturn3file9L1787-L1856

------------------------------------------------------------------------

# 19. AI Risk Rules

Known AI risks include:

  Risk                   Required Direction
  ---------------------- ---------------------------------------
  Class imbalance        Balanced sampling / class weighting
  Dataset drift          Periodic retraining
  Overfitting            Cross-validation / regularization
  False positives        Threshold tuning / user feedback
  Adversarial URLs       Ensemble features / anomaly detection
  Resource constraints   Model optimization / quantization

These mitigations are specified in the PRD.
fileciteturn3file1L69-L76

------------------------------------------------------------------------

# 20. Security Boundaries

The AI subsystem must not weaken security.

Never allow AI code to:

-   Disable TLS.
-   Skip authentication.
-   Skip authorization.
-   Read secret keys from source files.
-   Modify RBAC rules dynamically.
-   Disable integrity checks.
-   Bypass Android permissions.
-   Automatically trust downloaded models.
-   Log sensitive credentials.
-   Expose raw private SMS data in monitoring.

Security controls remain outside the authority of the model.

------------------------------------------------------------------------

# 21. Data Storage Rules

## Local Room

Allowed entities include:

``` text
User
ThreatHistory
SMSAnalysis
QRAnalysis
Feedback
Settings
ModelVersion
```

## PostgreSQL

Allowed categories include:

``` text
Users/profile metadata
Devices
Threat/security telemetry
Analytics
Feedback
Model metadata
Version history
Audit logs
Threat intelligence
```

Never store:

``` text
Plaintext passwords
Authentication secrets
Unnecessary private SMS content
Raw credentials
API secrets
```

------------------------------------------------------------------------

# 22. Logging Rules

Logs MUST help diagnose failures without becoming a data-leak channel.

Log:

``` text
Event type
Timestamp
Component
Error code
Model version
Inference duration
Request ID
Non-sensitive status information
```

Avoid logging:

``` text
Passwords
JWT tokens
API keys
Database credentials
Raw private SMS content
Sensitive personal information
```

AI monitoring should use anonymized operational metrics such as
detection counts, false-positive reports, inference time, and
model-version adoption. fileciteturn3file9L1921-L1932

------------------------------------------------------------------------

# 23. Performance Rules

Respect the PRD targets:

  Operation                    Target
  --------------------- -------------
  App cold start               \< 2 s
  SMS analysis              \< 500 ms
  QR analysis               \< 800 ms
  Model loading                \< 3 s
  Memory                    \< 250 MB
  Battery drain           \< 5% daily
  Crash-free sessions        \> 99.5%

Avoid:

-   Repeated model initialization.
-   Blocking main-thread inference.
-   Excessive background work.
-   Unnecessary network requests.
-   Large unnecessary local caches.
-   Holding camera resources longer than required.

The performance targets are defined in the PRD.
fileciteturn3file3L695-L702

------------------------------------------------------------------------

# 24. Privacy Rules

MUST:

-   Process sensitive content locally whenever possible.
-   Request only required permissions.
-   Explain permission requirements.
-   Obtain consent before uploading SMS content.
-   Allow deletion of user data.
-   Minimize telemetry.
-   Anonymize feedback where applicable.
-   Restrict admin visibility of personal information.

MUST NOT:

-   Upload all SMS messages by default.
-   Store unnecessary message bodies.
-   Display personal phone numbers/emails to unauthorized
    administrators.
-   Use private user data for unrelated purposes.

------------------------------------------------------------------------

# 25. Background Service Rules

Use:

``` text
Foreground Service
→ continuous SMS/threat monitoring where required

WorkManager
→ synchronization
→ feedback upload
→ cleanup
→ model update checks
```

Do not use an always-running background process when WorkManager can
perform the task.

Do not use excessive wake locks.

Use efficient broadcast handling and lazy model loading.

The PRD specifically assigns SMS monitoring/threat detection to
foreground services and synchronization/cleanup to WorkManager.
fileciteturn3file6L852-L878

------------------------------------------------------------------------

# 26. Permission Rules

Request only permissions required by enabled features.

Relevant permissions include:

``` text
Receive SMS
Read SMS
Camera
Notifications
Internet
Foreground Service
Gallery/storage access where required
```

Never:

-   Request unrelated permissions.
-   Hide the reason for a permission.
-   Bypass a denied permission.
-   Repeatedly harass the user with permission prompts.

------------------------------------------------------------------------

# 27. UI Rules

Use:

-   Material Design 3.
-   Clear security states.
-   Simple language.
-   Accessible typography.
-   Dark mode.
-   Large text support.
-   Screen-reader support.
-   Color-blind-friendly indicators.

Threat colors should communicate:

``` text
Safe       → Green
Warning    → Yellow
Danger     → Red
Information→ Blue
```

Do not rely on color alone to communicate threat severity.

------------------------------------------------------------------------

# 28. Coding Style Rules

Prefer:

``` text
Small classes
Single responsibility
Clear interfaces
Dependency injection
Immutable UI state
Repository abstraction
Use cases
Testable services
Explicit error types
```

Avoid:

``` text
God classes
Global mutable state
Hard-coded configuration
Duplicated business logic
Hidden network calls
Direct database access from UI
Business logic inside Composables
Large utility classes
```

------------------------------------------------------------------------

# 29. Testing Rules

Every module should be independently testable.

Minimum testing areas:

``` text
Android
├── UI tests
├── ViewModel tests
├── Use-case tests
├── Repository tests
├── SMS parser tests
├── QR decoder tests
├── AI inference tests
└── Database tests

Backend
├── API tests
├── Authentication tests
├── RBAC tests
├── Validation tests
├── Repository tests
├── Service tests
└── Integration tests

AI
├── Preprocessing tests
├── Feature tests
├── Model tests
├── Evaluation tests
├── Threshold tests
└── Regression tests
```

Security tests must verify that unauthorized users cannot access
administrative or other users' data.

------------------------------------------------------------------------

# 30. Change Management Rules

Before changing an architectural component:

1.  Check `PRD.md`.
2.  Check `Architecture.md`.
3.  Check `Rules.md`.
4.  Determine whether the requested change conflicts with an existing
    requirement.
5.  Prefer the existing architecture.
6.  If a change is unavoidable, isolate it behind an interface.
7.  Update documentation when the architecture changes.
8.  Do not silently introduce a new technology.

------------------------------------------------------------------------

# 31. Priority Rules for the AI Coding Agent

When requirements conflict, use this order:

``` text
1. Security
      ↓
2. Privacy
      ↓
3. PRD requirements
      ↓
4. Architecture.md
      ↓
5. Rules.md
      ↓
6. Performance requirements
      ↓
7. Maintainability
      ↓
8. Convenience
```

Do not sacrifice security or privacy merely to make implementation
easier.

------------------------------------------------------------------------

# 32. Absolute Boundaries

The following are hard boundaries.

### AI Boundary

``` text
AI can analyze.
AI can score.
AI can explain.
AI can provide evidence.

AI cannot:
- bypass security,
- access secrets,
- change authorization,
- directly modify databases,
- bypass permissions,
- install unverified models,
- upload private data without consent.
```

### Backend Boundary

``` text
Backend can:
- authenticate,
- authorize,
- validate,
- synchronize,
- store permitted telemetry,
- provide analytics,
- manage model metadata.

Backend cannot become a mandatory dependency for local detection.
```

### Android Boundary

``` text
Android can:
- acquire SMS/QR input,
- run local AI,
- calculate risk,
- alert users,
- maintain local history,
- synchronize asynchronously.

Android cannot:
- directly access PostgreSQL,
- bypass permissions,
- expose secrets,
- depend on network connectivity for core detection.
```

### Admin Boundary

``` text
Admin dashboard can:
- inspect authorized threat data,
- analyze aggregate metrics,
- review threat events,
- perform authorized threat-management operations.

Admin dashboard cannot:
- bypass backend authorization,
- directly manipulate PostgreSQL,
- expose private user information beyond assigned permissions.
```

------------------------------------------------------------------------

# 33. Final Rule

When implementing any feature, the developer/AI agent should ask:

``` text
Does this follow the PRD?
        ↓
Does this follow Architecture.md?
        ↓
Does this follow Rules.md?
        ↓
Does it preserve offline detection?
        ↓
Does it preserve user privacy?
        ↓
Does it preserve security boundaries?
        ↓
Is it testable?
        ↓
Is it using the approved technology stack?
        ↓
Is error handling graceful?
```

If any answer is **No**, do not implement the change without resolving
the conflict.

------------------------------------------------------------------------

# 34. Short Reference

## USE

``` text
Kotlin
Jetpack Compose
Material 3
Clean Architecture
MVVM
Hilt
Room
DataStore
Firebase Authentication
Retrofit
OkHttp
CameraX
ZXing
WorkManager
Coroutines
StateFlow
ONNX Runtime
TinyBERT
XGBoost
Isolation Forest
FastAPI
Pydantic
PostgreSQL
SQLAlchemy
Alembic
Next.js / React
TypeScript
Tailwind CSS
Chart.js
Docker
Nginx
GitHub Actions
```

## AVOID

``` text
Direct client → database access
Unapproved duplicate libraries
Unnecessary frameworks
Cloud-only core AI detection
Hard-coded secrets
Plaintext passwords
Raw token logging
Unnecessary SMS uploads
Unverified AI model updates
Permission bypasses
Blocking network calls
Blocking AI inference on UI thread
Business logic in UI
God classes
Global mutable state
Silent architectural changes
```

## AI BOUNDARY

``` text
AI = Detection + Evidence + Scoring

NOT:

AI = Authentication
AI = Authorization
AI = Secret Management
AI = Database Administration
AI = Permission Management
AI = Unverified Model Deployment
AI = Uncontrolled Data Collection
```

------------------------------------------------------------------------

# 35. Source Alignment

This rules document is based on the project's `PRD.md` and the generated
`Architecture.md`, particularly the sections covering:

-   Approved technology stack
-   Android architecture
-   AI/ML pipeline
-   Security architecture
-   Fault tolerance
-   Privacy
-   Data governance
-   Offline operation
-   Model lifecycle
-   AI risks
-   API security
-   Performance
-   Testing
-   Repository/module boundaries

The PRD explicitly establishes the core stack, Clean Architecture +
MVVM, local AI inference, asynchronous backend communication, fault
tolerance, and security-by-design principles.
fileciteturn3file1L166-L226 fileciteturn3file7L1245-L1261
