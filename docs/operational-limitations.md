# Known Operational Limitations, Boundaries & Environmental Constraints

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Public / System Engineering  

---

## 1. Overview

This document provides transparent, verified boundaries, edge constraints, and operational limitations of the **Squish Shield** system in production environments. Recognizing these operational boundaries prevents unrealistic performance expectations and guides field troubleshooting.

---

## 2. Edge & Mobile Inference Boundaries

### 2.1 Hardware & Camera Requirements
- **Camera Resolution:** QR scanning requires a minimum 720p (1280x720) rear camera with autofocus. Low-contrast or scratched camera lenses degrade QR code extraction latency by up to 200ms.
- **Physical Memory (RAM):**
  - Minimum device RAM: 3 GB.
  - TinyBERT ONNX model requires ~58 MB resident memory during active tokenization and inference.
  - On devices with < 2 GB free RAM, Android low-memory killer (LMK) may terminate background SMS listeners.

### 2.2 Inference Latency & Battery Impact
- **Inference Speed:**
  - TinyBERT SMS inference executes in 4.77ms (median p50) on desktop CPU and ~15–25ms on modern ARM64 mobile chipsets (Snapdragon 7/8 series, Tensor).
  - XGBoost URL inference executes in 0.02ms (p50).
- **Battery Optimization:**
  - Real-time SMS interception runs on an event-driven `BroadcastReceiver`. It does **not** poll or consume background CPU when no SMS is arriving.
  - QR scanning continuously analyzes video preview frames only while the QR scanner activity is actively displayed in the foreground.

---

## 3. Network & Synchronization Boundaries

### 3.1 Offline-First Resilience
- **Zero Network Dependency for Protection:** Threat detection operates 100% locally on the device using bundled ONNX models. Lack of internet connectivity has zero impact on threat blocking or warning dialogs.
- **Queue Limits:**
  - The Android Room database queues unsynced threat events indefinitely until network connectivity is restored.
  - Queue batching is bounded to 50 events per HTTP POST request to avoid HTTP 413 (Payload Too Large) errors.
  - WorkManager enforces exponential retry backoff (1m, 2m, 4m, 8m, capped at 1 hour) to preserve device battery during prolonged network outages.

### 3.2 Account Switching Boundaries
- If multiple users share an Android device and log out/log in with different accounts:
  - Local database rows record the active `user_id` when the threat was detected.
  - The offline sync service strictly verifies that events uploaded match the currently authenticated token's UID.
  - Events recorded under Account A cannot be synced under Account B.

---

## 4. Backend & Database Concurrency Boundaries

### 4.1 SQLite Fallback vs. PostgreSQL in Production
- **SQLite Single-Writer Lock:**
  - In local development or isolated edge nodes without a running PostgreSQL daemon, the backend falls back to SQLite WAL mode (`squish_shield_dev.db`).
  - While read latency is negligible (< 10ms), SQLite serializes all write operations. Concurrency tests indicate write contention begins when more than 10 simultaneous workers attempt batch writes.
  - **Production Requirement:** PostgreSQL 15+ with connection pooling (e.g., PgBouncer or SQLAlchemy Pool size 10, max overflow 20) must be deployed for production multi-user scale.

### 4.2 Port Allocation Constraints
- Default FastAPI configuration specifies port `8001` (`PORT=8001`) to avoid collisions on systems running enterprise observability tools (e.g., Splunk Enterprise or other services defaulting to port 8000).

---

## 5. Security & Privacy Non-Negotiables

### 5.1 Zero Raw SMS Ingestion
- In accordance with data minimization principles, the backend API schema does **not** possess a field for SMS text content.
- Telemetry payloads strictly transmit:
  - `threat_type` ("SMS" or "QR")
  - `sender` (e.g. "+18005550199" or alphanumeric header)
  - `verdict` ("BENIGN", "SUSPICIOUS", "MALICIOUS")
  - `risk_score` (0–100 integer)
  - `sha256_hash` (anonymized payload fingerprint)
  - `extracted_urls` (if any URL was present)
- It is architecturally impossible for backend logs or database records to leak personal conversational SMS bodies.

### 5.2 Model Retraining Safeguards
- The system deliberately **lacks** an automated online learning / auto-retraining pipeline. Models are updated exclusively via the controlled, cryptographically verified MLOps release process detailed in `docs/model-lifecycle.md`.
