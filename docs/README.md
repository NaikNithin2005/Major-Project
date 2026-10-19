# Project Documentation Index

**Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System**  
**Release:** v1.0.0 (Phase 12 Complete)

---

## 1. Release & Architecture Specifications
- [`RELEASE_NOTES_v1.0.0.md`](./RELEASE_NOTES_v1.0.0.md) — Official v1.0.0 Release Notes, Checksums & Changelog.
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — Subsystem Architecture, Edge-to-Cloud Protocols & Security Boundaries.
- [`API_REFERENCE.md`](./API_REFERENCE.md) — Complete FastAPI REST API Specification, RBAC & Request/Response Schemas.
- [`DATABASE_GUIDE.md`](./DATABASE_GUIDE.md) — PostgreSQL Schema, Alembic Migrations (001-003), Backup & Disaster Recovery.
- [`MODEL_CARDS_AND_VERSIONING.md`](./MODEL_CARDS_AND_VERSIONING.md) — TinyBERT, XGBoost, and Isolation Forest Specs, Checksums & Metrics.
- [`SECURITY_AND_PRIVACY_GUIDE.md`](./SECURITY_AND_PRIVACY_GUIDE.md) — Data Minimization (No Raw SMS to Cloud), Token Hardening & RBAC.
- [`TESTING_GUIDE.md`](./TESTING_GUIDE.md) — Automated Test Suites, Pytest Commands, Android Tests & Web Builds.
- [`USER_GUIDE.md`](./USER_GUIDE.md) — End-User Mobile Application Manual (SMS Monitoring, QR Scanning, Actions).
- [`ADMIN_GUIDE.md`](./ADMIN_GUIDE.md) — SOC Operations Manual, Threat Triage, Device Auditing & User Management.
- [`DEPLOYMENT_AND_INSTALLATION_GUIDE.md`](./DEPLOYMENT_AND_INSTALLATION_GUIDE.md) — Reproducible Local, Staging & Docker Deployment Instructions.

---

## 2. Phase-by-Phase Milestone Reports
- [`phase11_performance_and_uat_report.md`](./phase11_performance_and_uat_report.md) — Integration, Performance & UAT Results.
- [`phase8_auth_rbac_sync_report.md`](./phase8_auth_rbac_sync_report.md) — Firebase Authentication, RBAC & Offline Sync Report.
- [`phase7_backend_postgresql_report.md`](./phase7_backend_postgresql_report.md) — FastAPI & PostgreSQL Telemetry Service Report.
- [`phase6_risk_engine_report.md`](./phase6_risk_engine_report.md) — Multi-Stage Risk Engine & Explainability Report.
- [`phase6_integration_stabilization_report.md`](./phase6_integration_stabilization_report.md) — Clean Architecture & Stabilization Report.
- [`phase5_model_evaluation_report.md`](./phase5_model_evaluation_report.md) — Model Training & Evaluation Report.
- [`CONVENTIONS.md`](./CONVENTIONS.md) — Git Branching, Commit Conventions & Coding Guidelines.

---

## 3. Project Foundations
- [`PRD.md`](../PRD.md) — Product Requirements Document.
- [`Architecture.md`](../Architecture.md) — Master Architectural Specification.
- [`Design.md`](../Design.md) — UI/UX Design System & Theme Guidelines.
- [`Rules.md`](../Rules.md) — Development Rules & Security Constraints.
- [`Phases.md`](../Phases.md) — Project Roadmap.
- [`Memory.md`](../Memory.md) — Persistent Project Decision Log.

---

## 4. Phase 13 — Post-Release Monitoring & Maintenance Extension Guides
- [`monitoring.md`](./monitoring.md) — Observability, Health/Readiness Probes, Correlation IDs & SOC Admin Telemetry.
- [`model-lifecycle.md`](./model-lifecycle.md) — MLOps Protocol, Cryptographic Model Manifest, Benchmark Scripts & Update Pipeline.
- [`incident-response.md`](./incident-response.md) — 11 Scenario Runbooks (Auth bypass, data leak, model tampering, outages).
- [`backup-recovery.md`](./backup-recovery.md) — PostgreSQL / SQLite Backup Schedules, Integrity Verification & Restore Protocols.
- [`maintenance-runbook.md`](./maintenance-runbook.md) — Maintenance Schedules, Index Tuning, Connection Pooling & Security Audits.
- [`operational-limitations.md`](./operational-limitations.md) — Hardware, Edge Memory, Battery, Concurrency Bounds & Data Minimization.
- [`phase13_model_evaluation_report.json`](./phase13_model_evaluation_report.json) — Certified Evaluation Benchmark Metrics (Accuracy, F1, Recall, Latency).

---

## 5. Phase 14 — Advanced Threat Intelligence & Adaptive Detection Extension Guides
- [`threat-intelligence.md`](./threat-intelligence.md) — Threat Intelligence Architecture, Feeds (URLhaus, PhishTank, GSB, Internal), Normalization & Provenance.
- [`url-reputation.md`](./url-reputation.md) — Safe URL Canonicalization, Punycode IDN Conversion, SSRF Protection, Privacy Query Scrubbing & Status Machine.
- [`model-improvement.md`](./model-improvement.md) — Controlled Multi-Gate Model Promotion Workflow, Governance, Manifest Sign-Off & Rollback Protocols.
- [`detection-evaluation.md`](./detection-evaluation.md) — Emerging-Threat Evaluation Benchmark (+50% Recall Lift, Zero False-Positive Inflation, Latency Metrics).
- [`intelligence-privacy.md`](./intelligence-privacy.md) — Privacy-Preserving Architecture, Zero Raw SMS Logging, Provider Secret Isolation & Data Minimization.
- [`phase14_threat_intel_evaluation_report.json`](./phase14_threat_intel_evaluation_report.json) — Certified Emerging-Threat Evaluation Benchmark Results.

---

## 6. Phase 15 — Advanced SOC Dashboard, Incident Response & Threat Investigation Guides
- [`incident-management.md`](./incident-management.md) — Incident Management Architecture, Lifecycle State Machine, Transition Rules, RBAC, and Decoupled Schema.
- [`incident-correlation.md`](./incident-correlation.md) — Threat Correlation Engine, Conservative Provenance, Matching Signals (`MATCHING_URL`, `MATCHING_DOMAIN`, `MATCHING_CAMPAIGN`), and Confidence Levels.
- [`incident-response-runbook.md`](./incident-response-runbook.md) — Standard Operating Procedures (SOP) for SOC Analysts, Triage Matrix, Containment Protocols, and Post-Incident Verification.
- [`soc-dashboard.md`](./soc-dashboard.md) — SOC Web Operations Dashboard Architecture, Incident Queue, Workstation Dossier, Evidence View, and Dark-Mode Visual Identity.
- [`incident-metrics.md`](./incident-metrics.md) — Incident Metrics Definitions (MTTT, MTTR, Workload Breakdowns, Trends), Formulates, Clock Skew Defenses, and Edge Case Handling.

---

## 7. Phase 17 — Continuous Integration, Automated Quality Gates & Secure Delivery (DevSecOps)
- [`DEVSECOPS_PLAN.md`](../DEVSECOPS_PLAN.md) — Master DevSecOps Architecture, Multi-Stage CI/CD Topology, Quality Gates & Gated Rollout Protocols.
- [`CI_CD_SETUP.md`](../CI_CD_SETUP.md) — Step-by-Step GitHub Actions Setup, Environment Secrets (`staging`/`production`), Branch Protections & Local Developer Verification.
- [`SECURITY_SCANNING.md`](../SECURITY_SCANNING.md) — Automated Secret Scanning, SAST (Bandit), Dependency Audits (`pip-audit`, `npm audit`), Container Hardening & Remediation SLAs.
- [`RELEASE_RUNBOOK.md`](../RELEASE_RUNBOOK.md) — Release Engineering Standard Operating Procedure, Semantic Versioning Protocol, Smoke Testing & Rollback SOPs.
- [`FINAL_ACCEPTANCE_REPORT.md`](../FINAL_ACCEPTANCE_REPORT.md) — Comprehensive Master Production Readiness & Requirements Traceability Matrix.



