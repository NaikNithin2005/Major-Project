# Memory.md — Project Memory & Decision Log

This document serves as the persistent memory of the Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System. It records architectural decisions, completed work, changes, bug fixes, dependency updates, and project state.

---

## 2026-08-24 — Initialization of Project Governance & Persistent Memory

### Type
Documentation / System Setup

### Change
- Initialized `Memory.md` as the persistent project development memory.
- Completed comprehensive review and verification of mandatory documentation source-of-truth files:
  - `PRD.md` (Product Requirements Document)
  - `Architecture.md` (System Architecture & Subsystems)
  - `Design.md` (UI/UX & Visual Rules)
  - `Phases.md` (Implementation Roadmap & Milestones)
  - `Rules.md` (Development Guidelines, Security & AI Boundaries)
  - `Memory.md` (Persistent Memory Log)
- Inspected workspace repository structure:
  - `APP/` — Android Application shell (Kotlin/Gradle, MVVM + Clean Architecture)
  - `Datasets/` — Dataset files (`Phishing URLs.csv`, `URL dataset.csv`, `spam_ham_india.csv`)
  - `Doc/` — Project reports, presentations, and design diagrams

### Reason
Established mandatory memory tracking and governance guidelines as specified in the Project Documentation & Memory Management Instructions.

### Files Affected
- `Memory.md`

### Previous Behavior
`Memory.md` did not exist in the root workspace directory.

### New Behavior
`Memory.md` is now initialized and will be maintained continuously after every modification, feature implementation, refactoring, or architectural change.

### Impact
Ensures all future AI development agents maintain strict consistency, prevent hallucinations, respect documented architectural rules, and track progress without relying solely on chat history.

### Related Documentation
- `PRD.md`
- `Architecture.md`
- `Design.md`
- `Rules.md`
- `Phases.md`
- `Memory.md`

### Status
Completed

### Important Notes
- Core detection must remain offline-first (on-device inference via ONNX Runtime using TinyBERT for SMS and XGBoost for URLs).
- Android app uses Kotlin, Jetpack Compose, Material 3, Clean Architecture + MVVM, Hilt, Room, DataStore, CameraX, and ZXing.
- Backend (FastAPI + PostgreSQL + Firebase Auth) and Web Admin Dashboard (React/Next.js + Tailwind CSS) are decoupled extensions.
