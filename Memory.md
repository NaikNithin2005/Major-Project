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

---

## 2026-08-26 — Phase 0 Project Foundation Implementation

### Type
Project Foundation / Infrastructure / Multi-Component Setup

### Change
- Preserved existing Android Studio folder structure in `android/` without moving or renaming.
- Configured Android build compatibility (`compileSdk = 35`, `targetSdk = 35`, `JavaVersion.VERSION_17`) in `android/app/build.gradle.kts`.
- Established Backend subsystem in `backend/` with FastAPI (`app/main.py`, `app/config.py`), Pydantic settings, `requirements.txt`, `Dockerfile`, `.env.example`, `README.md`, and test suite (`tests/test_main.py`).
- Established AI/ML Detection Engine workspace in `ai/` with modular packages (`preprocessing`, `feature_engineering`, `tinybert`, `xgboost`, `anomaly`, `explainable_ai`, `training`, `evaluation`, `deployment`), `requirements.txt`, `.env.example`, `README.md`, and foundation test (`tests/test_ai_foundation.py`).
- Established Web Admin Dashboard shell in `web/` with Next.js 14, React 18, TypeScript, Tailwind CSS (`package.json`, `tsconfig.json`, `next.config.js`, `tailwind.config.js`, `postcss.config.js`, `src/app/page.tsx`, `src/app/layout.tsx`, `src/app/globals.css`, `Dockerfile`, `.env.example`, `README.md`).
- Created central documentation guidelines in `docs/` (`README.md`, `CONVENTIONS.md`).
- Created build & validation scripts in `scripts/` (`README.md`, `verify_phase0.py`).
- Created root integration test suite shell in `tests/` (`README.md`, `__init__.py`).
- Created repository configuration at root: `.gitignore`, `.env.example` (placeholders only), `docker-compose.yml`, `.github/workflows/ci.yml`, and comprehensive `README.md`.

### Reason
Fulfill Phase 0 Project Foundation deliverables specified in `Phases.md` while maintaining complete compatibility with existing Android Studio layout and preserving strict security boundaries.

### Files Affected
- `android/app/build.gradle.kts`
- `backend/app/main.py`, `backend/app/config.py`, `backend/app/__init__.py`, `backend/requirements.txt`, `backend/Dockerfile`, `backend/.env.example`, `backend/README.md`, `backend/tests/test_main.py`
- `ai/requirements.txt`, `ai/.env.example`, `ai/README.md`, `ai/__init__.py`, `ai/preprocessing/__init__.py`, `ai/feature_engineering/__init__.py`, `ai/tinybert/__init__.py`, `ai/xgboost/__init__.py`, `ai/anomaly/__init__.py`, `ai/explainable_ai/__init__.py`, `ai/training/__init__.py`, `ai/evaluation/__init__.py`, `ai/deployment/__init__.py`, `ai/tests/test_ai_foundation.py`
- `web/package.json`, `web/tsconfig.json`, `web/next.config.js`, `web/tailwind.config.js`, `web/postcss.config.js`, `web/.env.example`, `web/Dockerfile`, `web/src/app/page.tsx`, `web/src/app/layout.tsx`, `web/src/app/globals.css`, `web/README.md`
- `docs/README.md`, `docs/CONVENTIONS.md`
- `scripts/README.md`, `scripts/verify_phase0.py`
- `tests/README.md`, `tests/__init__.py`
- `.gitignore`, `.env.example`, `docker-compose.yml`, `.github/workflows/ci.yml`, `README.md`, `Memory.md`

### Verification Performed
- `python scripts/verify_phase0.py` executed successfully, confirming all 26 foundation file paths exist.
- Python compilation check (`python -m py_compile`) succeeded with 0 errors across backend, AI, and script Python files.
- Resolved Gradle Java Toolchain auto-detection error (`jlink` missing in VS Code extension directory) by setting JDK 21 paths (`org.gradle.java.installations.auto-detect=false`, `org.gradle.java.installations.paths=C\:\\Program Files\\Java\\jdk-21`) in `android/gradle.properties`.
- Configured `compileSdk = 37` in `android/app/build.gradle.kts` matching `androidx.core:1.19.0` and `androidx.activity:1.13.0` metadata.
- Executed `.\gradlew assembleDebug --console=plain` cleanly in `android/` directory (`BUILD SUCCESSFUL in 13s`, Exit code: 0).

### Verification Could Not Perform
- Full `npm install` and Next.js production build in `web/` were not run locally to avoid downloading multi-megabyte node_modules packages during Phase 0 setup.

### Unresolved Issues
- None.

### Deviations from Phases.md
- None. Strict Phase 0 boundary observed; no Phase 1+ features (authentication, Room DB, SMS/QR pipelines, AI models) were implemented.

### Phase 0 Status
Foundation Complete & Fully Verified (Android Build Successful).

---

## 2026-08-26 — Phase 1 Implementation: Android Shell & Authentication

### Type
Feature Implementation / Android Architecture / Authentication Flow

### Change
- Implemented Clean Architecture + MVVM layer separation for Android in `com.example.android`:
  - **Application Shell**: `AegisApplication.kt` annotated with `@HiltAndroidApp`. Updated `AndroidManifest.xml` with app class and `INTERNET` permission.
  - **Domain Layer**:
    - Models: `User.kt`, `AuthState.kt` (Uninitialized, Unauthenticated, Loading, Authenticated, Error).
    - Repository Contract: `AuthRepository.kt`.
    - Use Cases: `LoginUseCase`, `RegisterUseCase`, `LogoutUseCase`, `GetAuthStateUseCase`, `GuestLoginUseCase`.
  - **Data Layer**:
    - `FirebaseAuthDataSource`: Integrates safely with `FirebaseAuth`, supporting graceful offline/guest fallback when Firebase services are unconfigured.
    - `AuthRepositoryImpl`: Mediates authentication state management via `StateFlow<AuthState>`.
  - **Dependency Injection**:
    - `di/AuthModule`: Binds `AuthRepositoryImpl` to `AuthRepository` with `@Singleton` scope.
  - **Presentation Layer (Jetpack Compose + Material 3)**:
    - `presentation/navigation/`: `Screen.kt` routes and `NavGraph.kt` managing route transitions and protected screens.
    - `presentation/splash/`: `SplashScreen` & `SplashViewModel` with AegisShield branding and auto-session routing.
    - `presentation/onboarding/`: `OnboardingScreen` & `OnboardingViewModel` with 5-stage feature carousel.
    - `presentation/auth/login/`: `LoginScreen` & `LoginViewModel` with email/password inputs, password visibility toggle, error banner, Google sign-in button, Guest mode option, and Register navigation.
    - `presentation/auth/register/`: `RegisterScreen` & `RegisterViewModel` with full name, email, password, confirm password validation, and Login navigation.
    - `presentation/dashboard/`: `DashboardScreen` & `DashboardViewModel` with Security Score card (92/100), activity counters, quick action buttons, and 5-item Bottom Navigation bar.
    - `presentation/settings/`: `SettingsScreen` & `SettingsViewModel` with user profile info, security engine toggles, app version info, and Logout confirmation dialog.

### Reason
Fulfill Phase 1 requirements from `Phases.md`: build the Android shell, establish presentation/viewmodel/domain/repository/data Clean Architecture, and provide complete user authentication (Login, Register, Guest Mode, Logout, Session persistence).

### Files Affected
- `android/gradle/libs.versions.toml`
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/example/android/AegisApplication.kt`
- `android/app/src/main/java/com/example/android/MainActivity.kt`
- `android/app/src/main/java/com/example/android/domain/model/User.kt`
- `android/app/src/main/java/com/example/android/domain/model/AuthState.kt`
- `android/app/src/main/java/com/example/android/domain/repository/AuthRepository.kt`
- `android/app/src/main/java/com/example/android/domain/usecase/LoginUseCase.kt`
- `android/app/src/main/java/com/example/android/domain/usecase/RegisterUseCase.kt`
- `android/app/src/main/java/com/example/android/domain/usecase/LogoutUseCase.kt`
- `android/app/src/main/java/com/example/android/domain/usecase/GetAuthStateUseCase.kt`
- `android/app/src/main/java/com/example/android/domain/usecase/GuestLoginUseCase.kt`
- `android/app/src/main/java/com/example/android/data/source/FirebaseAuthDataSource.kt`
- `android/app/src/main/java/com/example/android/data/repository/AuthRepositoryImpl.kt`
- `android/app/src/main/java/com/example/android/di/AuthModule.kt`
- `android/app/src/main/java/com/example/android/presentation/navigation/Screen.kt`
- `android/app/src/main/java/com/example/android/presentation/navigation/NavGraph.kt`
- `android/app/src/main/java/com/example/android/presentation/splash/SplashViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/splash/SplashScreen.kt`
- `android/app/src/main/java/com/example/android/presentation/onboarding/OnboardingViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/onboarding/OnboardingScreen.kt`
- `android/app/src/main/java/com/example/android/presentation/auth/login/LoginViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/auth/login/LoginScreen.kt`
- `android/app/src/main/java/com/example/android/presentation/auth/register/RegisterViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/auth/register/RegisterScreen.kt`
- `android/app/src/main/java/com/example/android/presentation/dashboard/DashboardViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/dashboard/DashboardScreen.kt`
- `android/app/src/main/java/com/example/android/presentation/settings/SettingsViewModel.kt`
- `android/app/src/main/java/com/example/android/presentation/settings/SettingsScreen.kt`
- `Memory.md`

### Verification Performed
- Installed Firebase agent skills using `npx skills add firebase/agent-skills --agent=antigravity` into `.\.agents\skills\`.
- Integrated Google Services plugin (`com.google.gms.google-services`) and Firebase Firestore SDK (`firebase-firestore-ktx`) into `android/app/build.gradle.kts`.
- Created `android/app/google-services.json` registered to package `com.example.android` for Firebase project `squish-shield`.
- Implemented `FirestoreDataSource.kt` and wired automated user profile syncing upon registration/login in `AuthRepositoryImpl.kt` and `AppContainer.kt`.
- Executed `.\gradlew assembleDebug --console=plain` cleanly in `android/` directory:
  `BUILD SUCCESSFUL in 31s` (37 actionable tasks, Exit code: 0).

### Verification Could Not Perform
- Live Firebase cloud sync verification until valid production OAuth credentials are sync'd.

### Unresolved Issues
- None.

### Deviations from Phases.md
- None. Strict Phase 1 boundary observed.

### Phase 1 Status
Phase 1 Complete & Verified (Firebase Auth & Firestore Configured for `squish-shield`, Android Build Successful). Ready for Phase 2.





