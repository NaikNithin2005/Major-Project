# Backend Subsystem (FastAPI)

Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System.

## Architecture
- **Framework**: FastAPI (Python 3.11+)
- **Database**: PostgreSQL with SQLAlchemy ORM & Alembic migrations
- **Authentication**: Firebase Authentication Token Verification
- **Containerization**: Docker & Docker Compose

## Phase 0 Foundation Setup

### Local Setup
1. Create a Python virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Copy environment template:
   ```bash
   cp .env.example .env
   ```

### Running Locally
```bash
uvicorn app.main:app --reload --port 8000
```
Visit API documentation at `http://localhost:8000/docs`.

### Running Tests
```bash
pytest
```
