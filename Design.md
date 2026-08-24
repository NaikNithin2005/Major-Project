# Design.md

# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

**Version:** 1.0\
**Purpose:** Define the visual, interaction, UX, component, and
design-system rules for the Android application and Web Admin Security
Dashboard.

This document is derived from `PRD.md`, `Architecture.md`, `Rules.md`,
and `Phases.md`.

------------------------------------------------------------------------

# 1. Design Goals

The product UI must communicate **security, trust, simplicity, and
immediate action**.

The design must support:

-   Real-time protection
-   Simple threat explanations
-   Fast threat recognition
-   Clear Safe / Low / Medium / High risk states
-   Minimal user effort
-   Privacy transparency
-   Offline-first behavior
-   Accessibility
-   Low battery usage
-   Consistent navigation
-   Clear separation between normal users and administrators

The PRD describes the Android app as a continuous protection system that
should run silently, consume minimal battery, provide instant alerts,
explain detections clearly, respect privacy, and continue working
offline. fileciteturn4file8L2001-L2014

------------------------------------------------------------------------

# 2. Design Principles

## 2.1 Security First

Every security decision must be visually obvious.

Users should immediately understand:

``` text
What happened?
      ↓
How dangerous is it?
      ↓
Why was it detected?
      ↓
What should I do?
```

------------------------------------------------------------------------

## 2.2 Simple Language

Avoid highly technical language in the primary user interface.

Instead of:

``` text
XGBoost probability = 0.91
```

show:

``` text
High Risk
91/100

Why?
• Suspicious domain
• Unknown sender
• Urgency detected
```

Technical model information may be available in an expanded
details/debug view.

The PRD explicitly requires simple explanations so users can understand
why content is dangerous. fileciteturn4file1L408-L414

------------------------------------------------------------------------

## 2.3 Evidence Before Action

A threat report must show evidence before asking the user to take an
action.

Preferred structure:

``` text
Threat Level
    ↓
Risk Score
    ↓
Threat Type
    ↓
Why?
    ↓
Evidence
    ↓
Recommended Action
    ↓
User Action
```

------------------------------------------------------------------------

## 2.4 Do Not Overload the User

Avoid displaying all AI features, raw URLs, model internals, and
technical metadata at the same time.

Use progressive disclosure:

``` text
Summary
   ↓
Reasons
   ↓
Evidence
   ↓
Technical Details
```

------------------------------------------------------------------------

# 3. Design System

## 3.1 Platform

The Android application must use:

-   Jetpack Compose
-   Material Design 3

The PRD specifies Jetpack Compose, Material Design 3, and Motion Layout
for the mobile UI. fileciteturn4file8L2076-L2129

------------------------------------------------------------------------

# 4. Color System

The interface must use a consistent semantic color system.

## 4.1 Threat Colors

``` text
SAFE
   → Green

LOW RISK
   → Blue/Neutral

MEDIUM RISK
   → Amber/Yellow

HIGH RISK
   → Red
```

The PRD defines the risk categories:

``` text
0–25     Safe
26–50    Low Risk
51–75    Medium Risk
76–100   High Risk
```

fileciteturn4file1L275-L285

Do not rely on color alone. Always display:

-   Label
-   Icon
-   Score
-   Text explanation

------------------------------------------------------------------------

## 4.2 Safe State

Example:

``` text
✓ SAFE

Risk Score
12 / 100

No significant phishing indicators detected.
```

------------------------------------------------------------------------

## 4.3 Medium Risk State

Example:

``` text
⚠ MEDIUM RISK

Risk Score
64 / 100

Review this content before continuing.
```

------------------------------------------------------------------------

## 4.4 High Risk State

Example:

``` text
! HIGH RISK

Risk Score
91 / 100

Do not open this link.

Why?
• Suspicious domain
• Unknown sender
• Urgency detected
```

The PRD's threat-alert specification requires threat type, severity,
reason, and recommended action. fileciteturn4file1L286-L327

------------------------------------------------------------------------

# 5. Typography

Use Material 3 typography hierarchy.

Recommended structure:

``` text
Display
   ↓
Large security score / major status

Headline
   ↓
Page title

Title
   ↓
Card and section titles

Body
   ↓
Explanations and descriptions

Label
   ↓
Buttons, metadata, chips
```

Rules:

-   Keep body text readable.
-   Avoid long paragraphs on threat screens.
-   Use bold/strong hierarchy for threat severity.
-   Do not communicate severity using font color alone.
-   Support Android large-text settings.

------------------------------------------------------------------------

# 6. Icons

Use clear semantic icons.

Examples:

``` text
Home       → Home
History    → History
Scanner    → QR/Scan
Alerts     → Notifications
Settings   → Settings

Safe       → Check
Warning    → Warning
Danger     → Error/Shield
SMS        → Message
QR         → QR Code
Blocked    → Block
Report     → Flag
```

Avoid decorative icons that do not communicate meaning.

------------------------------------------------------------------------

# 7. Shapes and Cards

Use Material 3 cards and surfaces.

Cards should represent:

-   Security Score
-   Today's Threats
-   Safe Messages
-   Blocked Links
-   Recent Alerts
-   Quick Actions
-   Threat Evidence
-   History Items

Avoid excessive card nesting.

Preferred:

``` text
Dashboard
├── Security Score Card
├── Statistics Row
├── Recent Alerts
└── Quick Actions
```

Avoid:

``` text
Card
 └── Card
      └── Card
           └── Card
```

------------------------------------------------------------------------

# 8. Spacing

Use a consistent spacing scale.

Recommended:

``` text
4dp   → micro spacing
8dp   → small spacing
12dp  → compact spacing
16dp  → standard spacing
24dp  → section spacing
32dp  → major separation
```

Do not create arbitrary spacing values throughout the application.

------------------------------------------------------------------------

# 9. Android Navigation Design

The PRD defines:

``` text
Splash
 ↓
Login
 ↓
Permissions
 ↓
Dashboard
```

and the main bottom navigation:

``` text
Home
History
Scanner
Alerts
Settings
```

fileciteturn4file0L37-L82

------------------------------------------------------------------------

# 10. Application Navigation Structure

``` text
Splash
 │
 ├── First Launch → Onboarding
 │                     ↓
 │                  Login/Register
 │
 └── Returning User → Authentication
                         ↓
                    Permissions
                         ↓
                     Dashboard
```

Dashboard:

``` text
Home
 ├── Security Overview
 ├── Today's Threats
 ├── Recent Alerts
 └── Quick Actions

History
 ├── SMS History
 ├── QR History
 └── Filters/Search

Scanner
 ├── Camera
 ├── Flash
 ├── Zoom
 └── Gallery Import

Alerts
 ├── Critical
 ├── Warning
 └── Informational

Settings
 ├── Permissions
 ├── Notifications
 ├── Privacy
 ├── Theme
 └── Model Updates
```

------------------------------------------------------------------------

# 11. Splash Screen

## Purpose

The splash screen should initialize:

-   AI models
-   Model integrity
-   User settings
-   Services
-   Update checks

The PRD defines the splash screen around initialization and AI model
loading, with a maximum duration target of 2 seconds.
fileciteturn4file0L112-L134

## Layout

``` text
┌──────────────────────────────┐
│                              │
│                              │
│            LOGO              │
│                              │
│       AI Protection          │
│          Enabled             │
│                              │
│       Initializing...        │
│                              │
│        ███████░░░             │
│                              │
└──────────────────────────────┘
```

Do not show unnecessary technical logs.

------------------------------------------------------------------------

# 12. Onboarding Design

The PRD defines five onboarding stages:

``` text
Welcome
   ↓
Features
   ↓
Permissions
   ↓
Privacy
   ↓
Ready
```

fileciteturn4file0L136-L166

## Screen 1 --- Welcome

Purpose:

Explain the product in one sentence.

Example:

``` text
Stay protected from
SMS and QR phishing.

Real-time AI protection
for your device.

[ Get Started ]
```

------------------------------------------------------------------------

## Screen 2 --- Features

Show three primary capabilities:

``` text
SMS Protection
QR Protection
AI Risk Analysis
```

Keep the explanation short.

------------------------------------------------------------------------

## Screen 3 --- Permissions

Explain:

``` text
SMS
Used to detect phishing messages.

Camera
Used to scan QR codes.

Notifications
Used for security alerts.
```

Do not request unrelated permissions.

------------------------------------------------------------------------

## Screen 4 --- Privacy

Explain:

``` text
Your messages are processed locally
whenever possible.

SMS content is not uploaded
without your consent.
```

The PRD requires local-first data handling and no SMS upload without
consent. fileciteturn4file2L901-L913

------------------------------------------------------------------------

## Screen 5 --- Ready

``` text
You're protected.

Background protection can now
monitor suspicious SMS and QR codes.

[ Enable Protection ]
```

------------------------------------------------------------------------

# 13. Login Screen

## Layout

``` text
┌──────────────────────────────┐
│            LOGO              │
│                              │
│        Welcome Back          │
│                              │
│  Email                       │
│  ┌────────────────────────┐  │
│  │                        │  │
│  └────────────────────────┘  │
│                              │
│  Password                    │
│  ┌────────────────────────┐  │
│  │                        │  │
│  └────────────────────────┘  │
│                              │
│       [ Login ]              │
│                              │
│  [ Continue with Google ]    │
│                              │
│  Continue as Guest           │
│                              │
│  Don't have an account?      │
│  Create Account              │
└──────────────────────────────┘
```

The PRD specifies Google Sign-In, email login, and Guest Mode as
authentication options. fileciteturn4file0L83-L110

------------------------------------------------------------------------

# 14. Home Dashboard

The dashboard is the primary security overview.

The PRD requires:

-   Security Score
-   Today's Threats
-   SMS Scanned
-   QR Scanned
-   Blocked Attacks
-   Recent Alerts
-   Quick Actions

fileciteturn4file0L203-L259

## Layout

``` text
┌─────────────────────────────────┐
│ Good evening, User              │
│ Your protection is active       │
├─────────────────────────────────┤
│                                 │
│        SECURITY SCORE           │
│             92                  │
│          / 100                  │
│                                 │
│       Protection Active ✓       │
├─────────────────────────────────┤
│ Today's Activity                │
│                                 │
│  Threats     SMS       QR       │
│     5         45        12      │
├─────────────────────────────────┤
│ Recent Alerts                   │
│                                 │
│ ! High Risk SMS                 │
│   2 minutes ago                │
│                                 │
│ ✓ Safe QR                       │
│   8 minutes ago                │
├─────────────────────────────────┤
│ Quick Actions                   │
│                                 │
│ [ Scan QR ] [ History ]         │
└─────────────────────────────────┘

       Home History Scanner Alerts Settings
```

------------------------------------------------------------------------

# 15. Security Score Component

The score must not be the only information displayed.

Recommended:

``` text
       92
      /100

Protection Strong

5 threats detected today
3 blocked
```

The score should be accompanied by a clear status.

Avoid making the score look like a generic credit score.

------------------------------------------------------------------------

# 16. QR Scanner Design

The QR scanner must provide:

-   Camera preview
-   Auto focus
-   Flash
-   Zoom
-   Continuous scanning
-   Gallery import

These are explicitly required by the PRD.
fileciteturn4file1L547-L559

## Layout

``` text
┌──────────────────────────────┐
│ ← Scan QR                    │
│                              │
│       ┌──────────────┐       │
│       │              │       │
│       │   SCAN AREA  │       │
│       │              │       │
│       └──────────────┘       │
│                              │
│ Point camera at QR code      │
│                              │
│                              │
│    ⚡ Flash     ◉ Zoom       │
│                              │
│       [ Gallery ]            │
└──────────────────────────────┘
```

The scanner should not automatically open a decoded URL.

The URL must first pass through the detection pipeline.

------------------------------------------------------------------------

# 17. SMS Analysis Screen

The PRD requires the SMS screen to show:

-   Sender
-   Message
-   Prediction
-   Confidence
-   Reasons

fileciteturn4file1L561-L573

## Layout

``` text
┌──────────────────────────────┐
│ ← SMS Analysis               │
├──────────────────────────────┤
│ Sender                       │
│ +91 XXXXX XXXXX              │
│                              │
│ Message                      │
│ ┌──────────────────────────┐ │
│ │ Your account requires... │ │
│ └──────────────────────────┘ │
│                              │
│        HIGH RISK             │
│          91/100              │
│                              │
│ Why?                         │
│ ✓ Unknown sender             │
│ ✓ Urgency detected           │
│ ✓ Suspicious URL             │
│ ✓ Banking keywords           │
│                              │
│ [ View Threat Report ]       │
└──────────────────────────────┘
```

------------------------------------------------------------------------

# 18. Threat Report Design

This is the most important security screen.

The PRD requires:

-   Risk Meter
-   Threat Type
-   Recommendation
-   Time
-   Evidence

fileciteturn4file2L762-L774

## Layout

``` text
┌──────────────────────────────┐
│ ← Threat Report              │
├──────────────────────────────┤
│                              │
│          HIGH RISK           │
│                              │
│          91 / 100            │
│       ███████████████░        │
│                              │
│ Type                         │
│ SMISHING                     │
│                              │
│ Why was this detected?       │
│                              │
│ ✓ Unknown sender             │
│ ✓ Banking urgency            │
│ ✓ Suspicious domain          │
│ ✓ HTTPS missing              │
│                              │
│ Recommended Action           │
│ Do not open the link.        │
│                              │
│ [ Block ]  [ Report ]        │
│ [ Ignore ] [ Copy Report ]   │
└──────────────────────────────┘
```

The alert action set is defined in the PRD as Block, Ignore, Report, and
Copy Report. fileciteturn4file1L307-L327

------------------------------------------------------------------------

# 19. Threat History Design

The history screen must support:

-   Search
-   Filter
-   Export
-   Delete
-   Sort

fileciteturn4file1L329-L357

## Layout

``` text
┌──────────────────────────────┐
│ Threat History               │
│                              │
│ 🔍 Search threats...         │
│                              │
│ [All] [SMS] [QR]             │
│                              │
│ Today                        │
│                              │
│ 🔴 Smishing       91         │
│    Unknown sender            │
│    10:42 AM                  │
│                              │
│ 🟡 Quishing       64         │
│    Suspicious domain         │
│    09:18 AM                  │
│                              │
│ 🟢 Safe QR        12         │
│    08:42 AM                  │
└──────────────────────────────┘
```

------------------------------------------------------------------------

# 20. Alert Center Design

The PRD defines:

-   Heads-up notification
-   Critical alert
-   Silent alert
-   Background warning

fileciteturn4file1L615-L635

## High-Risk Notification

``` text
⚠ HIGH RISK SMS DETECTED

Fake SBI Login

Risk: 96/100

Do not open the link.

[ View Report ]
```

The notification must provide enough context to understand the danger
without exposing unnecessary private message content.

------------------------------------------------------------------------

# 21. Feedback Design

Feedback options:

``` text
False Positive
False Negative
Correct Detection
```

fileciteturn4file1L347-L357

## Layout

``` text
Was this detection correct?

○ Yes, correct detection
○ No, false positive
○ No, false negative

Optional comment:
┌──────────────────────────────┐
│                              │
└──────────────────────────────┘

[ Submit Feedback ]
```

Feedback should be simple and quick.

------------------------------------------------------------------------

# 22. Settings Design

Settings must contain:

``` text
Protection
├── SMS Monitoring
├── QR Scanner
└── Notifications

Appearance
├── Theme
└── Language

Privacy
├── Data Processing
├── Feedback Data
└── Delete Data

AI
└── Model Version / Updates

About
└── App Information
```

The PRD lists permissions, theme, language, notifications, privacy, and
model version as settings areas. fileciteturn4file1L359-L373

------------------------------------------------------------------------

# 23. Permission UI

Permission status should be visual and understandable.

``` text
┌──────────────────────────────┐
│ Protection Permissions       │
├──────────────────────────────┤
│                              │
│ SMS Monitoring       ✓ ON    │
│ Used for SMS protection      │
│                              │
│ Camera               ✓ ON    │
│ Used for QR scanning         │
│                              │
│ Notifications        ✓ ON    │
│ Used for threat alerts       │
│                              │
│ Internet             ✓ ON    │
│ Used for synchronization     │
└──────────────────────────────┘
```

The PRD defines permission purpose and the flow for granted/denied
permissions. fileciteturn4file0L168-L202

------------------------------------------------------------------------

# 24. Offline State Design

Offline mode must not look like a system failure because core detection
continues to operate offline.

Preferred:

``` text
✓ Protection Active

Offline Mode
Detection is running on your device.

Sync will resume when you're online.
```

Avoid:

``` text
ERROR: Internet unavailable
```

unless the unavailable connection actually prevents the requested
action.

The PRD explicitly states that SMS detection, QR detection, risk
scoring, history, and alerts remain available offline, while model
download, analytics upload, and feedback synchronization do not.
fileciteturn4file9L2170-L2192

------------------------------------------------------------------------

# 25. Loading States

Every asynchronous operation needs a clear loading state.

Examples:

``` text
Analyzing SMS...
Scanning QR...
Checking URL...
Loading threat report...
Syncing...
Updating model...
```

Do not display indefinite spinners.

For long-running operations:

``` text
Loading...
[Progress]
```

Provide cancellation where appropriate.

------------------------------------------------------------------------

# 26. Error State Design

Errors must be human-readable.

## Camera Error

``` text
Camera unavailable

Please check camera permission
and try again.

[ Try Again ]
```

## QR Error

``` text
Couldn't read this QR code.

Make sure the code is clear
and try again.

[ Scan Again ]
```

## Model Error

``` text
Protection model unavailable.

Your existing protection remains active
where supported.

[ Retry ]
```

## Network Error

``` text
You're offline.

Threat detection continues on this device.
Cloud synchronization will resume later.
```

The PRD explicitly requires graceful handling of camera unavailable,
permission denied, model unavailable, internet unavailable, corrupted
QR, invalid SMS, and timeout conditions. fileciteturn4file1L656-L672

------------------------------------------------------------------------

# 27. Admin Dashboard Design

The administrator interface is separate from the normal Android user
interface.

The architecture requires:

``` text
Admin Browser
      ↓
Authenticated Backend API
      ↓
Authorized Threat Data
```

The admin dashboard must never directly access PostgreSQL.
fileciteturn4file3L1084-L1113

------------------------------------------------------------------------

# 28. Admin Visual Style

The dashboard should be:

-   Professional
-   Data-oriented
-   Dense enough for security analysts
-   Easy to scan
-   Consistent
-   Responsive
-   Accessible

Prefer:

``` text
Sidebar
   +
Top Header
   +
KPI Cards
   +
Charts
   +
Threat Table
```

------------------------------------------------------------------------

# 29. Admin Dashboard Layout

``` text
┌──────────────────────────────────────────────────────────┐
│ Logo       Security Dashboard              Admin ▼       │
├───────────────┬──────────────────────────────────────────┤
│               │                                          │
│ Dashboard     │ Overview                                 │
│               │                                          │
│ Threats       │ ┌────────┐ ┌────────┐ ┌────────┐        │
│               │ │ Users  │ │Threats │ │Malicious│       │
│ Analytics     │ │ 1,250  │ │ 3,420  │ │ 742    │        │
│               │ └────────┘ └────────┘ └────────┘        │
│ Users         │                                          │
│               │ ┌────────────────────────────────────┐   │
│ Models        │ │ Threat Trend                       │   │
│               │ │                                    │   │
│ Audit Logs    │ │       ╱╲     ╱╲                   │   │
│               │ │  ╱╲  ╱  ╲___╱  ╲                  │   │
│ Settings      │ └────────────────────────────────────┘   │
│               │                                          │
│               │ Recent Threats                           │
│               │ ┌────────────────────────────────────┐   │
│               │ │ Time │ Type │ Risk │ Status         │   │
│               │ ├──────┼──────┼──────┼────────────────┤   │
│               │ │10:32 │ SMS  │ 91   │ Blocked        │   │
│               │ │10:36 │ QR   │ 84   │ Reported       │   │
│               │ └────────────────────────────────────┘   │
└───────────────┴──────────────────────────────────────────┘
```

------------------------------------------------------------------------

# 30. Admin Navigation

``` text
Dashboard
Threats
Analytics
Users
Models
Audit Logs
Settings
```

Admin navigation must be visually distinct from the consumer Android
navigation.

------------------------------------------------------------------------

# 31. Admin Threat Table

Columns:

``` text
Time
Threat Type
Input
Risk Score
Classification
Status
```

Filters:

``` text
Threat Type:
All / Smishing / Quishing

Risk:
High / Medium / Low

Date:
Today / Last 7 Days / Custom

Classification:
Safe / Suspicious / Malicious
```

The PRD explicitly defines these filtering and sorting requirements.
fileciteturn4file3L1014-L1019

------------------------------------------------------------------------

# 32. Admin Threat Details

When selecting a threat, display:

``` text
Threat ID
Threat Type
Risk Score
Classification
Detected URL
Domain
Detection Model
Detection Time
Reasons
Action
```

The PRD's threat inspector defines these exact information categories
and requires sensitive user information to be masked/restricted
according to administrator permissions.
fileciteturn4file3L1022-L1060

------------------------------------------------------------------------

# 33. Admin Analytics

Required visualizations:

## Threat Distribution

``` text
Smishing  ███████████████
Quishing  ███████████
```

## Classification

``` text
Safe        █████████████
Suspicious  █████
Malicious   ███
```

## Threat Trend

Provide:

``` text
Daily
Weekly
Monthly
```

The PRD requires threat distribution, classification breakdown, and
time-based threat trends. fileciteturn4file3L1063-L1081

------------------------------------------------------------------------

# 34. Responsive Design

## Android

Support:

-   Android 10 minimum
-   Android 12+ recommended
-   ARM64
-   ARMv8
-   x86 emulator

The compatibility requirements are specified in the PRD.
fileciteturn4file2L957-L973

## Web

The admin dashboard must support:

``` text
Desktop
Tablet
Small laptop screens
```

Mobile web support can be responsive but desktop remains the primary
analyst experience.

------------------------------------------------------------------------

# 35. Accessibility

The design must support:

-   Dark mode
-   Large text
-   Screen readers
-   Color-blind-friendly UI
-   Simple language
-   Accessible touch targets
-   Content descriptions
-   Semantic navigation

The PRD explicitly lists dark mode, large text, screen readers,
color-blind-friendly UI, and simple language.
fileciteturn4file2L861-L875

Do not communicate threat severity through color alone.

Example:

``` text
🔴 HIGH RISK
```

not merely:

``` text
[red screen]
```

------------------------------------------------------------------------

# 36. Motion and Animation

Use motion only when it improves comprehension.

Appropriate:

-   Splash progress
-   Scanner animation
-   Threat status transition
-   Dashboard card transitions
-   Navigation transitions

Avoid:

-   Excessive animations
-   Long transitions
-   Distracting effects during security alerts
-   Animations that delay threat information

Motion should never interfere with emergency/security actions.

------------------------------------------------------------------------

# 37. Security Alert Interaction Rules

High-risk alerts must prioritize:

``` text
1. Threat severity
2. Reason
3. Recommended action
4. User decision
```

Do not place a harmless-looking "Continue" action above "Block" for a
high-risk threat.

For example:

``` text
HIGH RISK

Do not open this link.

[ Block ]    [ Report ]

More details
```

The user should not need to navigate through several screens to
understand a critical threat.

------------------------------------------------------------------------

# 38. Threat State Design

Use a consistent state model:

``` text
SAFE
SUSPICIOUS
MALICIOUS
```

And a severity model:

``` text
SAFE
LOW
MEDIUM
HIGH
```

Do not mix classification and severity labels inconsistently.

Recommended:

``` text
Classification: MALICIOUS
Risk: HIGH
Score: 91/100
```

------------------------------------------------------------------------

# 39. Privacy UI

Privacy controls must be visible and understandable.

Example:

``` text
Privacy

SMS Processing
● On-device

Cloud Sync
● Enabled

Raw SMS Upload
○ Off

Feedback Data
● Anonymized

[ Delete My Data ]
```

The UI must accurately represent the actual implementation.

Never claim that data is local if it is being uploaded.

------------------------------------------------------------------------

# 40. Model Update UI

The user may see:

``` text
AI Model

Current Version
v1.2.0

Status
✓ Verified

Last Updated
24 Aug 2026

[ Check for Updates ]
```

During an update:

``` text
Downloading model...
      ↓
Verifying integrity...
      ↓
Activating...
```

Never activate an unverified model.

The PRD requires semantic versioning, incremental downloads, rollback
support, integrity verification, and rejection of unverified updates.
fileciteturn4file4L1265-L1275

------------------------------------------------------------------------

# 41. Empty States

Every list must have a useful empty state.

## No Threats

``` text
✓ You're all clear

No threats detected today.
```

## No History

``` text
No detection history yet.

Your analyzed SMS and QR results
will appear here.
```

## No Alerts

``` text
No alerts

We'll notify you when something
needs your attention.
```

Avoid blank screens.

------------------------------------------------------------------------

# 42. Confirmation Dialogs

Use confirmation dialogs only for destructive or important actions.

Examples:

``` text
Delete threat history?
This cannot be undone.

[ Cancel ] [ Delete ]
```

``` text
Disable SMS protection?
Incoming SMS will no longer
be automatically analyzed.

[ Cancel ] [ Disable ]
```

Do not use confirmation dialogs for every ordinary action.

------------------------------------------------------------------------

# 43. Data Visualization Rules

Charts must:

-   Have labels
-   Have meaningful legends
-   Use accessible contrast
-   Provide textual summaries
-   Support filtering
-   Avoid unnecessary 3D effects
-   Avoid misleading scales

Every important chart should have a text equivalent.

Example:

``` text
Threat Distribution

Smishing: 54%
Quishing: 46%
```

------------------------------------------------------------------------

# 44. Design Boundaries

The UI must not:

-   Expose passwords.
-   Expose authentication tokens.
-   Expose database credentials.
-   Display unnecessary private SMS data to administrators.
-   Make technical AI scores the only explanation.
-   Automatically open suspicious URLs.
-   Hide security warnings.
-   Use misleading security claims.
-   Claim a threat is safe simply because the model is uncertain.
-   Bypass permission flows.
-   Make backend connectivity appear mandatory when local detection is
    available.

------------------------------------------------------------------------

# 45. Design-to-Architecture Mapping

``` text
Design
  ↓
Compose UI
  ↓
ViewModel
  ↓
Use Case
  ↓
Repository
  ↓
Local / Remote Data
```

For AI:

``` text
UI
 ↓
Threat ViewModel
 ↓
AnalyzeThreatUseCase
 ↓
AI Repository / AI Service
 ↓
TinyBERT / XGBoost / Isolation Forest
 ↓
Risk Engine
 ↓
Threat Result
 ↓
UI
```

The UI must not directly invoke model runtimes.

------------------------------------------------------------------------

# 46. Design-to-Data Mapping

## Dashboard

Reads:

``` text
Security Score
Threat Counts
SMS Counts
QR Counts
Blocked Attacks
Recent Alerts
```

## Threat Report

Reads:

``` text
Threat Type
Risk Score
Classification
Reasons
Evidence
Recommendation
Timestamp
```

## History

Reads:

``` text
Time
Threat Type
Sender
QR
Score
Action Taken
```

The PRD defines these screen-level information requirements.
fileciteturn4file1L329-L345

------------------------------------------------------------------------

# 47. Design Acceptance Criteria

The design is considered complete when:

``` text
[ ] Splash screen designed
[ ] Onboarding designed
[ ] Login/Register designed
[ ] Permission flow designed
[ ] Dashboard designed
[ ] QR scanner designed
[ ] SMS analysis designed
[ ] Threat report designed
[ ] Threat history designed
[ ] Alert center designed
[ ] Feedback designed
[ ] Settings designed
[ ] Offline states designed
[ ] Error states designed
[ ] Empty states designed
[ ] Admin login designed
[ ] Admin dashboard designed
[ ] Admin threat table designed
[ ] Admin threat inspector designed
[ ] Admin analytics designed
[ ] Dark mode designed
[ ] Accessibility considered
[ ] Privacy states designed
[ ] Model update UI designed
```

------------------------------------------------------------------------

# 48. Design Handoff Rules for the AI Coding Agent

When implementing UI, the AI coding agent must:

1.  Follow this `Design.md`.
2.  Follow `PRD.md`.
3.  Follow `Architecture.md`.
4.  Follow `Rules.md`.
5.  Follow the current implementation phase in `Phases.md`.
6.  Use Material 3 components where appropriate.
7.  Keep UI state in ViewModels.
8.  Keep business logic outside Composables.
9.  Implement loading, error, empty, success, offline, and permission
    states.
10. Maintain accessibility.
11. Maintain dark mode.
12. Keep threat states visually consistent.
13. Do not invent screens that conflict with the PRD.
14. Do not remove required functionality merely for visual simplicity.
15. Do not add unnecessary animations or dependencies.
16. Keep security warnings prominent.
17. Never expose secrets or sensitive authentication information.
18. Test the UI on different screen sizes.
19. Verify that all destructive actions have appropriate confirmation.
20. Verify that every major asynchronous operation has a visible state.

------------------------------------------------------------------------

# 49. Final Design Contract

The final product should feel like:

``` text
A trusted security assistant
        +
A simple consumer application
        +
A professional security monitoring platform
```

The Android experience should be:

``` text
Simple
Fast
Clear
Private
Protective
Accessible
```

The administrator experience should be:

``` text
Professional
Data-rich
Searchable
Filterable
Auditable
Security-focused
```

The core design philosophy is:

> **Show the user what happened, explain why it matters, and clearly
> tell them what to do next.**

This directly supports the PRD requirement that every prediction be
explained and that alerts provide threat type, severity, reason, and
recommended action. fileciteturn4file1L286-L327
