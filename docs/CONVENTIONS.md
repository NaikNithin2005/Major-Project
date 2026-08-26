# Development & Coding Conventions

Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System.

## 1. Git Branching Strategy
- `main` / `master` - Production-ready, stable codebase. Direct commits forbidden.
- `develop` - Integration branch for ongoing phase implementation.
- Feature Branches: `feature/<phase-number>-<short-description>` (e.g. `feature/p0-foundation-setup`, `feature/p1-android-auth`)
- Bugfix Branches: `fix/<issue-description>`

## 2. Commit Message Conventions
Commits must follow Conventional Commits standard:
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Formatting, whitespace changes (no logic changes)
- `refactor`: Code restructuring without API or feature changes
- `test`: Adding or updating tests
- `chore`: Build task, dependency, or config updates

Format:
```text
<type>(<scope>): <short summary>

[optional body explaining details]
```
Example: `feat(android): configure Kotlin 2.2 and Compose foundation`

## 3. Coding Conventions

### Android (Kotlin)
- Clean Architecture + MVVM layer separation (Presentation, ViewModel, Domain, Repository, Data).
- UI built exclusively with Jetpack Compose & Material 3.
- Access local database via Room repositories; never call Room directly from Composables.
- Asynchronous operations managed with Kotlin Coroutines and StateFlow.
- Dedicated service abstraction for ONNX Runtime model inference.

### Backend (Python / FastAPI)
- Strict validation using Pydantic models.
- Async request handlers with explicit HTTP error status codes.
- Security-by-design: Firebase token verification, RBAC authorization, TLS.
- Database access mediated strictly through SQLAlchemy ORM.

### AI Subsystem (Python)
- Model training & ONNX export pipelines isolated in `ai/`.
- No raw SMS or secret credentials exposed in datasets or logs.
- Evaluation against standardized metrics (Accuracy >=95%, False Positive <=3%).

### Web Admin Dashboard (TypeScript / Next.js)
- TypeScript strict mode enabled.
- Modular React components styled with Tailwind CSS.
- Communication strictly via backend REST endpoints (`/api/v1/admin/*`).

## 4. Security & Privacy Rules
- Secrets must **never** be committed to Git. Use environment variables (`.env`).
- Mobile client must operate **offline-first**; backend calls must never block local threat evaluation.
- Android must never connect directly to PostgreSQL.
