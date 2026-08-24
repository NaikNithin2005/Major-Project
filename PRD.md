# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System
## Product Requirements Document (PRD)

**Version:** 1.0  
**Document Type:** Comprehensive Product Requirements Document (PRD)  
**Project Status:** Major Project Phase I  
**Authors:** Nithin Naik R, S MD Umar Talha Azeez, Sagar S H, Prashant Kushwaha  
**Guide:** Prof. Brunda S  

---

# Master Table of Contents

- [Part 1: Executive Summary & Project Vision](#part-1-executive-summary--project-vision)
  - 1 Executive Summary
  - 2 Vision
  - 3 Mission
  - 4 Problem Statement
  - 5 Existing Solutions
  - 6 Research Gap
  - 7 Proposed Solution
  - 8 Product Goals
  - 9 Success Metrics
  - 10 Stakeholders
  - 11 Target Users
  - 12 User Personas
  - 13 Product Scope
  - 14 Out of Scope
  - 15 Assumptions
  - 16 Constraints
  - 17 Product Principles
  - 18 Business Value
  - 19 Technical Objectives
  - 20 High-Level Features
  - 21 Glossary
- [Part 2: Functional & Non-Functional Requirements](#part-2-functional--non-functional-requirements)
  - 2.1 Product Functional Overview
  - 2.2 User Roles
  - 2.3 Core Functional Modules
  - 2.4 Functional Requirements (FR-1 to FR-20)
  - 2.5 User Stories
  - 2.6 User Journey
  - 2.7 User Flow
  - 2.8 Detailed Feature Requirements
  - 2.9 Notifications & Alerts
  - 2.10 Permission Requirements
  - 2.11 Error Handling
  - 2.12 Accessibility Requirements
  - 2.13 Performance Requirements
  - 2.14 Security Requirements
  - 2.15 Privacy Requirements
  - 2.16 Scalability Requirements
  - 2.17 Reliability Requirements
  - 2.18 Availability Requirements
  - 2.19 Compatibility Requirements
  - 2.20 Compliance Requirements
  - 2.21 Acceptance Criteria
- [Part 3: System Architecture & Technical Design](#part-3-system-architecture--technical-design)
  - 3.1 System Overview
  - 3.2 Design Philosophy
  - 3.3 High-Level Architecture
  - 3.4 Architectural Layers
  - 3.5 Component Architecture
  - 3.6 Android Architecture
  - 3.7 Backend Architecture
  - 3.8 AI/ML Architecture
  - 3.9 Data Flow Architecture
  - 3.10 Component Responsibilities
  - 3.11 Database Architecture
  - 3.12 Deployment Architecture
  - 3.13 Communication Flow
  - 3.14 Sequence Diagrams
  - 3.15 Design Patterns
  - 3.16 Technology Stack
  - 3.17 Scalability Strategy
  - 3.18 Security Architecture
  - 3.19 Fault Tolerance
  - 3.20 Future Extensibility
- [Part 4: AI/ML Design & Detection Pipelines](#part-4-aiml-design--detection-pipelines)
  - 4.1 AI/ML Vision
  - 4.2 AI Architecture
  - 4.3 Detection Pipeline Overview
  - 4.4 Data Collection Strategy
  - 4.5 Dataset Requirements
  - 4.6 Data Labeling Strategy
  - 4.7 Data Preprocessing Pipeline
  - 4.8 SMS Detection Pipeline (TinyBERT)
  - 4.9 QR Detection Pipeline (XGBoost)
  - 4.10 URL Intelligence Engine
  - 4.11 Feature Engineering
  - 4.12 Smishing Detection Model
  - 4.13 Quishing Detection Model
  - 4.14 Zero-Day Detection Module (Isolation Forest)
  - 4.15 Cross Verification Engine
  - 4.16 Risk Scoring Engine
  - 4.17 Explainable AI (XAI)
  - 4.18 Model Training Pipeline
  - 4.19 Model Evaluation
  - 4.20 Model Optimization
  - 4.21 Model Deployment
  - 4.22 Model Updating
  - 4.23 AI Monitoring
  - 4.24 AI Risks & Mitigations
  - 4.25 Future AI Enhancements
- [Part 5: Android Application Design & Mobile Requirements](#part-5-android-application-design--mobile-requirements)
  - 5.1 Mobile Application Vision
  - 5.2 Android Architecture (MVVM + Clean)
  - 5.3 Mobile Tech Stack
  - 5.4 Application Modules
  - 5.5 App Navigation
  - 5.6 User Authentication
  - 5.7 Splash Screen
  - 5.8 Onboarding
  - 5.9 Permission Manager
  - 5.10 Home Dashboard
  - 5.11 SMS Monitoring Module
  - 5.12 QR Scanner Module
  - 5.13 Threat Analysis Screen
  - 5.14 Threat History
  - 5.15 Notification Center
  - 5.16 Feedback Module
  - 5.17 Settings
  - 5.18 Local Storage
  - 5.19 Background Services
  - 5.20 Offline Mode
  - 5.21 Battery Optimization
  - 5.22 Security Features
  - 5.23 UI/UX Guidelines
  - 5.24 Accessibility
  - 5.25 Android Performance Requirements
  - 5.26 Error Handling
  - 5.27 Future Android Features
- [Part 6: Backend Architecture, APIs, Database & Infrastructure](#part-6-backend-architecture-apis-database--infrastructure)
  - 6.1 Backend Vision
  - 6.2 Backend Goals
  - 6.3 Overall Backend Architecture
  - 6.4 Backend Components
  - 6.5 API Gateway
  - 6.6 Authentication Service
  - 6.7 AI Model Service
  - 6.8 Threat Intelligence Service
  - 6.9 Feedback Service
  - 6.10 Notification Service
  - 6.11 Synchronization Service
  - 6.12 Database Design
  - 6.13 PostgreSQL Schema
  - 6.14 Firebase Architecture
  - 6.15 REST API Specifications
  - 6.16 Authentication Flow
  - 6.17 API Security
  - 6.18 Background Synchronization
  - 6.19 File Storage
  - 6.20 Logging
  - 6.21 Monitoring
  - 6.22 Infrastructure
  - 6.23 CI/CD
  - 6.24 Deployment
  - 6.25 Disaster Recovery
  - 6.26 Future Backend Roadmap
- [Part 7: Authentication & Threat Management Module](#part-7-authentication--threat-management-module)
  - 7.1 Feature Overview
  - 7.2 User Roles & Access Control
  - 7.3 Authentication System Architecture
  - 7.4 User Database Schema
  - 7.5 Threat Database Schema
  - 7.6 Smishing Detection Data Pipeline & Storage
  - 7.7 Quishing Detection Data Pipeline & Storage
  - 7.8 Correlation Between SMS and QR Threats
  - 7.9 Admin Web Dashboard Overview
  - 7.10 Threat Monitoring Page & Filtering Specifications
  - 7.11 Threat Details Page Specifications
  - 7.12 Threat Analytics & Visualization System
  - 7.13 Backend Architecture & Decoupled Access Pattern
  - 7.14 REST API Structure & Endpoints
  - 7.15 System Security & Data Governance Requirements
  - 7.16 Recommended Technology Stack
  - 7.17 End-to-End Operational Lifecycle
  - 7.18 Conceptual Terminology & Database Naming
- [Part 8: Security, Testing, Deployment & DevOps Strategy](#part-8-security-testing-deployment--devops-strategy)
  - 8.1 Security Vision & Objectives
  - 8.2 Security Architecture & Layers
  - 8.3 Application Security
  - 8.4 Android Security
  - 8.5 AI Model Security
  - 8.6 Backend & API Security
  - 8.7 Data Privacy & User Controls
  - 8.8 Threat Modeling & STRIDE Analysis
  - 8.9 Risk Assessment
  - 8.10 Testing Strategy
  - 8.11 Unit & Integration Testing
  - 8.12 System & AI Model Testing
  - 8.13 Performance & Security Testing
  - 8.14 User Acceptance Testing (UAT)
  - 8.15 DevOps & CI/CD Pipelines
  - 8.16 Deployment Strategy
  - 8.17 Monitoring & Observability
  - 8.18 Backup, Disaster Recovery & Maintenance
  - 8.19 Product Roadmap & Milestones
- [Part 9: Implementation Roadmap, UI Specifications & Deliverables](#part-9-implementation-roadmap-ui-specifications--deliverables)
  - 9.1 Project Overview & Methodology
  - 9.2 Sprint Planning (Sprints 1 to 10)
  - 9.3 Team Responsibilities
  - 9.4 Complete Folder Structure
  - 9.5 Module Breakdown (Android, Backend, AI)
  - 9.6 Database Implementation
  - 9.7 UI Wireframes & Design System
  - 9.8 UX Guidelines
  - 9.9 Project Timeline & Risk Register
  - 9.10 Cost Analysis & System Requirements
  - 9.11 Documentation & Deliverables
  - 9.12 Success Criteria & Project Sign-Off Checklist

---

# Part 1: Executive Summary & Project Vision
1 Executive Summary
Overview

The Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System is an intelligent mobile cybersecurity solution designed to protect users from phishing attacks delivered through SMS messages (Smishing) and QR codes (Quishing).

Unlike traditional phishing detection systems that rely heavily on blacklist databases, cloud APIs, or manually updated threat intelligence, the proposed system leverages Artificial Intelligence and Machine Learning to identify malicious behavior in real time.

The solution combines:

Natural Language Processing (NLP)
Computer Vision
URL Intelligence
Machine Learning
Behavioral Analysis
Context Correlation

to provide proactive phishing detection directly on the user's device.

The system continuously monitors incoming SMS messages and scanned QR codes, extracts relevant features, performs intelligent analysis, assigns a dynamic risk score, and recommends preventive actions before the user interacts with malicious content.

The long-term objective is to reduce financial fraud, identity theft, credential theft, and malicious redirection attacks by providing explainable, privacy-preserving, and low-latency phishing detection.

Why this Product Exists

The rapid adoption of:

UPI payments
QR payments
Mobile banking
Online shopping
Digital KYC
Government services
OTP verification

has significantly increased the attack surface for cybercriminals.

Recent phishing campaigns increasingly exploit:

fake banking SMS
fake courier messages
fake electricity bill notifications
fake government notices
malicious QR payment stickers
QR code replacement attacks
AI-generated phishing messages

Traditional antivirus software rarely inspects SMS semantics or QR payload intent, leaving users vulnerable to modern phishing attacks.

Product Vision in One Sentence

Protect every smartphone user from phishing attacks before they become victims.

2 Vision

To become a privacy-first, AI-powered mobile security platform capable of detecting and preventing phishing attacks across every digital communication channel in real time.

The vision extends beyond QR codes and SMS to eventually include:

Email phishing
WhatsApp phishing
Telegram phishing
Fake websites
Browser attacks
Voice phishing (Vishing)
AI-generated phishing
Deepfake scams

The platform should evolve into a unified mobile cyber-defense assistant capable of understanding user context rather than relying solely on static signatures.

3 Mission

Develop an intelligent mobile application that continuously monitors, analyzes, and prevents phishing attacks using advanced Artificial Intelligence techniques while maintaining:

Privacy
Low latency
High accuracy
Offline capability
Explainability
User trust

The application should empower users to make informed security decisions instead of merely displaying generic warnings.

4 Problem Statement
Background

Digital communication has fundamentally changed the way individuals interact with financial institutions, businesses, and government services.

Today, users regularly receive:

Banking SMS
Payment requests
QR codes
UPI requests
Delivery notifications
Promotional campaigns
Authentication messages

Cybercriminals exploit this trust by crafting highly convincing phishing campaigns that mimic legitimate organizations.

Unlike traditional phishing emails, modern attacks increasingly target mobile devices because users are less likely to inspect URLs, certificates, or sender authenticity on smaller screens.

Current Problems

Current phishing detection systems suffer from several limitations:

Static Detection

Most solutions rely on predefined:

blacklists
signature databases
rule engines

These approaches fail against newly generated phishing domains.

Poor Context Understanding

Current systems cannot understand:

urgency
emotional manipulation
contextual deception

Example:

Your SBI account will be suspended within 30 minutes.

Traditional filters may classify this as harmless.

Humans perceive urgency.

AI should too.

Separate Analysis

Existing solutions treat:

SMS

and

QR codes

as unrelated entities.

However, attackers commonly combine both.

Example:

SMS:

Your KYC has expired.

â†“

QR Code

â†“

Fake Banking Website

Without correlation, detection accuracy decreases significantly.

Cloud Dependency

Many security applications depend on:

VirusTotal
Google Safe Browsing
URLHaus
PhishTank APIs

Problems:

Internet required
Privacy concerns
API limits
Detection delay
Zero-Day Attacks

Blacklist systems cannot detect domains created minutes ago.

Modern phishing domains often:

live for only hours
change rapidly
evade existing databases
User Awareness

Most users cannot differentiate:

upi-payments-secure.in

vs

upi-payment-secure.in

The system should educate users rather than only blocking content.

5 Existing Solutions

Current market solutions include:

Product	Strength	Weakness
Google Play Protect	Malware detection	Limited phishing understanding
Microsoft Defender	Enterprise protection	Not SMS focused
Norton Mobile Security	URL scanning	Cloud dependency
McAfee Mobile	Antivirus	Weak contextual analysis
Bitdefender	Reputation-based	Limited explainability
Traditional Spam Filters	Keyword detection	High false negatives

Academic solutions primarily focus on:

Email phishing
URL classification
Website screenshots
QR classification
SMS spam

Very few integrate all components into one unified mobile application.

6 Research Gap

Based on the literature survey and project review materials, the following gaps are identified:

Gap 1

Most systems detect only one phishing medium.

Example:

Only SMS

or

Only QR.

Gap 2

Few systems perform contextual correlation between SMS content and QR destination.

Gap 3

Most solutions rely on external APIs.

Gap 4

Limited support for zero-day phishing attacks.

Gap 5

Few systems provide explainable AI decisions.

Instead of explaining:

Suspicious because:
â€¢ Domain age: 1 day
â€¢ HTTPS missing
â€¢ Brand mismatch

Most simply display:

Dangerous

without justification.

Gap 6

Few lightweight models exist for mobile deployment.

Many academic papers use:

BERT Large
LLMs
Large CNNs

which cannot execute efficiently on smartphones.

7 Proposed Solution

The proposed solution introduces a unified AI-powered mobile application capable of analyzing both SMS messages and QR codes within a single intelligent detection framework.

The system consists of the following major components:

SMS Monitoring Engine
QR Code Scanner
NLP Pipeline
URL Intelligence Engine
Feature Extraction Module
Smishing Detection Model
Quishing Detection Model
Cross-Verification Engine
Risk Scoring Engine
Explainable AI Module
Decision Engine
Alert & Prevention Module
Feedback Collection Module
Model Retraining Pipeline

Unlike conventional systems, this architecture combines content analysis, contextual understanding, and behavioral intelligence to provide comprehensive phishing protection.

8 Product Goals
Primary Goals
Detect SMS phishing attacks in real time.
Detect malicious QR codes before interaction.
Correlate SMS content with QR destinations.
Detect zero-day phishing attacks.
Provide actionable prevention recommendations.
Protect user privacy through on-device analysis where feasible.
Deliver low-latency detection suitable for mobile devices.
Secondary Goals
Educate users about phishing risks.
Reduce false positives.
Support continuous model improvement through feedback.
Build a scalable foundation for future phishing channels (email, messaging apps, browsers).
9 Success Metrics

The product will be considered successful when it achieves measurable technical and user outcomes.

Metric	Target
SMS Detection Accuracy	â‰¥95%
QR Detection Accuracy	â‰¥94%
False Positive Rate	â‰¤3%
Detection Latency	<1 second per scan/message
Model Size	Suitable for mobile deployment
User Alert Precision	High-confidence, explainable alerts
User Feedback Integration	Support continuous retraining pipeline
10 Stakeholders
Internal Stakeholders
Project Team
Project Guide
Department Review Committee
AI/ML Development Team
Android Development Team
Backend/API Development Team
QA & Testing Team
External Stakeholders
End Users
Banking Users
Educational Institutions
Organizations using QR-based services
Cybersecurity Researchers
11 Target Users

The application is intended for users who frequently interact with digital communication and payment systems.

Primary target users include:

Mobile banking users
UPI payment users
Students
Working professionals
Elderly users
Online shoppers
Small business owners
Organizations relying on QR codes
Government service users
12 User Personas
Persona 1: Banking User
Age: 32
Uses UPI daily
Receives frequent banking SMS
Wants quick and trustworthy alerts before opening suspicious links
Persona 2: Student
Frequently scans QR codes on campus
Uses payment apps and messaging services
Needs lightweight protection without affecting device performance
Persona 3: Elderly User
Limited cybersecurity awareness
High risk of falling for social engineering
Benefits from simple explanations and clear warnings
Persona 4: Small Business Owner
Uses QR codes for payments
Receives business-related SMS
Requires reliable detection to avoid financial fraud
13 Product Scope
Included
SMS monitoring
QR code scanning
NLP preprocessing
URL analysis
Smishing detection
Quishing detection
Context correlation
Risk scoring
User alerts
Prevention recommendations
Feedback collection
Model retraining support
14 Out of Scope (Phase 1)

The following features are intentionally excluded from the initial release:

Email phishing detection
Browser extension support
WhatsApp message scanning
Telegram message analysis
Voice phishing (Vishing)
Deepfake detection
Cloud-based SOC integration
Enterprise management console
Cross-platform desktop applications

These may be considered in future phases.

15 Assumptions
Users grant required Android permissions (SMS, Camera, Notifications).
A representative dataset is available for model training.
Devices meet minimum hardware requirements for on-device inference.
Internet connectivity may be unavailable; core detection should still function where possible.
16 Constraints
Limited mobile CPU/GPU resources.
Battery consumption must remain low.
Model size should fit within mobile storage constraints.
Detection latency should remain under one second.
Privacy regulations require careful handling of user data.
17 Product Principles
Privacy First: Analyze data locally whenever feasible.
Explainable: Every warning should include understandable reasons.
Fast: Real-time detection with minimal delay.
Accurate: Balance sensitivity with low false positives.
Lightweight: Optimized for Android devices.
User-Centric: Simple, actionable guidance instead of technical jargon.
18 Business Value

The system aims to:

Reduce financial fraud caused by phishing.
Improve user confidence in digital payments.
Increase cybersecurity awareness.
Provide a scalable platform for future AI-powered mobile security features.
Demonstrate an innovative academic project with potential for real-world deployment.
19 Technical Objectives
Build an Android application for SMS and QR monitoring.
Implement AI/ML models for smishing and quishing detection.
Design a modular architecture supporting future enhancements.
Enable explainable risk scoring and decision-making.
Support feedback-driven model improvement.
20 High-Level Features
Real-time SMS monitoring
QR code scanner
URL feature extraction
NLP-based message analysis
AI/ML threat classification
Cross-verification engine
Risk scoring dashboard
User alerts and recommendations
Threat history
Feedback submission
Offline-capable core detection
21 Glossary
Term	Definition
Smishing	Phishing through SMS messages
Quishing	Phishing through malicious QR codes
NLP	Natural Language Processing
URL Intelligence	Analysis of URL structure and metadata for risk assessment
TinyBERT	Lightweight transformer model for text classification
XGBoost	Gradient boosting algorithm used for URL classification
Cross-Verification	Correlating SMS context with QR or URL information
Risk Score	Numeric measure indicating the likelihood of a phishing attempt
Explainable AI (XAI)	AI techniques that provide understandable reasons for predictions
Zero-Day Phishing	Newly created phishing attacks not yet present in blacklists




---

# Part 2: Functional & Non-Functional Requirements

**Version:** 1.0

Table of Contents
2.1 Product Functional Overview

2.2 User Roles

2.3 Core Functional Modules

2.4 Functional Requirements

2.5 User Stories

2.6 User Journey

2.7 User Flow

2.8 Detailed Feature Requirements

2.9 Notifications & Alerts

2.10 Permission Requirements

2.11 Error Handling

2.12 Accessibility Requirements

2.13 Performance Requirements

2.14 Security Requirements

2.15 Privacy Requirements

2.16 Scalability Requirements

2.17 Reliability Requirements

2.18 Availability Requirements

2.19 Compatibility Requirements

2.20 Compliance Requirements

2.21 Acceptance Criteria
2.1 Product Functional Overview

The application continuously protects Android users from phishing attacks delivered through:

SMS Messages (Smishing)
QR Codes (Quishing)

The application operates in both:

Passive Mode (background SMS monitoring)
Active Mode (user scans QR code)

The system automatically analyzes every incoming SMS and every scanned QR code before the user interacts with the content.

2.2 User Roles
Primary User

The individual using the Android application.

Capabilities:

Read threat reports
Scan QR codes
View SMS analysis
Submit feedback
View history
Configure settings
AI Engine

Responsible for:

NLP analysis
QR analysis
URL classification
Risk scoring
Decision making
Administrator (Future Scope)

Responsible for:

Dataset updates
Model deployment
Analytics
Monitoring

(Not included in Phase 1.)

2.3 Core Functional Modules

The application consists of the following modules:

1 SMS Monitoring Module

2 QR Scanner Module

3 URL Analysis Module

4 NLP Engine

5 Feature Extraction Module

6 AI Detection Engine

7 Cross Verification Engine

8 Risk Scoring Engine

9 Explainable AI Module

10 Alert Module

11 Threat History Module

12 Feedback Module

13 Settings Module
2.4 Functional Requirements
FR-1 User Registration

Priority

Medium

Description

The application shall allow users to create an account.

Authentication methods

Google Sign-In
Email & Password
Guest Mode (optional)

Stored Information

Name
Email
Device ID
Registration Date
FR-2 Login

The application shall authenticate users securely.

Requirements

Secure session
Persistent login
Logout support
FR-3 Background SMS Monitoring

Priority

Critical

Description

The application shall monitor incoming SMS messages in real time.

Trigger

Incoming SMS Broadcast

Workflow

SMS Received

â†“

Permission Check

â†“

SMS Parser

â†“

AI Analysis

â†“

Risk Score

â†“

Alert
FR-4 SMS Parsing

The system shall extract:

Sender

Timestamp

Body

URLs

Phone Numbers

OTP

Bank Name

Special Characters

Language

FR-5 SMS NLP Analysis

The system shall preprocess SMS using:

Lowercase Conversion

Stopword Removal

Tokenization

Lemmatization

Normalization

Vectorization

The processed text shall be sent to TinyBERT.

FR-6 Smishing Detection

The AI engine shall classify messages into:

Safe

Spam

Smishing

Scam

High Risk

Output

Probability Score

Confidence Score

Threat Category

FR-7 QR Scanner

The application shall provide:

Camera Scanner

Image Upload

Gallery Scanner

Continuous Scanner

FR-8 QR Decoding

The application shall decode:

Plain Text

Website URLs

Payment Links

WiFi QR

vCard

Email

Phone

UPI

Location

FR-9 URL Feature Extraction

The system shall calculate:

URL Length

Domain Length

HTTPS

SSL

Port

Entropy

Subdomains

IP Address Usage

Special Characters

Unicode

Homograph Attack

Shortened URL

Redirections

WHOIS Age (Future)

DNS Features

FR-10 QR Threat Detection

The system shall classify QR payloads.

Categories

Safe

Suspicious

Malicious

Unknown

FR-11 Brand Impersonation Detection

The application shall detect fake domains.

Example

paytm.com

vs

paytm-secure-login.com

The AI shall compare:

Domain

Brand Name

Visual Similarity

Context

FR-12 Cross Verification

The system shall correlate:

SMS Content

â†“

QR Code

â†“

URL

â†“

Sender

â†“

Brand

Example

SMS

"Update SBI KYC"

â†“

QR

â†“

paypal-login.xyz

â†“

Risk

High

FR-13 Risk Scoring

Every threat shall receive a score.

Range

0 - 100

Classification

Score	Level
0â€“25	Safe
26â€“50	Low Risk
51â€“75	Medium Risk
76â€“100	High Risk
FR-14 Explainable AI

The application shall explain every prediction.

Example

Risk: 87%

Reasons

âœ“ HTTPS Missing

âœ“ Suspicious Domain

âœ“ Banking Keywords

âœ“ Newly Created Domain

âœ“ Sender Unknown

âœ“ Urgency Detected
FR-15 Alert System

Alerts shall include

Threat Type

Severity

Reason

Recommended Action

Buttons

Block

Ignore

Report

Copy Report

FR-16 Threat History

Maintain history.

Fields

Time

Threat Type

Sender

QR

Score

Action Taken

FR-17 Feedback

Users may report:

False Positive

False Negative

Correct Detection

Used for retraining.

FR-18 Settings

Users can configure

SMS Monitoring

QR Scanner

Notifications

Dark Mode

Model Updates

Privacy

FR-19 Offline Detection

The application shall continue to detect threats even without internet.

FR-20 Cloud Synchronization (Future)

Synchronize

Threat Reports

Feedback

Models

Across devices.

2.5 User Stories
Story 1

As a user,

I want incoming SMS messages to be automatically analyzed,

so that I do not accidentally open phishing links.

Story 2

As a user,

I want every QR code scanned before opening,

so that malicious payment requests are detected.

Story 3

As an elderly user,

I want simple explanations,

so that I understand why a message is dangerous.

Story 4

As a banking customer,

I want suspicious banking SMS detected instantly,

so that my account remains secure.

Story 5

As a student,

I want lightweight background protection,

so that battery consumption remains low.

2.6 User Journey
Install App

â†“

Login

â†“

Grant Permissions

â†“

Background Protection Enabled

â†“

Receive SMS

â†“

AI Analysis

â†“

Alert

â†“

User Action

â†“

Feedback

â†“

History

QR Flow

Open Scanner

â†“

Scan QR

â†“

Decode

â†“

AI Analysis

â†“

Risk Score

â†“

Warning

â†“

User Decision
2.7 User Flow
User

â†“

Dashboard

â†“

Choose

â†“

SMS History

OR

QR Scanner

â†“

AI Engine

â†“

Threat Report

â†“

Recommendation

â†“

History
2.8 Detailed Feature Requirements
Dashboard

Shows

Threats Today

Safe Messages

Blocked Links

Recent Alerts

Security Score

QR Scanner

Features

Flash

Zoom

Gallery Import

Continuous Scan

Auto Focus

SMS Screen

Shows

Sender

Message

Prediction

Confidence

Reasons

Threat Report

Displays

Risk Meter

Threat Type

Recommendation

Time

Evidence

History Screen

Search

Filter

Export

Delete

Sort

Settings

Permissions

Theme

Language

Notification

Privacy

Model Version

2.9 Notifications & Alerts

The system shall generate:

Heads-up Notification

Critical Alert

Silent Alert

Background Warning

Example

âš  High Risk SMS Detected

Fake SBI Login

Confidence 96%

Do not open the link.
2.10 Permission Requirements

Required Android permissions

SMS

Receive SMS

Read SMS

Camera

Internet

Foreground Service

Notifications

Storage (Gallery Import)

2.11 Error Handling

The application shall gracefully handle:

Camera unavailable

Permission denied

Model unavailable

Internet unavailable

Corrupted QR

Invalid SMS

Timeout

2.12 Accessibility Requirements

Support

Dark Mode

Large Text

Screen Readers

Color Blind Friendly UI

Simple Language

Voice Alerts (Future)

2.13 Performance Requirements
Requirement	Target
SMS Analysis	<500 ms
QR Analysis	<800 ms
App Launch	<2 sec
Model Loading	<3 sec
Memory Usage	<250 MB
Battery Consumption	Minimal
2.14 Security Requirements

The application shall

Encrypt local data

Use HTTPS

Prevent model tampering

Validate APK integrity

Protect Firebase

Secure local database

2.15 Privacy Requirements

User data shall

Remain local whenever possible

Not upload SMS without consent

Anonymize feedback

Allow deletion

Provide transparency

2.16 Scalability Requirements

Future support

Email

WhatsApp

Telegram

Browser Extension

Enterprise Dashboard

Threat Intelligence APIs

2.17 Reliability Requirements

Target

99% uptime

Graceful failure

Automatic recovery

Crash logging

2.18 Availability Requirements

The application shall operate

Offline

Low Network

5G

WiFi

Dual SIM

2.19 Compatibility Requirements

Minimum Android

Android 10

Recommended

Android 12+

Supported Architectures

ARM64

ARMv8

x86 Emulator

2.20 Compliance Requirements

The application should comply with

OWASP Mobile Top 10

Android Security Best Practices

Google Play Policies

GDPR principles where applicable

Indian DPDP Act (future deployment)

2.21 Acceptance Criteria

The product will be accepted for Phase 1 when the following criteria are met:

ID	Acceptance Criteria
AC-01	Incoming SMS messages are automatically monitored and analyzed in real time.
AC-02	QR codes can be scanned using the device camera or imported from the gallery.
AC-03	SMS content is preprocessed using NLP and classified by the AI model.
AC-04	QR payloads and extracted URLs are analyzed for phishing indicators.
AC-05	Context correlation between SMS content and QR/URL is performed when applicable.
AC-06	Every analyzed item receives a risk score between 0 and 100 with a threat category.
AC-07	Users receive clear alerts containing threat level, explanation, and recommended actions.
AC-08	A threat history is maintained with filtering and search capabilities.
AC-09	User feedback can be submitted to improve future model performance.
AC-10	Core detection functionality remains operational even without an internet connection (where supported by on-device models).




---

# Part 3: System Architecture & Technical Design

**Version:** 1.0

Table of Contents
3.1 System Overview

3.2 Design Philosophy

3.3 High-Level Architecture

3.4 Architectural Layers

3.5 Component Architecture

3.6 Android Architecture

3.7 Backend Architecture

3.8 AI/ML Architecture

3.9 Data Flow Architecture

3.10 Component Responsibilities

3.11 Database Architecture

3.12 Deployment Architecture

3.13 Communication Flow

3.14 Sequence Diagrams

3.15 Design Patterns

3.16 Technology Stack

3.17 Scalability Strategy

3.18 Security Architecture

3.19 Fault Tolerance

3.20 Future Extensibility
3.1 System Overview

The proposed system is a privacy-first intelligent mobile cybersecurity platform that detects and prevents phishing attacks originating from SMS messages and QR codes.

Unlike traditional security solutions that depend on cloud services and blacklist APIs, this platform performs most threat analysis locally using AI/ML models while optionally synchronizing metadata for model updates and analytics.

The architecture follows a modular layered design, allowing each subsystem (Android, AI, backend, and storage) to evolve independently without affecting the rest of the application.

Primary Objectives

The architecture is designed to achieve the following goals:

Real-time SMS analysis
Real-time QR analysis
Zero-day phishing detection
Context-aware threat correlation
Explainable AI
Low battery consumption
Offline detection capability
Scalable model deployment
Modular architecture
Future multi-platform support
3.2 Design Philosophy

The architecture follows six core principles.

1. Modular

Each subsystem should work independently.

Example:

SMS Module

â†“

NLP Module

â†“

Risk Engine

â†“

Alert Engine

Changing one module should not require modifying others.

2. Offline First

Core phishing detection must function without internet access.

Cloud connectivity is only used for:

Model updates
Feedback upload
Analytics
Future synchronization
3. AI Native

Artificial Intelligence is the primary decision-making engine.

Traditional rule-based logic is used only:

as fallback
for feature engineering
for validation
4. Explainable

Every prediction must include an explanation.

Example

High Risk

Reasons

â€¢ HTTPS Missing

â€¢ Suspicious Domain

â€¢ Banking Keyword

â€¢ Newly Registered Domain

Confidence

96%
5. Lightweight

The system should execute efficiently on mid-range Android devices.

Target:

RAM <250 MB
CPU usage <15%
Low battery drain
6. Extensible

Future modules should integrate without redesigning the architecture.

Possible future modules:

Email Detection
WhatsApp Analysis
Browser Extension
Voice Phishing Detection
Enterprise Dashboard
3.3 High-Level Architecture
                    USER

                      â”‚

        â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

        â”‚                           â”‚

   Incoming SMS               QR Scanner

        â”‚                           â”‚

        â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

                      â”‚

            Data Acquisition Layer

                      â”‚

          Preprocessing Layer

      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

      â”‚                             â”‚

 NLP Pipeline                 QR Decoder

      â”‚                             â”‚

      â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

                     â”‚

          Feature Engineering Layer

                     â”‚

      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

      â”‚                             â”‚

 TinyBERT                  URL Intelligence

      â”‚                             â”‚

      â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

                     â”‚

          Cross Verification Engine

                     â”‚

           Risk Scoring Engine

                     â”‚

          Explainable AI Engine

                     â”‚

            Decision Engine

                     â”‚

      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

      â”‚                             â”‚

 Alert System                Threat History

                     â”‚

                User Feedback

                     â”‚

              Model Retraining
3.4 Architectural Layers

The application consists of eight logical layers.

Layer 1 â€” Presentation Layer

Responsibilities

Dashboard
QR Scanner
SMS History
Threat Reports
Settings
Notifications

Technology

Kotlin
Jetpack Compose
Material 3
Layer 2 â€” Input Layer

Responsible for collecting data.

Sources

Incoming SMS

Camera

Gallery QR

Clipboard (Future)

Browser (Future)

Layer 3 â€” Preprocessing Layer

SMS

Cleaning
Tokenization
Normalization

QR

Decode
Validate
Extract URL
Layer 4 â€” Feature Engineering Layer

Generates AI features.

Examples

SMS

urgency
keywords
sender

QR

entropy
domain
SSL
redirects
Layer 5 â€” AI Layer

Contains

TinyBERT

XGBoost

Isolation Forest

Rule Engine

Future

ONNX Runtime

Layer 6 â€” Intelligence Layer

Contains

Cross Verification

Risk Scoring

Threat Correlation

Decision Making

Layer 7 â€” Storage Layer

Local

Room Database

Shared Preferences

Encrypted Storage

Cloud

Firebase

PostgreSQL

Layer 8 â€” Analytics Layer

Stores

Threat statistics

User feedback

Model metrics

Future dashboard

3.5 Component Architecture
Android App

â”‚

â”œâ”€â”€ UI Module

â”œâ”€â”€ SMS Module

â”œâ”€â”€ QR Module

â”œâ”€â”€ NLP Module

â”œâ”€â”€ Feature Module

â”œâ”€â”€ AI Module

â”œâ”€â”€ Risk Engine

â”œâ”€â”€ Alert Engine

â”œâ”€â”€ History Module

â”œâ”€â”€ Feedback Module

â”œâ”€â”€ Sync Module

â””â”€â”€ Settings Module

Each module communicates only through well-defined interfaces.

3.6 Android Architecture

The Android application follows MVVM + Clean Architecture.

Presentation

â†“

ViewModel

â†“

Use Cases

â†“

Repository

â†“

Local Data Source

â†“

Remote Data Source

â†“

Database

â†“

AI Engine
Presentation Layer

Screens

Splash

Login

Dashboard

Scanner

History

Threat Report

Settings

Feedback

ViewModel

Responsible for

UI State

Business Logic

Model Communication

Navigation

Repository

Acts as a bridge between:

Room

Firebase

AI Engine

Data Sources

Local

Room

Preferences

Encrypted Storage

Remote

Firebase

REST API

3.7 Backend Architecture

Although the application primarily performs local inference, the backend provides optional services for synchronization, feedback processing, analytics, and model management.

Android App

â†“

HTTPS API

â†“

FastAPI

â†“

Authentication

â†“

Business Services

â†“

PostgreSQL

â†“

Object Storage

â†“

Analytics

â†“

Admin Dashboard
Backend Modules
Authentication
Firebase Authentication
JWT verification
Session management
Feedback API

Stores

False positives

False negatives

User reports

Analytics API

Stores

Threat counts

Detection metrics

Model versions

Model Update API

Downloads

New TinyBERT model

Updated XGBoost model

Configuration files

3.8 AI/ML Architecture

The AI layer is divided into specialized engines.

Incoming Data

â†“

Feature Engineering

â†“

Smishing Model

â†“

Quishing Model

â†“

Context Engine

â†“

Risk Engine

â†“

Explainable AI

â†“

Decision Engine
Smishing Pipeline
SMS

â†“

Cleaning

â†“

Tokenizer

â†“

Embeddings

â†“

TinyBERT

â†“

Probability

â†“

Risk
Quishing Pipeline
QR

â†“

Decode

â†“

Extract URL

â†“

Feature Engineering

â†“

XGBoost

â†“

Probability

â†“

Risk
Cross Verification
SMS

â†“

Bank Name

â†“

URL

â†“

Domain

â†“

Brand Match

â†“

Risk

Example:

SMS

SBI

â†“

URL

paypal-secure.xyz

â†“

High Risk
3.9 Data Flow Architecture
SMS Flow
Incoming SMS

â†“

Broadcast Receiver

â†“

Permission Check

â†“

Parser

â†“

NLP

â†“

TinyBERT

â†“

Risk Score

â†“

Alert

â†“

History

â†“

Feedback
QR Flow
Camera

â†“

ZXing

â†“

QR Decode

â†“

Extract URL

â†“

Feature Engineering

â†“

XGBoost

â†“

Decision

â†“

Alert
3.10 Component Responsibilities
Component	Responsibility
SMS Receiver	Listen for incoming SMS
QR Scanner	Read QR codes
NLP Engine	Analyze text
URL Engine	Analyze URLs
Feature Generator	Produce ML features
TinyBERT	SMS classification
XGBoost	URL classification
Context Engine	Correlate SMS & QR
Risk Engine	Compute final score
Alert Engine	Notify users
History Module	Store detections
Feedback Module	Collect corrections
3.11 Database Architecture
Local Database (Room)
User

ThreatHistory

SMSAnalysis

QRAnalysis

Feedback

Settings

ModelVersion
ThreatHistory
Field	Type
id	UUID
timestamp	Long
source	String
sender	String
riskScore	Int
category	String
actionTaken	String
Cloud Database (PostgreSQL)

Stores

Aggregated analytics
Feedback
Model metadata
Version history
Anonymous statistics
3.12 Deployment Architecture
Android Device

â†“

Local AI Models

â†“

Optional HTTPS Sync

â†“

Cloud Backend

â†“

Admin Dashboard

The application must remain functional if the backend is unavailable.

3.13 Communication Flow
User

â†“

Android UI

â†“

ViewModel

â†“

Repository

â†“

AI Engine

â†“

Risk Engine

â†“

Alert

â†“

History

â†“

Feedback

Backend communication occurs asynchronously and never blocks local threat detection.

3.14 Sequence Diagrams
SMS Detection
User

â†“

Receive SMS

â†“

Broadcast Receiver

â†“

SMS Parser

â†“

NLP Engine

â†“

TinyBERT

â†“

Risk Engine

â†“

Decision Engine

â†“

Notification

â†“

History
QR Detection
User

â†“

Open Scanner

â†“

Scan QR

â†“

Decode

â†“

URL Analysis

â†“

XGBoost

â†“

Risk Engine

â†“

Alert

â†“

History
3.15 Design Patterns
Pattern	Usage
MVVM	Android presentation layer
Repository	Data access abstraction
Dependency Injection (Hilt)	Module management
Factory	AI model creation
Singleton	Database, model manager
Observer	LiveData/StateFlow updates
Strategy	Risk scoring algorithms
Builder	Threat report generation
3.16 Technology Stack
Android
Kotlin
Jetpack Compose
CameraX
ZXing
Room
Hilt
WorkManager
Coroutines
StateFlow
AI/ML
TinyBERT
XGBoost
Isolation Forest
ONNX Runtime
TensorFlow Lite (optional)
Backend
FastAPI
PostgreSQL
Firebase Authentication
Docker
Nginx
Development
Android Studio
Python
GitHub
GitHub Actions
Jupyter Notebook
3.17 Scalability Strategy

The architecture is designed to support future additions without major redesign.

Planned extensions include:

Email phishing detection
Browser protection
WhatsApp analysis
Telegram analysis
Enterprise policy management
Threat intelligence feeds
Multi-language NLP models

Each feature should integrate as an independent module communicating through shared interfaces.

3.18 Security Architecture

The system follows a defense-in-depth approach.

Device Security
Encrypted local database
Secure Android Keystore
Root detection (future)
Network Security
HTTPS/TLS
Certificate pinning (future)
Signed API requests
Model Security
Digitally signed AI models
Version verification
Integrity checks before loading
Data Security
Minimal data collection
Encrypted feedback
User-controlled data deletion
3.19 Fault Tolerance

The application must continue operating under common failure scenarios.

Failure	Expected Behavior
No Internet	Continue local detection
Backend unavailable	Queue synchronization tasks
Model update failure	Retain previous stable model
Camera unavailable	Allow gallery QR import
SMS permission denied	Disable SMS module and notify user
Corrupted QR	Display validation error without crashing

Background tasks should automatically retry synchronization when connectivity returns.

3.20 Future Extensibility

The architecture intentionally separates acquisition, analysis, and decision layers so additional phishing sources can reuse the existing AI pipeline.

Potential future modules:

Email phishing analysis
Browser extension
Voice phishing (Vishing)
Image-based scam detection
Deepfake content analysis
Enterprise security dashboard
Federated learning for privacy-preserving model improvements
Cross-platform clients (iOS/Web)






---

# Part 4: AI/ML Design & Detection Pipelines

**Version:** 1.0

Table of Contents
4.1 AI/ML Vision

4.2 AI Architecture

4.3 Detection Pipeline Overview

4.4 Data Collection Strategy

4.5 Dataset Requirements

4.6 Data Labeling Strategy

4.7 Data Preprocessing Pipeline

4.8 SMS Detection Pipeline

4.9 QR Detection Pipeline

4.10 URL Intelligence Engine

4.11 Feature Engineering

4.12 Smishing Detection Model

4.13 Quishing Detection Model

4.14 Zero-Day Detection Module

4.15 Cross Verification Engine

4.16 Risk Scoring Engine

4.17 Explainable AI

4.18 Model Training Pipeline

4.19 Model Evaluation

4.20 Model Optimization

4.21 Model Deployment

4.22 Model Updating

4.23 AI Monitoring

4.24 AI Risks

4.25 Future AI Enhancements
4.1 AI/ML Vision

The Artificial Intelligence subsystem serves as the decision-making core of the application.

Instead of relying solely on predefined phishing signatures or blacklists, the system learns behavioral patterns from SMS content, URLs, and QR code payloads to detect known and previously unseen (zero-day) phishing attacks.

The AI pipeline is designed to be:

Privacy-preserving
Lightweight
Explainable
Real-time
Modular
Continuously improvable
AI Objectives

The AI subsystem shall:

Detect smishing attacks
Detect malicious QR codes
Analyze suspicious URLs
Detect zero-day attacks
Correlate SMS and QR information
Explain every prediction
Continuously improve using feedback
4.2 AI Architecture
Incoming SMS / QR

â†“

Data Cleaning

â†“

Preprocessing

â†“

Feature Engineering

â†“

AI Models

â†“

Threat Correlation

â†“

Risk Engine

â†“

Explainable AI

â†“

Decision Engine

â†“

Alert

â†“

Feedback

â†“

Model Retraining
4.3 Detection Pipeline Overview

The system contains four major AI pipelines.

Pipeline 1

SMS Detection

â†“

TinyBERT

â†“

Risk

-----------------------

Pipeline 2

QR Detection

â†“

URL Features

â†“

XGBoost

â†“

Risk

-----------------------

Pipeline 3

Zero-Day Detection

â†“

Isolation Forest

â†“

Anomaly Score

-----------------------

Pipeline 4

Cross Verification

â†“

Context Engine

â†“

Final Decision
4.4 Data Collection Strategy

The quality of the AI models depends on a diverse, representative dataset.

SMS Sources
Public SMS spam datasets
Banking SMS samples
UPI notification messages
Telecom alerts
Government SMS
User-contributed messages (opt-in)
QR Sources
Benign QR codes
UPI payment QR codes
Business QR codes
Government QR codes
Event QR codes
Public malicious QR datasets
URL Sources
Legitimate websites
Banking domains
E-commerce URLs
Phishing URLs
Shortened URLs
Malicious redirection chains
4.5 Dataset Requirements
SMS Dataset

Required fields:

Field	Description
id	Unique identifier
sender	Sender ID
message	SMS body
language	Language
url	Extracted URL
category	Safe/Spam/Smishing
label	Binary/Multiclass

Target size:

50,000â€“100,000 messages
URL Dataset

Fields:

URL
Domain
HTTPS
TLD
Entropy
Label

Target:

100,000+ URLs

QR Dataset

Fields:

QR Image
Payload
URL
Type
Label

Target:

20,000+ QR codes

4.6 Data Labeling Strategy

Labels:

SAFE

â†“

SPAM

â†“

SMISHING

â†“

QUISHING

â†“

HIGH RISK

â†“

UNKNOWN

Each sample will undergo:

Manual validation
Duplicate removal
Label verification
Quality checks
4.7 Data Preprocessing Pipeline
SMS Pipeline
Raw SMS

â†“

Unicode Cleaning

â†“

Lowercase

â†“

Emoji Handling

â†“

URL Extraction

â†“

Phone Extraction

â†“

Tokenization

â†“

Stopword Removal

â†“

Lemmatization

â†“

Normalization

â†“

Embedding

â†“

TinyBERT
QR Pipeline
QR Image

â†“

Decode

â†“

Extract Payload

â†“

Extract URL

â†“

Validate

â†“

Feature Engineering

â†“

Classifier
4.8 SMS Detection Pipeline

The SMS detection engine analyzes textual content using transformer-based NLP.

Incoming SMS

â†“

Language Detection

â†“

Cleaning

â†“

Tokenizer

â†“

Embedding

â†“

TinyBERT

â†“

Softmax

â†“

Probability

â†“

Risk Score
Extracted Features

Examples:

Banking keywords
Urgency words
Threat phrases
Sender reputation
URL presence
Phone number patterns
OTP context
Brand names
Suspicious requests
Social engineering indicators
4.9 QR Detection Pipeline
QR Image

â†“

ZXing Decode

â†“

Extract Payload

â†“

Identify URL

â†“

Feature Engineering

â†“

XGBoost

â†“

Risk Probability

Supported QR types:

UPI
Website
WiFi
Contact
Email
Phone
Payment
Location
4.10 URL Intelligence Engine

The URL Intelligence Engine computes structural and lexical features.

Lexical Features
URL length
Domain length
Path length
Query length
Number of dots
Number of slashes
Number of digits
Number of hyphens
Number of underscores
Security Features
HTTPS usage
SSL validity (online mode)
Port number
Redirect count
Shortened URL detection
Domain Features
TLD
IP address usage
Homograph detection
Brand similarity
Unicode characters
Entropy score
4.11 Feature Engineering

The system combines heterogeneous feature types.

Text Features
TF-IDF vectors
Context embeddings
Keyword frequency
Named entities
Urgency score
Sentiment indicators
URL Features
Lexical metrics
Structural metrics
Statistical metrics
QR Features
Payload type
Embedded URL
Encoding format
QR version
Error correction level
Context Features
Sender vs domain consistency
Banking brand match
Payment context
Time correlation
4.12 Smishing Detection Model
Primary Model

TinyBERT

Reason:

Lightweight
Mobile-friendly
High contextual understanding
Fast inference
Input

Processed SMS tokens

Output
Safe

Spam

Smishing

High Risk
Probability Example
Safe

4%

Spam

6%

Smishing

89%

Unknown

1%
Backup Model

Logistic Regression (offline fallback)

4.13 Quishing Detection Model

Primary model:

XGBoost

Input:

Feature vector

Output:

Risk probability

Why XGBoost?
Excellent tabular performance
Low inference latency
Explainable feature importance
Robust against noisy features
Example Features
Entropy

3.8

HTTPS

No

Domain Age

Unknown

Redirects

4

Special Characters

8

Homograph

True

â†“

High Risk

4.14 Zero-Day Detection Module

Known phishing models struggle with previously unseen attacks.

To address this, an anomaly detection layer is introduced.

Model

Isolation Forest

Purpose:

Detect abnormal feature combinations.

Workflow

Feature Vector

â†“

Isolation Forest

â†“

Anomaly Score

â†“

Risk Adjustment

Example

Known URL

â†“

Low anomaly

â†“

Normal

Unknown URL

â†“

Very high anomaly

â†“

Risk increases

4.15 Cross Verification Engine

This module correlates multiple information sources.

Example

SMS

â†“

Bank

â†“

"SBI"

â†“

URL

â†“

paypal-security.xyz

â†“

Mismatch

â†“

High Risk

Verification checks

Sender

â†“

Brand

â†“

URL

â†“

QR

â†“

Context

â†“

Final Score

4.16 Risk Scoring Engine

The final score combines outputs from all AI modules.

Inputs

TinyBERT

XGBoost

Isolation Forest

Rule Engine

Context Engine

Formula (Conceptual)
Final Risk =
0.35 Ã— SMS Model

+

0.30 Ã— URL Model

+

0.15 Ã— QR Features

+

0.10 Ã— Context Match

+

0.10 Ã— Anomaly Score
Final Categories
Score	Category
0â€“25	Safe
26â€“50	Low
51â€“75	Medium
76â€“100	High
4.17 Explainable AI (XAI)

Users should understand why content is classified as risky.

Example output:

Risk Score

91%

Reasons

âœ“ Banking urgency

âœ“ Unknown sender

âœ“ HTTPS missing

âœ“ Newly observed domain

âœ“ Homograph detected

âœ“ Suspicious QR payload

The system should also expose feature importance for debugging and model evaluation.

4.18 Model Training Pipeline
Collect Dataset

â†“

Clean

â†“

Label

â†“

Split

â†“

Feature Engineering

â†“

Training

â†“

Validation

â†“

Testing

â†“

Optimization

â†“

Export

â†“

ONNX/TFLite

â†“

Android Deployment
Training Split
Dataset	Percentage
Training	70%
Validation	15%
Testing	15%
4.19 Model Evaluation

Metrics:

Accuracy
Precision
Recall
F1-score
ROC-AUC
Confusion Matrix

Target Performance

Metric	Target
Accuracy	â‰¥95%
Precision	â‰¥94%
Recall	â‰¥95%
F1	â‰¥94%
False Positive	â‰¤3%
4.20 Model Optimization

Optimization techniques:

Quantization
Pruning
ONNX conversion
TensorFlow Lite conversion (optional)
Dynamic batching (backend)
Mixed precision (future)

Goal:

Fast inference
Low memory
Low battery impact
4.21 Model Deployment

Deployment workflow:

Training

â†“

Versioning

â†“

Validation

â†“

Signing

â†“

Cloud Storage

â†“

Android Download

â†“

Integrity Verification

â†“

Activation

Models are loaded independently from the application binary to allow future upgrades.

4.22 Model Updating

Update strategy:

Semantic versioning
Incremental downloads
Rollback support
Integrity verification
User-controlled updates (optional)

The app should never replace a working model with an unverified update.

4.23 AI Monitoring

The system should track anonymized operational metrics such as:

Detection counts
False positive reports
False negative reports
Average inference time
Model version adoption
Feature drift indicators (future)

Monitoring data must not include raw SMS content unless the user has explicitly opted in.

4.24 AI Risks
Risk	Mitigation
Class imbalance	Balanced sampling, class weighting
Dataset drift	Periodic retraining
Overfitting	Cross-validation, regularization
False positives	Threshold tuning, user feedback
Adversarial URLs	Ensemble features, anomaly detection
Resource constraints	Model optimization and quantization
4.25 Future AI Enhancements

Planned improvements include:

Multilingual transformer models
Federated learning
Graph Neural Networks for phishing infrastructure analysis
Vision Transformer (ViT) for QR image tampering detection
LLM-assisted phishing explanation
Adaptive risk scoring based on user behavior
Threat intelligence integration
Continuous online learning with privacy-preserving techniques



---

# Part 5: Android Application Design & Mobile System Requirements

**Version:** 1.0

Table of Contents
5.1 Mobile Application Vision

5.2 Android Architecture

5.3 Mobile Tech Stack

5.4 Application Modules

5.5 App Navigation

5.6 User Authentication

5.7 Splash Screen

5.8 Onboarding

5.9 Permission Manager

5.10 Home Dashboard

5.11 SMS Monitoring Module

5.12 QR Scanner Module

5.13 Threat Analysis Screen

5.14 Threat History

5.15 Notification Center

5.16 Feedback Module

5.17 Settings

5.18 Local Storage

5.19 Background Services

5.20 Offline Mode

5.21 Battery Optimization

5.22 Security Features

5.23 UI/UX Guidelines

5.24 Accessibility

5.25 Android Performance Requirements

5.26 Error Handling

5.27 Future Android Features
5.1 Mobile Application Vision

The Android application serves as the primary interface between users and the AI-powered phishing detection system.

Unlike traditional antivirus applications that operate mainly through periodic scans, this application provides continuous real-time protection by monitoring SMS messages and analyzing QR codes before users interact with potentially malicious content.

The application should:

Run silently in the background.
Consume minimal battery.
Deliver instant alerts.
Provide clear explanations.
Respect user privacy.
Continue operating even when offline.
5.2 Android Architecture

The application follows Clean Architecture + MVVM.

Presentation Layer

â†“

ViewModel Layer

â†“

Domain Layer

â†“

Repository Layer

â†“

Data Layer

â†“

AI Layer

â†“

Storage Layer
Presentation Layer

Contains:

Activities
Fragments / Jetpack Compose Screens
Navigation
UI Components
Domain Layer

Contains:

Business Logic
Use Cases
Validation
Risk Calculation
Data Layer

Contains:

Room Database
Firebase
REST APIs
Local Cache
AI Layer

Contains:

TinyBERT Runtime
XGBoost Runtime
Risk Engine
Explainable AI
5.3 Mobile Tech Stack
Language

Kotlin

UI

Jetpack Compose

Material Design 3

Motion Layout

Dependency Injection

Hilt

Local Database

Room

Authentication

Firebase Authentication

Networking

Retrofit

OkHttp

Camera

CameraX

ZXing

Background Jobs

WorkManager

Foreground Services

AI

ONNX Runtime

TensorFlow Lite (Optional)

Storage

Jetpack DataStore

EncryptedSharedPreferences

5.4 Application Modules
App

â”‚

â”œâ”€â”€ Authentication

â”œâ”€â”€ Dashboard

â”œâ”€â”€ SMS Monitor

â”œâ”€â”€ QR Scanner

â”œâ”€â”€ AI Engine

â”œâ”€â”€ Threat Reports

â”œâ”€â”€ Threat History

â”œâ”€â”€ Notification Center

â”œâ”€â”€ Feedback

â”œâ”€â”€ Settings

â””â”€â”€ Synchronization
5.5 App Navigation
Splash

â†“

Login

â†“

Permissions

â†“

Dashboard

â†“

â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

Dashboard

â”‚

â”œâ”€â”€ QR Scanner

â”œâ”€â”€ SMS Analysis

â”œâ”€â”€ Threat History

â”œâ”€â”€ Notification Center

â”œâ”€â”€ Feedback

â””â”€â”€ Settings

Bottom Navigation

Home

History

Scanner

Alerts

Settings
5.6 User Authentication
Authentication Options

Google Sign-In

Email Login

Guest Mode

Stored Information
UID

Name

Email

Registration Date

Device ID

Preferences
Session

Persistent Login

Auto Refresh

Secure Logout

5.7 Splash Screen

Purpose

Load AI Models
Verify Integrity
Load User Settings
Check Updates
Initialize Services

Display

LOGO

AI Protection Enabled

Loading...

â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆ

Maximum duration

2 seconds

5.8 Onboarding

First-time users are guided through:

Screen 1

Welcome

â†“

Screen 2

Features

â†“

Screen 3

Permissions

â†“

Screen 4

Privacy

â†“

Screen 5

Ready

5.9 Permission Manager

Required Permissions

Permission	Purpose
Receive SMS	Monitor incoming SMS
Read SMS	Analyze messages
Camera	QR scanning
Notifications	Threat alerts
Internet	Updates
Foreground Service	Background protection

Permission Flow

Permission Request

â†“

Granted

â†“

Enable Module

â†“

Denied

â†“

Explain Need

â†“

Retry
5.10 Home Dashboard

The dashboard provides an overview of the user's security status.

Widgets

Security Score

Today's Threats

SMS Scanned

QR Scanned

Blocked Attacks

Recent Alerts

Dashboard Layout

------------------------------------------------

Security Score

92%

------------------------------------------------

Today's Threats

5

------------------------------------------------

Safe SMS

45

------------------------------------------------

Blocked

3

------------------------------------------------

Recent Activity

------------------------------------------------

Quick Actions

Scan QR

View History

Settings

------------------------------------------------
5.11 SMS Monitoring Module

Runs continuously in the background.

Workflow

Incoming SMS

â†“

Broadcast Receiver

â†“

Permission Check

â†“

Parser

â†“

AI Model

â†“

Risk Score

â†“

Alert

â†“

History

Features

Automatic Detection

Sender Analysis

URL Extraction

Threat Classification

Risk Explanation

History

Message Categories

Safe

Spam

Smishing

Fraud

Unknown

5.12 QR Scanner Module

Supports

Live Camera

Gallery Import

Continuous Scanning

Workflow

Camera

â†“

ZXing

â†“

Decode

â†“

Extract URL

â†“

AI Analysis

â†“

Risk Score

â†“

Alert

Scanner Features

Auto Focus

Flash

Zoom

Gallery Import

Multiple QR Support (Future)

5.13 Threat Analysis Screen

Displays

Threat Type

Risk Score

Confidence

Reasons

Recommended Action

Evidence

Example

Threat

SMISHING

Confidence

97%

Risk

HIGH

Reasons

âœ“ Fake Banking Message

âœ“ Unknown Domain

âœ“ HTTPS Missing

âœ“ High Urgency

Recommended

BLOCK

IGNORE

REPORT
5.14 Threat History

Stores

SMS

QR

Time

Action

Risk

Filters

Today

Week

Month

Risk

Type

Search

Sender

URL

Threat

Export

CSV (Future)

PDF (Future)

5.15 Notification Center

Notification Types

Information

Warning

Critical

Example

âš  High Risk QR Code

Detected

Score

94%

Avoid Opening

[View Report]

Notification Channels

SMS Alerts

QR Alerts

Model Updates

General

5.16 Feedback Module

Purpose

Collect user corrections.

Buttons

Correct Detection

False Positive

False Negative

Report Threat

Workflow

Threat

â†“

Feedback

â†“

Upload

â†“

Dataset

â†“

Retraining
5.17 Settings

Categories

General

Notifications

Privacy

Detection

Appearance

About

Detection Settings

SMS Monitoring

QR Detection

Offline Detection

Cloud Sync

Privacy

Anonymous Feedback

Data Sharing

Delete History

Appearance

Dark Mode

Light Mode

System Theme

5.18 Local Storage

Room Database

Stores

Threat History

Feedback

Settings

AI Metadata

Encrypted Storage

Stores

Authentication Token

API Keys

Preferences

DataStore

Stores

Theme

Language

Notification Settings

5.19 Background Services

Foreground Service

Purpose

SMS Monitoring

Threat Detection

Model Updates

WorkManager

Purpose

Feedback Upload

Synchronization

Cleanup

Boot Receiver

Purpose

Restart protection after reboot.

5.20 Offline Mode

The application continues operating without internet.

Supported

SMS Detection

QR Detection

Risk Scoring

Threat History

Alerts

Unavailable Offline

Model Download

Analytics Upload

Feedback Sync

5.21 Battery Optimization

Strategies

Lazy Model Loading

Background Scheduling

Efficient Broadcast Handling

Compressed Models

Avoid Wake Locks

Suspend Unused Services

Target

Battery usage

Less than 5% daily

5.22 Security Features

Application Security

Encrypted Database

Secure Storage

Tamper Detection

APK Integrity Verification

Root Detection (Future)

Biometric Lock (Future)

Network Security

TLS 1.3

Certificate Pinning (Future)

Secure APIs

AI Security

Signed Models

Integrity Check

Rollback Support

5.23 UI/UX Guidelines

Design Language

Material Design 3

Colors

Green

Safe

Yellow

Warning

Red

Danger

Blue

Information

Icons

Shield

Threat

QR

SMS

History

Settings

Animations

Smooth

Subtle

Under 300 ms

5.24 Accessibility

Support

Large Text

High Contrast

Dark Theme

Screen Reader

Voice Feedback (Future)

Simple Language

5.25 Android Performance Requirements
Requirement	Target
Cold Start	<2 s
SMS Analysis	<500 ms
QR Analysis	<800 ms
Memory Usage	<250 MB
CPU Usage	<15% average
Battery Drain	<5% daily
App Size	<80 MB (excluding downloadable AI models)
Crash-Free Sessions	>99.5%
5.26 Error Handling
SMS Permission Denied

Display explanation.

Offer retry.

Disable SMS module.

Camera Failure

Switch to gallery import.

AI Model Missing

Load fallback model.

Prompt download when online.

Network Failure

Continue offline.

Queue synchronization.

Corrupted QR

Display

Unable to decode QR.

Please scan again.
Unexpected Crash

Automatically

Log

Recover

Restart monitoring

Notify user if required

5.27 Future Android Features

The Android application is designed to evolve beyond the Phase 1 implementation.

Planned enhancements include:

Floating security assistant
Browser overlay protection
Accessibility-based phishing detection
Email phishing scanning
WhatsApp and Telegram message analysis (subject to platform permissions)
Wear OS companion notifications
Widget displaying live security score
AI-powered chatbot for phishing education
Enterprise device management integration
Cross-device synchronization
Multi-language interface
Real-time threat intelligence updates
Android Auto compatibility for voice alerts
UI Screen Inventory
Screen	Purpose
Splash	Initialize app and AI models
Onboarding	Introduce features and permissions
Login	User authentication
Dashboard	Security overview
QR Scanner	Scan and analyze QR codes
SMS Analysis	View analyzed SMS messages
Threat Report	Detailed explanation of a detection
Threat History	Browse previous detections
Notification Center	View alerts and updates
Feedback	Submit corrections
Settings	Configure app behavior
About	Version, licenses, and project information




---

# Part 6: Backend Architecture, APIs, Database & Infrastructure

**Version:** 1.0

Table of Contents
6.1 Backend Vision

6.2 Backend Goals

6.3 Overall Backend Architecture

6.4 Backend Components

6.5 API Gateway

6.6 Authentication Service

6.7 AI Model Service

6.8 Threat Intelligence Service

6.9 Feedback Service

6.10 Notification Service

6.11 Synchronization Service

6.12 Database Design

6.13 PostgreSQL Schema

6.14 Firebase Architecture

6.15 REST API Specifications

6.16 Authentication Flow

6.17 API Security

6.18 Background Synchronization

6.19 File Storage

6.20 Logging

6.21 Monitoring

6.22 Infrastructure

6.23 CI/CD

6.24 Deployment

6.25 Disaster Recovery

6.26 Future Backend Roadmap
6.1 Backend Vision

Although the mobile application performs AI inference locally, the backend acts as the centralized platform responsible for:

User Authentication
Model Versioning
Feedback Collection
Analytics
Threat Intelligence
Model Distribution
Secure Synchronization

The backend must never become a dependency for core phishing detection.

Even if the backend becomes unavailable, the Android application should continue detecting threats offline.

6.2 Backend Goals

The backend must provide:

Secure Authentication
Fast APIs
Model Version Control
Anonymous Analytics
User Feedback Collection
Threat Synchronization
Future Enterprise Dashboard
Scalable Infrastructure
Backend Principles
Stateless APIs
REST-first Architecture
Modular Services
Horizontal Scalability
Secure by Design
Privacy First
6.3 Overall Backend Architecture
                 Android App

                       â”‚

             HTTPS / REST API

                       â”‚

                 API Gateway

                       â”‚

      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

      â”‚             â”‚             â”‚

 Authentication   Feedback   Model Service

      â”‚             â”‚             â”‚

      â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

                    â”‚

             Business Services

                    â”‚

         PostgreSQL Database

                    â”‚

         Firebase Authentication

                    â”‚

        Cloud Storage (Models)

                    â”‚

        Monitoring & Analytics
6.4 Backend Components
API Gateway

Responsible for

Request Routing
Authentication
Rate Limiting
Logging
Authentication Service

Responsible for

Login
Registration
JWT Validation
Session Management
Model Service

Responsible for

TinyBERT Distribution
XGBoost Distribution
Version Control
Rollback
Feedback Service

Responsible for

User Feedback
False Positives
False Negatives
Threat Intelligence

Responsible for

Threat Statistics
Future Threat Feeds
Analytics
Notification Service

Responsible for

Model Updates
Security Bulletins
Future Push Notifications
6.5 API Gateway

Technology

FastAPI

Responsibilities

Incoming Request

â†“

Authentication

â†“

Validation

â†“

Rate Limiting

â†“

Business Service

â†“

Response
Request Validation

Every request should validate

Headers

JWT

Payload

Timestamp

Version

Response Format
{
  "success": true,
  "message": "Feedback submitted successfully",
  "data": {},
  "timestamp": "2026-08-04T20:30:00Z"
}
Error Format
{
  "success": false,
  "error": {
    "code": "AUTH_001",
    "message": "Invalid Token"
  }
}
6.6 Authentication Service

Authentication Provider

Firebase Authentication

Supported Methods

Google Login
Email Password
Guest Mode

Future

Passkeys
Phone OTP
JWT Flow
User Login

â†“

Firebase

â†“

JWT

â†“

Android

â†“

Backend

â†“

Token Verification

â†“

API Access
User Profile
User

â†“

UID

â†“

Email

â†“

Display Name

â†“

Preferences

â†“

Device ID

â†“

Created At
6.7 AI Model Service

Purpose

Distribute AI models securely.

Models

TinyBERT

XGBoost

Isolation Forest

Configuration Files

Model Workflow
New Model

â†“

Validation

â†“

Versioning

â†“

Cloud Upload

â†“

Android Check

â†“

Download

â†“

Verification

â†“

Activation
Model Metadata
Field	Description
Version	Semantic Version
SHA256	Integrity Check
Size	Model Size
Created	Upload Date
Active	Boolean
6.8 Threat Intelligence Service

Current Scope

Stores

Anonymous Threat Statistics

Example

Total SMS

Safe

Spam

Smishing

Quishing

False Positive

False Negative

Future Scope

Threat Feeds

OpenPhish

PhishTank

URLHaus

6.9 Feedback Service

Users may report

Correct Detection

False Positive

False Negative

Unknown Threat

Feedback Flow

Threat

â†“

Feedback

â†“

Backend

â†“

Validation

â†“

Dataset

â†“

Retraining Queue
Feedback Object
{
  "feedbackId": "",
  "userId": "",
  "threatId": "",
  "feedbackType": "FALSE_POSITIVE",
  "comments": "",
  "timestamp": ""
}
6.10 Notification Service

Current

Model Update

Future

Security Alerts

Critical Threat Broadcast

New Phishing Campaigns

Example

New AI Model Available

Version 1.2.0

Improved Detection Accuracy

Download Now
6.11 Synchronization Service

Synchronizes

Threat History

Feedback

Settings

Model Versions

Sync Flow

Android

â†“

Queue

â†“

Internet Available

â†“

Sync

â†“

Backend

â†“

Success

Offline Mode

Queue requests.

Retry automatically.

6.12 Database Design

The backend uses PostgreSQL.

Main Tables

Users

ThreatHistory

Feedback

ModelVersions

Devices

Analytics

AuditLogs
Entity Relationship
Users

â”‚

â”œâ”€â”€â”€â”€ ThreatHistory

â”‚

â”œâ”€â”€â”€â”€ Feedback

â”‚

â”œâ”€â”€â”€â”€ Devices

â”‚

â””â”€â”€â”€â”€ Analytics
6.13 PostgreSQL Schema
Users
Column	Type
id	UUID
firebase_uid	VARCHAR
email	VARCHAR
name	VARCHAR
created_at	TIMESTAMP
Devices
Column	Type
id	UUID
user_id	UUID
device_model	VARCHAR
android_version	VARCHAR
app_version	VARCHAR
ThreatHistory
Column	Type
id	UUID
user_id	UUID
threat_type	VARCHAR
score	INTEGER
confidence	FLOAT
timestamp	TIMESTAMP
Feedback
Column	Type
id	UUID
threat_id	UUID
feedback	VARCHAR
comments	TEXT
created_at	TIMESTAMP
ModelVersions
Column	Type
id	UUID
model_name	VARCHAR
version	VARCHAR
checksum	VARCHAR
release_date	TIMESTAMP
AuditLogs
Column	Type
id	UUID
endpoint	VARCHAR
action	VARCHAR
status	INTEGER
timestamp	TIMESTAMP
6.14 Firebase Architecture

Firebase Services

Authentication

Cloud Messaging (Future)

Crashlytics

Analytics (Optional)

Collections

users

feedback

settings

devices

model_metadata
Security Rules
User can access only own data
Admin only for models
Read-only metadata
6.15 REST API Specifications
Authentication
POST
/api/v1/auth/login

Response

{
 "token":"JWT",
 "expires":"3600"
}
User Profile

GET

/api/v1/user/profile
Feedback

POST

/api/v1/feedback

Request

{
 "threatId":"",
 "feedback":"FALSE_POSITIVE",
 "comments":""
}
Model Check

GET

/api/v1/models/latest

Response

{
 "tinybert":"1.0.5",
 "xgboost":"1.0.2"
}
Model Download

GET

/api/v1/models/download/{model}
Analytics

POST

/api/v1/analytics
API Status Codes
Code	Meaning
200	Success
201	Created
400	Bad Request
401	Unauthorized
403	Forbidden
404	Not Found
429	Rate Limited
500	Server Error
6.16 Authentication Flow
User

â†“

Firebase Login

â†“

ID Token

â†“

Backend

â†“

Verify

â†“

JWT

â†“

Protected APIs
6.17 API Security

Every API must implement

HTTPS

JWT

Rate Limiting

Request Validation

Input Sanitization

SQL Injection Protection

CORS

Headers

Authorization

Bearer JWT
Rate Limits

Guest

30 requests/min

Authenticated

100 requests/min

6.18 Background Synchronization

WorkManager

â†“

Internet Available

â†“

Upload Feedback

â†“

Sync History

â†“

Check Models

â†“

Complete

Retry Policy

Exponential Backoff

6.19 File Storage

Stores

TinyBERT

XGBoost

ONNX Files

Documentation

Release Notes

Naming

tinybert_v1.0.4.onnx

xgboost_v1.0.1.json
6.20 Logging

Every API logs

Endpoint

Time

Latency

User

Status

Errors

Example

POST

/api/v1/feedback

200

120 ms
6.21 Monitoring

Metrics

API Latency

CPU

RAM

Errors

Traffic

Model Downloads

Feedback Count

Tools

Prometheus

Grafana

6.22 Infrastructure

Recommended Deployment

Docker

â†“

Nginx

â†“

FastAPI

â†“

PostgreSQL

â†“

Cloud Storage

Recommended Cloud

AWS

Azure

Google Cloud

Railway (Development)

Render (Development)

6.23 CI/CD

GitHub

â†“

Pull Request

â†“

Unit Tests

â†“

Integration Tests

â†“

Docker Build

â†“

Security Scan

â†“

Deploy

Tools

GitHub Actions

Docker

Pytest

6.24 Deployment

Development

Android Emulator

â†“

Local FastAPI

â†“

PostgreSQL

â†“

Firebase Emulator

Production

Android

â†“

Cloud

â†“

Docker

â†“

Load Balancer

â†“

FastAPI

â†“

PostgreSQL
6.25 Disaster Recovery

Database

Daily Backup

Weekly Snapshot

Monthly Archive

Rollback

Model Rollback

Database Restore

Version Rollback

6.26 Future Backend Roadmap

Future Enhancements

Enterprise Dashboard
Threat Intelligence Integration
Multi-Tenant Architecture
Web Admin Portal
AI Model A/B Testing
Federated Learning Coordinator
GraphQL API
WebSocket Notifications
Distributed Model Serving
Kubernetes Deployment
Auto Scaling
Multi-Region Replication
Backend Directory Structure
backend/

â”œâ”€â”€ app/

â”‚ â”œâ”€â”€ api/

â”‚ â”œâ”€â”€ auth/

â”‚ â”œâ”€â”€ models/

â”‚ â”œâ”€â”€ services/

â”‚ â”œâ”€â”€ repositories/

â”‚ â”œâ”€â”€ schemas/

â”‚ â”œâ”€â”€ middleware/

â”‚ â”œâ”€â”€ utils/

â”‚ â”œâ”€â”€ ai/

â”‚ â””â”€â”€ config/

â”œâ”€â”€ migrations/

â”œâ”€â”€ tests/

â”œâ”€â”€ docker/

â”œâ”€â”€ scripts/

â”œâ”€â”€ docs/

â””â”€â”€ requirements.txt
Recommended Technology Stack
Layer	Technology
Backend Framework	FastAPI
Authentication	Firebase Auth
Database	PostgreSQL
ORM	SQLAlchemy
Migrations	Alembic
API Validation	Pydantic
Containerization	Docker
Reverse Proxy	Nginx
Monitoring	Prometheus + Grafana
CI/CD	GitHub Actions
Logging	Loguru / Python Logging
Storage	S3-compatible Object Storage




---

# Part 7: Authentication & Threat Management Module (Web Admin Dashboard & Centralized Threat Backend)

**Version:** 1.0

Table of Contents
7.1 Feature Overview
7.2 User Roles & Access Control
7.3 Authentication System Architecture
7.4 User Database Schema
7.5 Threat Database Schema
7.6 Smishing Detection Data Pipeline & Storage
7.7 Quishing Detection Data Pipeline & Storage
7.8 Correlation Between SMS and QR Threats
7.9 Admin Web Dashboard Overview
7.10 Threat Monitoring Page & Filtering Specifications
7.11 Threat Details Page Specifications
7.12 Threat Analytics & Visualization System
7.13 Backend Architecture & Decoupled Access Pattern
7.14 REST API Structure & Endpoints
7.15 System Security & Data Governance Requirements
7.16 Recommended Technology Stack
7.17 End-to-End Operational Lifecycle
7.18 Conceptual Terminology & Database Naming


7.1 Feature Overview

The application must include a secure user authentication system and a centralized backend for storing and managing security events detected by the system.

The system consists of two main interfaces:
1. Mobile Application — used by normal users to detect and analyze suspicious SMS messages and QR codes in real time.
2. Web Security Dashboard — used by authorized administrators/security analysts to monitor detected threats, users, threat statistics, and detection history.

The mobile application and web dashboard must communicate with the backend through authenticated APIs. The database must never be directly exposed to the client applications.


7.2 User Roles & Access Control

The system supports two primary user roles with strict Role-Based Access Control (RBAC):

1. Normal User (Mobile Application)
   A normal user can:
   - Register an account.
   - Log in and log out.
   - Manage their basic profile.
   - Scan QR codes.
   - Analyze SMS messages.
   - View the result of threat analysis (Safe, Suspicious, or Malicious).
   - View their previous detection history.
   - Report suspicious content.
   - View whether a detected item is Safe, Suspicious, or Malicious.
   
   Isolation Rule: A normal user must not be able to access other users' data or the administrator dashboard.

2. Administrator / Security Analyst (Web Security Dashboard)
   An administrator can:
   - Log in through the web dashboard.
   - View overall system statistics.
   - View detected threats from users.
   - Search and filter threats.
   - View individual threat details.
   - View detected malicious URLs/domains.
   - View smishing and quishing statistics.
   - View risk scores.
   - View detection timestamps.
   - Monitor threat trends.
   - Manage reported threats.
   - View appropriate user activity/security events.
   
   Authorization Rule: Administrators must have additional authorization beyond simply being logged in (verified ADMIN role claim / token privilege).


7.3 Authentication System Architecture

Implement authentication using a secure authentication mechanism (e.g., Firebase Authentication or backend JWT session management).

General Authentication Lifecycle Flow:
Register
   ↓
Account Created
   ↓
Login
   ↓
Authentication
   ↓
Role Verification
   ↓
Application / Dashboard

For the Mobile Application:
User
 ↓
Login/Register
 ↓
Authentication Service
 ↓
Verify Credentials
 ↓
Create Authenticated Session
 ↓
Mobile Home Screen

For the Administrator:
Administrator
 ↓
Admin Login
 ↓
Authentication
 ↓
Verify Admin Role
 ↓
Admin Dashboard

Security Requirements:
- Passwords must never be stored as plaintext under any circumstances.
- Authentication credentials must be handled by a proper authentication service.
- Use secure password hashing (e.g., bcrypt or Argon2) if credentials are managed by the backend.
- Use HTTPS (TLS 1.3) for all network communication.
- Implement strict role-based access control (RBAC).
- Users must only access their own private information.
- Admin endpoints must explicitly require administrator authorization.
- Sessions/tokens must be validated on every protected API request.
- Logout must invalidate and remove the local authentication session.
- Do not store passwords in Firestore, PostgreSQL, or another application database as plain text.


7.4 User Database Schema

Create a `users` collection/table to store application-level user profiles (separate from authentication credentials).

Schema Definition:
Field           Type        Description
userId          UUID/String Unique user identifier assigned by Auth Provider
name            String      User's full name
email           String      User's email address
phone           String      User's phone number (optional/masked)
role            Enum        USER | ADMIN
createdAt       Timestamp   Account creation timestamp
lastLoginAt     Timestamp   Timestamp of last successful authentication
accountStatus   Enum        ACTIVE | SUSPENDED | DISABLED

Role Values:
- `USER` : Normal mobile application user.
- `ADMIN`: Administrator / Security Analyst.

Account Status Values:
- `ACTIVE`   : Account fully operational.
- `SUSPENDED`: Account temporarily restricted due to policy violation or security lock.
- `DISABLED` : Account permanently deactivated.

Credential Handling Rule: The authentication provider handles sensitive authentication credentials. The application database stores only the user information required by the application.


7.5 Threat Database Schema

Every time the AI/ML detection system analyzes an SMS message or QR code, the resulting security event should be recorded in the backend.

Create a `threats` collection/table.

Schema Definition:
Field           Type        Description
threatId        UUID/String Unique identifier for the threat event
userId          UUID/String ID of the user who received/scanned the content
threatType      Enum        SMISHING | QUISHING | SAFE | SUSPICIOUS
inputType       Enum        SMS | QR_CODE | URL
riskScore       Integer     Model's calculated risk score (0 to 100)
classification  Enum        SAFE | SUSPICIOUS | MALICIOUS
detectedUrl     String      Extracted URL (if present)
domain          String      Extracted domain / FQDN
detectionReason Array[Str]  List of detection reasons / XAI indicators
modelUsed       String      Name of ML model used (e.g., TinyBERT, XGBoost)
actionTaken     Enum        BLOCKED | ALLOWED | REPORTED | REVIEWED
createdAt       Timestamp   Detection timestamp
status          Enum        NEW | REVIEWED | RESOLVED | FALSE_POSITIVE

Possible `threatType` Values:
- `SMISHING`
- `QUISHING`
- `SAFE`
- `SUSPICIOUS`

Possible `inputType` Values:
- `SMS`
- `QR_CODE`
- `URL`

Possible `classification` Values:
- `SAFE`
- `SUSPICIOUS`
- `MALICIOUS`

Calculated Risk Score & Thresholds:
The score represents the estimated security risk on a 0–100 scale:
- 0–30    → SAFE (Normal content, safe for interaction)
- 31–70   → SUSPICIOUS (Potential security risk, caution advised)
- 71–100  → MALICIOUS (High security risk, threat blocked)

Threshold Configuration Rule: The exact threshold boundaries (0–30, 31–70, 71–100) are dynamically configurable in backend configuration files rather than hard-coded throughout the application.


7.6 Smishing Detection Data Pipeline & Storage

When an SMS is analyzed, store the necessary information required for security analysis and history.

Smishing Detection Data Flow:
SMS
 ↓
Preprocessing
 ↓
NLP Feature Extraction
 ↓
Smishing Model (TinyBERT)
 ↓
Risk Score
 ↓
Classification
 ↓
Threat Database

Detection Record Example:
{
  "threatId": "THR-SMS-8092",
  "userId": "USR-48102",
  "threatType": "SMISHING",
  "inputType": "SMS",
  "riskScore": 87,
  "classification": "MALICIOUS",
  "modelUsed": "TinyBERT",
  "detectionReason": [
    "Urgent language",
    "Suspicious URL",
    "Financial request",
    "Unknown sender"
  ],
  "actionTaken": "BLOCKED",
  "createdAt": "2026-08-24T18:32:00Z",
  "status": "NEW"
}

Privacy Protection Requirement:
- Do not unnecessarily store the user's entire private SMS history.
- The system should store only the minimum information necessary for security analysis, reporting, and research (extracted URLs, domain names, sender ID format, NLP keyword indicators, risk score).
- If the project requires storing message content for research purposes, it should be clearly controlled, anonymized, and user-consented.


7.7 Quishing Detection Data Pipeline & Storage

When a QR code is scanned:

Quishing Detection Data Flow:
QR Image
   ↓
QR Decoder
   ↓
Extract URL
   ↓
URL Feature Extraction
   ↓
XGBoost / URL Detection Model
   ↓
Risk Score
   ↓
Classification
   ↓
Threat Database

Detection Record Example:
{
  "threatId": "THR-QR-4910",
  "userId": "USR-19204",
  "threatType": "QUISHING",
  "inputType": "QR_CODE",
  "detectedUrl": "http://sbi-verify-kyc.bank-update.xyz/login.php",
  "domain": "bank-update.xyz",
  "riskScore": 91,
  "classification": "MALICIOUS",
  "modelUsed": "XGBoost",
  "detectionReason": [
    "Suspicious domain",
    "URL obfuscation",
    "Abnormal URL structure"
  ],
  "actionTaken": "BLOCKED",
  "createdAt": "2026-08-24T18:35:12Z",
  "status": "NEW"
}


7.8 Correlation Between SMS and QR Threats

One of the important features of the major project is the correlation between smishing and quishing.

Example Attack Scenario:
SMS: "Your bank account is blocked. Scan this QR code immediately."
        ↓
     QR Code
        ↓
   Extract URL
        ↓
   URL Analysis
        ↓
    Malicious

System Correlation Flow:
SMS Threat ID
      ↓
Related QR Threat ID
      ↓
Correlation Analysis
      ↓
Combined Risk Assessment

Association Mechanism:
The backend associates related security events by linking `threats.relatedThreatId` and `threats.campaignId`. This capability allows security analysts to identify coordinated phishing campaigns where SMS social engineering is combined with QR code redirection.


7.9 Admin Web Dashboard Overview

Create a separate web-based administrator dashboard.

The dashboard displays overall system security counters and threat metrics:

Widgets Overview Layout:
┌────────────────┬────────────────┬────────────────┐
│ Total Users    │ Total Threats  │ Malicious      │
│     1,250      │      3,420     │      742       │
└────────────────┴────────────────┴────────────────┘

┌────────────────┬────────────────┬────────────────┐
│ Smishing       │ Quishing       │ Suspicious     │
│     1,840      │      1,580     │      1,120     │
└────────────────┴────────────────┴────────────────┘

Summary Metrics Tracked:
- Total Users: 1,250
- Total Scans: 18,450
- Total Threats: 3,420
- Smishing Threats: 1,840
- Quishing Threats: 1,580
- Malicious Threats: 742
- Suspicious Threats: 1,120
- Safe Detections: 15,030


7.10 Threat Monitoring Page & Filtering Specifications

The administrator must have a dedicated threat monitoring page displaying detected security events in a structured table.

Threat Monitoring Table Format:
Time    Type      Input Risk  Classification  Status
10:32   Smishing  SMS   91    Malicious       Blocked
10:36   Quishing  QR    84    Malicious       Reported
10:42   Smishing  SMS   52    Suspicious      Reviewed
10:45   Quishing  QR    12    Safe            Allowed

Administrator Operations:
- Search: Full-text keyword search across threat ID, domain, detected URL, sender ID, or user ID.
- Filter by threat type: Filter by SMISHING or QUISHING.
- Filter by risk level: Filter by High (71–100), Medium (31–70), or Low (0–30) risk.
- Filter by date: Date range picker (Today, Last 7 Days, Custom range).
- Filter by classification: Filter by SAFE, SUSPICIOUS, or MALICIOUS.
- Sort by risk score: Ascending/Descending ordering by risk score or timestamp.
- Open detailed threat information: Click any row to navigate to the detailed threat inspector.


7.11 Threat Details Page Specifications

When the administrator selects a threat, show detailed security analysis information:

Threat Details Inspector View:
Threat ID:
THR-10293

Threat Type:
QUISHING

Risk Score:
92/100

Classification:
MALICIOUS

Detected URL:
example.com/login

Domain:
example.com

Detection Model:
XGBoost

Detection Time:
24-Aug-2026 18:32

Reasons:
• Suspicious URL structure
• Domain anomaly
• Login-related phishing pattern
• High-risk reputation

Action:
BLOCKED

Data Governance Rule: Sensitive user information (such as personal phone numbers or user email addresses) must be masked or restricted according to administrator permissions and role constraints.


7.12 Threat Analytics & Visualization System

The dashboard should provide visual analytics to help security analysts understand threat patterns:

1. Threat Distribution:
   Smishing    ███████████████  (53.8%)
   Quishing    ██████████       (46.2%)

2. Classification Breakdown:
   Safe          █████████████  (78.2%)
   Suspicious    ███████        (13.4%)
   Malicious     █████          (8.4%)

3. Threat Trend:
   Display the number of detected threats over time intervals:
   - Day
   - Week
   - Month
   This visual trend analysis allows administrators to identify emerging phishing campaigns and spike activity in real time.


7.13 Backend Architecture & Decoupled Access Pattern

Do not allow the web dashboard or Android application to directly manipulate the entire database.

System Access Architecture:
Android Application
        │
        │ HTTPS
        ↓
   Backend API
        │
        ├── Authentication
        ├── Authorization
        ├── Threat Processing
        ├── Validation
        └── Database Access
                 │
                 ↓
              Database
                 ↑
                 │
          Backend API
                 ↑
                 │
        Admin Web Dashboard

Request Validation & Isolation Rules:
Every API request must be authenticated and authorized by the backend API:
- Normal User Request (e.g., `GET /api/detections/history`): Returns ONLY the requesting user's own detection records (`User A -> User A's threats`).
- Administrator Request (e.g., `GET /api/admin/threats`): Requires verified `ADMIN` role claim before returning system-wide or aggregated threat data (`Admin -> Authorized threat data`).


7.14 REST API Structure & Endpoints

The backend exposes structured RESTful API endpoints:

Authentication Endpoints:
- `POST /api/auth/register` : User account creation
- `POST /api/auth/login`    : Authenticate credentials and return session token
- `POST /api/auth/logout`   : Terminate local and backend auth session

User Profile Endpoints:
- `GET  /api/users/profile` : Fetch logged-in user profile details
- `PUT  /api/users/profile` : Update profile information

Threat Detection Endpoints (Mobile App):
- `POST /api/detections/sms`     : Submit SMS analysis telemetry to threat database
- `POST /api/detections/qr`      : Submit QR scan telemetry to threat database
- `GET  /api/detections/history` : Fetch authenticated user's personal detection history

Admin Dashboard Endpoints (Admin Authorization Required):
- `GET  /api/admin/dashboard`     : Fetch overview system counters and metrics
- `GET  /api/admin/threats`       : Search, filter, and paginate global detected threats
- `GET  /api/admin/threats/{id}`  : Fetch complete threat details for inspection
- `GET  /api/admin/statistics`   : Fetch aggregated threat analytics and time-series trend data

Authorization Rule: All `/api/admin/*` endpoints must verify administrator claims. Unauthorized requests must return HTTP 403 Forbidden.


7.15 System Security & Data Governance Requirements

Because this is a cybersecurity project, security must be part of the feature itself.

Implement:
1. Secure authentication system.
2. Role-Based Access Control (RBAC).
3. Encryption in transit via HTTPS (TLS 1.3).
4. Input validation and payload sanitization on all endpoints.
5. API authorization token validation on protected endpoints.
6. Secure token handling and storage (no sensitive token logging).
7. Password hashing through the authentication provider/backend (bcrypt/Argon2).
8. Database security rules preventing unauthorized client database access.
9. Rate limiting for authentication and API endpoints where appropriate.
10. Protection against unauthorized database access (decoupled backend proxy).
11. Audit logging of important administrative and security events.
12. Minimal collection of sensitive user data.
13. No plaintext passwords stored anywhere.
14. No unnecessary storage of private SMS content.
15. Avoid exposing authentication tokens in server logs or admin dashboard pages.


7.16 Recommended Technology Architecture

For your existing project, a practical technology stack implementation is:

                ┌─────────────────────┐
                │   Android App       │
                │      Kotlin         │
                └──────────┬──────────┘
                           │
                           ↓
                  Firebase Authentication
                           │
                           ↓
                    Backend / API
                           │
             ┌─────────────┴─────────────┐
             ↓                           ↓
       ML Detection                  Database
       Python Models                Firestore
             │                           │
             │                           │
             └─────────────┬─────────────┘
                           ↓
                  Admin Web Dashboard
                     React / Next.js

ML Model Component Distribution:
- Smishing → NLP Preprocessing → TinyBERT Model → Risk Score & Reasons
- Quishing → QR Decode → URL Feature Extraction → XGBoost Model → Risk Score & Reasons


7.17 End-to-End Operational Lifecycle

A complete end-to-end operational sequence of the system:

1. User creates an account.
2. User logs into the Android application.
3. User receives a suspicious SMS.
4. Application analyzes the SMS.
5. NLP preprocessing is performed.
6. TinyBERT/NLP model evaluates the message.
7. System calculates a risk score of 89.
8. System classifies it as MALICIOUS.
9. User receives a warning.
10. Detection event is securely sent to backend.
11. Backend validates the authenticated user.
12. Detection record is stored in the database.
13. Administrator logs into the web dashboard.
14. Administrator sees the new threat.
15. Dashboard updates threat statistics.
16. Administrator can inspect the threat details.
17. The event contributes to the system's threat analytics.


7.18 Conceptual Terminology & Database Naming

For your major project, avoid referring to the database as simply "all credentials and threats".

Recommended Terminology:
"Centralized Threat Intelligence and Security Event Database"

Data Governance Principle:
- Credentials and passwords are managed exclusively by the dedicated authentication provider (Firebase Auth / Auth Service).
- The central database strictly stores user profile metadata, security telemetry events, threat metadata, detection results, and threat intelligence statistics.

This architectural separation reflects industry security standards and prevents storing authentication credentials alongside threat intelligence data.




---

# Part 8: Security, Testing, Deployment, DevOps & Product Roadmap

**Version:** 1.0

Table of Contents
7.1 Security Vision

7.2 Security Architecture

7.3 Application Security

7.4 Android Security

7.5 AI Model Security

7.6 Backend Security

7.7 API Security

7.8 Data Privacy

7.9 Threat Modeling

7.10 Risk Assessment

7.11 Testing Strategy

7.12 Unit Testing

7.13 Integration Testing

7.14 System Testing

7.15 AI Model Testing

7.16 Performance Testing

7.17 Security Testing

7.18 User Acceptance Testing

7.19 DevOps Pipeline

7.20 CI/CD Pipeline

7.21 Release Strategy

7.22 Deployment Strategy

7.23 Monitoring & Observability

7.24 Backup & Recovery

7.25 Maintenance

7.26 Product Roadmap

7.27 Milestones

7.28 Future Enhancements

7.29 Acceptance Criteria

7.30 Project Completion Definition
7.1 Security Vision

The system is designed using a Security-by-Design approach, ensuring that security is integrated into every stage of development rather than added as an afterthought.

The primary objectives are:

Protect users from phishing attacks.
Protect user data and privacy.
Secure AI models from tampering.
Secure APIs and backend services.
Maintain application integrity.
Ensure regulatory compliance.
Security Objectives

The system shall:

Detect phishing attempts before user interaction.
Prevent unauthorized access.
Protect sensitive information.
Ensure encrypted communication.
Minimize attack surface.
Support secure model updates.
7.2 Security Architecture
                User

                 â”‚

          Android Device

                 â”‚

      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”

      â”‚                     â”‚

 Local AI Engine      Secure Storage

      â”‚                     â”‚

      â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

                 â”‚

         HTTPS (TLS 1.3)

                 â”‚

            API Gateway

                 â”‚

     Authentication Service

                 â”‚

         Business Services

                 â”‚

          PostgreSQL

                 â”‚

      Monitoring & Logging
Security Layers
Device Security
Application Security
AI Security
Network Security
Backend Security
Database Security
Infrastructure Security
7.3 Application Security

The Android application shall implement:

Secure Authentication
Firebase Authentication
JWT Tokens
Secure Sessions
Secure Storage

Sensitive data shall never be stored in plaintext.

Use:

Android Keystore
EncryptedSharedPreferences
SQLCipher (optional)
Input Validation

Validate:

SMS content
QR payload
URLs
API responses
Runtime Protection

Detect:

Debuggers
Emulator (optional)
Tampered APK
Hooking frameworks (future)
7.4 Android Security
Permissions

Only request required permissions.

Permission	Justification
SMS	Threat detection
Camera	QR scanning
Notifications	User alerts
Internet	Updates
Secure Components
Foreground Service
WorkManager
BroadcastReceiver
CameraX
APK Protection

Implement:

Code obfuscation (ProGuard/R8)
APK signing
Integrity verification
Play Integrity API (future)
7.5 AI Model Security

AI models are valuable assets and must be protected.

Requirements
Digitally signed models
SHA-256 checksum verification
Version validation
Rollback support
Model Update Workflow
Cloud Model

â†“

SHA-256 Verification

â†“

Digital Signature Check

â†“

Store Securely

â†“

Activate

â†“

Delete Old Version
7.6 Backend Security

The backend shall implement:

HTTPS
JWT validation
Rate limiting
SQL injection prevention
XSS protection
CSRF protection (web dashboard)
Input sanitization
Secrets Management

Store secrets using:

Environment variables
Secret Manager
Vault (future)

Never hardcode:

API keys
Database passwords
JWT secrets
7.7 API Security

Every API request must include:

Authorization: Bearer <JWT>
Security Headers
Strict-Transport-Security

X-Content-Type-Options

X-Frame-Options

Content-Security-Policy
API Validation

Validate:

JSON Schema
Required fields
Field length
Data type
Token validity
Rate Limiting
User Type	Limit
Guest	30 req/min
Logged-in	100 req/min
Admin	500 req/min
7.8 Data Privacy

Privacy is a core design principle.

User Data

Collected:

Email
Preferences
Feedback (optional)

Not collected by default:

SMS contents
Contact list
Photos
Personal files
User Controls

Users may:

Delete history
Disable analytics
Disable cloud sync
Export data
Delete account
7.9 Threat Modeling

Potential threats:

Threat	Description
Smishing	Fake SMS
Quishing	Malicious QR
Credential Theft	Fake login pages
URL Spoofing	Homograph attacks
Replay	Reusing requests
API Abuse	Automated attacks
Model Tampering	Modified AI models
STRIDE Analysis
Threat	Covered
Spoofing	Yes
Tampering	Yes
Repudiation	Yes
Information Disclosure	Yes
Denial of Service	Yes
Elevation of Privilege	Yes
7.10 Risk Assessment
Risk	Probability	Impact	Mitigation
False Positive	Medium	Medium	Threshold tuning
False Negative	Low	High	Hybrid AI models
Model Drift	Medium	High	Retraining
Battery Usage	Medium	Medium	Optimization
API Downtime	Low	Medium	Offline mode
Data Leakage	Low	Critical	Encryption
7.11 Testing Strategy

Testing is divided into multiple layers.

Unit Testing

â†“

Integration Testing

â†“

System Testing

â†“

AI Testing

â†“

Performance Testing

â†“

Security Testing

â†“

UAT

â†“

Release
7.12 Unit Testing

Framework:

JUnit

Mockito

Coverage Target:

90%

Modules:

QR Scanner
SMS Parser
Risk Engine
URL Parser
Settings
ViewModels
7.13 Integration Testing

Verify:

Android â†” AI
Android â†” Backend
Backend â†” Database
Backend â†” Firebase

Example

SMS

â†“

Parser

â†“

TinyBERT

â†“

Risk

â†“

Alert
7.14 System Testing

Test the complete application.

Scenarios:

Banking SMS
Malicious QR
Safe QR
No Internet
App Restart
Phone Reboot
Model Update
7.15 AI Model Testing

Evaluate:

Accuracy
Precision
Recall
F1-score
ROC-AUC
Latency

Target:

Metric	Target
Accuracy	â‰¥95%
Recall	â‰¥95%
Precision	â‰¥94%
F1	â‰¥94%
7.16 Performance Testing

Targets

Metric	Target
SMS Detection	<500 ms
QR Detection	<800 ms
App Launch	<2 sec
Memory	<250 MB
Battery	<5% daily

Tools

Android Profiler
Benchmark Macro
JMeter (Backend)
Locust
7.17 Security Testing

Perform:

OWASP Mobile Top 10 testing
Penetration testing
SQL injection testing
XSS testing
API fuzzing
Authentication testing

Tools:

OWASP ZAP
Burp Suite Community
MobSF
JADX
APK Analyzer
7.18 User Acceptance Testing (UAT)

Participants:

Students
Faculty
Banking users
Elderly users

Evaluate:

Ease of use
Alert clarity
Performance
False positives
UI experience
7.19 DevOps Pipeline
Developer

â†“

GitHub

â†“

Pull Request

â†“

Code Review

â†“

Unit Tests

â†“

Build

â†“

Security Scan

â†“

Docker

â†“

Deploy

â†“

Monitoring
7.20 CI/CD Pipeline

Tools

GitHub Actions
Docker
FastAPI
Firebase App Distribution
SonarQube (optional)

Pipeline

Commit

â†“

Lint

â†“

Tests

â†“

Coverage

â†“

Build

â†“

Security Scan

â†“

Docker

â†“

Deploy
7.21 Release Strategy

Release Types

Version	Purpose
Alpha	Internal Team
Beta	Faculty Testing
RC	Final Validation
Production	Public Release

Versioning

Semantic Versioning

Example

v1.0.0

v1.1.0

v2.0.0
7.22 Deployment Strategy
Development
Android Emulator

â†“

Local Backend

â†“

Local PostgreSQL
Production
Android App

â†“

Google Play

â†“

FastAPI

â†“

Docker

â†“

PostgreSQL

â†“

Cloud Storage
7.23 Monitoring & Observability

Monitor:

Crash rate
API latency
Model version
Detection statistics
Battery usage
Memory usage

Tools

Firebase Crashlytics
Prometheus
Grafana
Android Profiler
7.24 Backup & Recovery

Database

Daily backups
Weekly snapshots
Monthly archives

Models

Version history
Rollback support

Recovery Targets

Metric	Target
RPO	<24 hours
RTO	<2 hours
7.25 Maintenance

Scheduled Activities

Monthly dependency updates
Quarterly model retraining
Annual security audit
Continuous bug fixes
7.26 Product Roadmap
Phase 1
SMS Detection
QR Detection
AI Models
Offline Detection
Phase 2
Email phishing
WhatsApp analysis
Browser protection
Phase 3
Enterprise Dashboard
Threat Intelligence
Federated Learning
Phase 4
Cross-platform support
iOS Application
Desktop Client
7.27 Milestones
Milestone	Deliverable
M1	Literature Review
M2	Dataset Collection
M3	AI Model Development
M4	Android Prototype
M5	Backend Development
M6	Integration
M7	Testing
M8	Final Deployment
M9	Documentation
M10	Project Demonstration
7.28 Future Enhancements

Future improvements include:

Multi-language NLP
Vision Transformer (ViT) for QR image analysis
LLM-assisted phishing explanations
Real-time threat intelligence integration
Federated learning
Enterprise policy management
Browser extension
Wear OS support
Accessibility enhancements
Adaptive risk scoring based on user behavior
7.29 Acceptance Criteria

The project will be considered complete when:

ID	Acceptance Criteria
AC-01	SMS messages are analyzed in real time.
AC-02	QR codes are scanned and classified accurately.
AC-03	Risk scores include explainable reasons.
AC-04	Core detection works offline.
AC-05	AI models meet target accuracy.
AC-06	Backend services authenticate and synchronize securely.
AC-07	Threat history and feedback function correctly.
AC-08	Security testing shows no critical vulnerabilities.
AC-09	Performance targets are achieved on supported Android devices.
AC-10	Documentation and deployment artifacts are complete.
7.30 Project Completion Definition

The project is considered production-ready when the following deliverables are completed:

Product
Android application
AI detection engine
Backend services
REST APIs
Local and cloud databases
AI
TinyBERT-based smishing detector
XGBoost-based quishing detector
Isolation Forest anomaly detector
Explainable AI engine
Infrastructure
Dockerized backend
CI/CD pipeline
Monitoring dashboard
Automated backups
Quality
Unit testing
Integration testing
Security testing
User acceptance testing
Performance validation
Documentation
Product Requirements Document (PRD)
System Design Document (SDD)
API Documentation
Database Schema
Deployment Guide
User Manual
Developer Guide
Testing Report
Final Project Report



---

# Part 9: Implementation Roadmap, UI Specifications & Final Documentation

**Version:** 1.0

Table of Contents
8.1 Project Overview

8.2 Development Methodology

8.3 Sprint Planning

8.4 Team Responsibilities

8.5 Complete Folder Structure

8.6 Android Module Breakdown

8.7 Backend Module Breakdown

8.8 AI Module Breakdown

8.9 Database Implementation

8.10 UI Wireframes

8.11 Design System

8.12 User Experience Guidelines

8.13 Project Timeline

8.14 Risk Register

8.15 Cost Analysis

8.16 Hardware Requirements

8.17 Software Requirements

8.18 Documentation

8.19 Deliverables

8.20 Success Criteria

8.21 Future Scope

8.22 Appendix

8.23 References

8.24 Final Sign-Off
8.1 Project Overview
Project Name

Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

Objective

Develop a mobile-first cybersecurity application capable of detecting and preventing phishing attacks delivered through:

SMS
QR Codes
URLs

using Artificial Intelligence while maintaining:

High accuracy
Low latency
Privacy
Explainability
Offline capability
Expected Deliverables

The completed project shall include:

Android Application
AI Models
Backend
APIs
Database
Documentation
Testing Reports
Deployment Guide
8.2 Development Methodology

The project will follow an Agile Scrum methodology.

Sprint Duration

2 Weeks

Total Development Duration

24 Weeks

Sprint Workflow
Planning

â†“

Development

â†“

Testing

â†“

Review

â†“

Deployment

â†“

Retrospective
8.3 Sprint Planning
Sprint 1

Project Setup

Deliverables

Android Project
Backend Project
Git Repository
Firebase Setup
Sprint 2

Authentication

Deliverables

Login
Registration
Firebase Authentication
Sprint 3

SMS Monitoring

Deliverables

SMS Receiver
Parser
Local Storage
Sprint 4

QR Scanner

Deliverables

CameraX
ZXing
QR Decoder
Sprint 5

AI Integration

Deliverables

TinyBERT
XGBoost
ONNX Runtime
Sprint 6

Risk Engine

Deliverables

Risk Scoring
Explainable AI
Sprint 7

Backend APIs

Deliverables

Feedback
Model Update
Analytics
Sprint 8

Testing

Deliverables

Unit Testing
Integration Testing
Sprint 9

Optimization

Deliverables

Performance
Battery
Model Compression
Sprint 10

Deployment

Deliverables

Docker
Cloud Deployment
Final APK
8.4 Team Responsibilities
Member	Responsibilities
Nithin Naik R	Android Architecture, SMS Monitoring, Data Acquisition, UI Integration
S MD Umar Talha Azeez	AI/ML Models, TinyBERT, Dataset Preparation, Training
Sagar S H	QR Detection, URL Intelligence, Feature Engineering, XGBoost
Prashant Kushwaha	Backend Development, APIs, Database, Dashboard, Deployment
8.5 Complete Folder Structure
RealTime-Phishing-Detection/

â”‚

â”œâ”€â”€ android/

â”‚

â”œâ”€â”€ backend/

â”‚

â”œâ”€â”€ ai/

â”‚

â”œâ”€â”€ datasets/

â”‚

â”œâ”€â”€ models/

â”‚

â”œâ”€â”€ docs/

â”‚

â”œâ”€â”€ scripts/

â”‚

â”œâ”€â”€ docker/

â”‚

â”œâ”€â”€ tests/

â”‚

â”œâ”€â”€ deployment/

â”‚

â”œâ”€â”€ diagrams/

â”‚

â””â”€â”€ README.md
Android Structure
android/

â”‚

â”œâ”€â”€ app/

â”‚

â”œâ”€â”€ ui/

â”‚

â”œâ”€â”€ navigation/

â”‚

â”œâ”€â”€ sms/

â”‚

â”œâ”€â”€ qr/

â”‚

â”œâ”€â”€ ai/

â”‚

â”œâ”€â”€ repository/

â”‚

â”œâ”€â”€ database/

â”‚

â”œâ”€â”€ services/

â”‚

â”œâ”€â”€ workers/

â”‚

â””â”€â”€ utils/
Backend Structure
backend/

â”œâ”€â”€ api/

â”œâ”€â”€ auth/

â”œâ”€â”€ services/

â”œâ”€â”€ ai/

â”œâ”€â”€ models/

â”œâ”€â”€ repositories/

â”œâ”€â”€ middleware/

â”œâ”€â”€ schemas/

â”œâ”€â”€ database/

â”œâ”€â”€ config/

â””â”€â”€ tests/
AI Structure
ai/

â”œâ”€â”€ preprocessing/

â”œâ”€â”€ feature_engineering/

â”œâ”€â”€ tinybert/

â”œâ”€â”€ xgboost/

â”œâ”€â”€ anomaly/

â”œâ”€â”€ explainable_ai/

â”œâ”€â”€ training/

â”œâ”€â”€ evaluation/

â””â”€â”€ deployment/
8.6 Android Module Breakdown

Each module should be independently testable.

Module	Responsibility
Authentication	Login
Dashboard	Home
SMS	Detection
QR	Scanner
AI	Inference
History	Reports
Settings	Preferences
Notifications	Alerts
Feedback	User Reports
8.7 Backend Module Breakdown

Modules

Authentication

â†“

API

â†“

Feedback

â†“

Analytics

â†“

Model Distribution

â†“

Notification

â†“

Monitoring

8.8 AI Module Breakdown
Data

â†“

Preprocessing

â†“

Feature Engineering

â†“

TinyBERT

â†“

XGBoost

â†“

Isolation Forest

â†“

Risk Engine

â†“

Explainable AI
8.9 Database Implementation
Local Database

Room

Tables

ThreatHistory
Settings
Feedback
SMSAnalysis
QRAnalysis
Cloud Database

PostgreSQL

Tables

Users
Devices
Analytics
Models
AuditLogs
8.10 UI Wireframes
Splash
--------------------

LOGO

Initializing AI...

â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆ

--------------------
Dashboard
-----------------------------------

Security Score

95%

-----------------------------------

Today's Threats

3

-----------------------------------

Safe Messages

25

-----------------------------------

Quick AAuth & Threat Management	âœ…€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
   â”‚                       API Gateway                         â”‚
   â”‚               (FastAPI / REST Controllers)                â”‚
   â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
   â”‚  - Authentication Verification (JWT / OAuth)             â”‚
   â”‚  - Role-Based Access Control (RBAC Middleware)           â”‚
   â”‚  - Input Sanitization & Payload Validation               â”‚
   â”‚  - Threat Processing Engine                             â”‚
   â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                                 â”‚
                                 â–¼
   â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
   â”‚   Centralized Threat Intelligence & Event Database       â”‚
   â”‚                (PostgreSQL / Firestore)                   â”‚
   â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜

Security Rules:
- Mobile Users can read/write ONLY their own detection history records (`userId == token.uid`).
- Administrators can query global threat history, aggregated metrics, and system analytics (`role == ADMIN`).


7.14 REST API Structure & Endpoints

The backend provides the following RESTful API endpoints:

Authentication Endpoints:
- `POST /api/v1/auth/register` : User registration
- `POST /api/v1/auth/login`    : Authenticate and issue JWT
- `POST /api/v1/auth/logout`   : Terminate session token

User Profile Endpoints:
- `GET  /api/v1/users/profile` : Fetch user details
- `PUT  /api/v1/users/profile` : Update profile settings

Threat Detection Endpoints (Mobile App):
- `POST /api/v1/detections/sms`     : Submit SMS analysis telemetry & receive risk score
- `POST /api/v1/detections/qr`      : Submit QR scan telemetry & receive risk score
- `GET  /api/v1/detections/history` : Fetch authenticated user's threat history

Admin Dashboard Endpoints (Admin Only):
- `GET  /api/v1/admin/dashboard`     : Fetch overview metric counters
- `GET  /api/v1/admin/threats`       : Search, filter, and paginate global threat events
- `GET  /api/v1/admin/threats/{id}`  : Fetch detailed breakdown of a specific threat
- `GET  /api/v1/admin/statistics`   : Fetch aggregated analytics time-series data
- `PUT  /api/v1/admin/threats/{id}`  : Update threat status or classification


7.15 System Security & Data Governance Requirements

As a dedicated cybersecurity solution, the platform adheres to rigorous security standards:
1. Zero Plaintext Passwords: Password credentials handled exclusively by bcrypt hashing or Firebase Auth.
2. Mandatory RBAC: Endpoints verify user roles before granting data access.
3. Strict API Validation: Pydantic schemas validate all payload structures.
4. Data Minimization: Minimal private SMS content is persisted.
5. HTTPS Enforcement: All network traffic requires TLS 1.3 encryption.
6. Rate Limiting: Authentication endpoints limited to 10 req/min; standard APIs to 100 req/min; Admin APIs to 500 req/min.
7. Audit Logging: Administrative actions (status changes, user status modifications) produce immutable audit logs.


7.16 Recommended Technology Stack

Component               Technology Selection
Mobile Application      Kotlin, Jetpack Compose, CameraX, Room, Hilt
Mobile AI Inference     ONNX Runtime (TinyBERT, XGBoost, Isolation Forest)
Authentication          Firebase Authentication / JWT Tokens
Backend Framework       Python FastAPI (Asynchronous, High-Performance)
Database Engine         PostgreSQL / Firebase Firestore
Web Admin Dashboard     React.js / Next.js, Tailwind CSS, Chart.js
Deployment & DevOps     Docker, Nginx, GitHub Actions, AWS / Railway


7.17 End-to-End Operational Lifecycle

A complete operational sequence of the system:
1. User registers via the Android mobile application.
2. User authenticates and receives a signed session token.
3. Incoming SMS containing a payment link is received on the user's phone.
4. Mobile application captures SMS via background receiver.
5. Local NLP engine cleans text and extracts features.
6. TinyBERT evaluates text and returns a risk score of 89.
7. Application classifies event as MALICIOUS and displays an explainable alert.
8. Mobile client sends telemetry payload to `POST /api/v1/detections/sms`.
9. API Gateway validates JWT and user permissions.
10. Event record is written to the Centralized Threat Intelligence Database.
11. Security Analyst logs into the Web Security Dashboard.
12. Dashboard authenticates user and verifies `ADMIN` role claim.
13. Admin sees the new threat event on the real-time monitoring table.
14. System-wide metric counters update automatically.
15. Admin inspects threat details (domain, model used, XAI reasons).
16. Admin flags domain for global threat intelligence blacklist.
17. Aggregated threat trend charts reflect the new incident.


7.18 Conceptual Terminology & Database Naming

To maintain professional cybersecurity standards, the central database is designated as:
"Centralized Threat Intelligence and Security Event Database"

Credentials and authentication tokens are managed by dedicated Identity Providers (Firebase Auth / Auth Service), while the central database strictly maintains user profile metadata, security telemetry, detection logs, and threat intelligence metrics.