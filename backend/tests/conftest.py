import os
import pytest
from datetime import datetime, timezone
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from fastapi.testclient import TestClient

# Ensure test environment
os.environ["ENVIRONMENT"] = "testing"
os.environ["DATABASE_URL"] = "sqlite:///:memory:"

from app.database.base import Base
from app.database.session import get_db
import app.models  # Register all models with Base.metadata
from app.models.user import User
from app.main import app

# Create in-memory SQLite engine for tests
test_engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


@pytest.fixture(scope="session", autouse=True)
def setup_test_db():
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)


@pytest.fixture
def db_session():
    """Provides a transactional database session for each test."""
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


@pytest.fixture
def client(db_session):
    """FastAPI TestClient with overridden get_db dependency."""
    def override_get_db():
        try:
            yield db_session
            db_session.commit()
        except Exception:
            db_session.rollback()
            raise

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture
def test_user(db_session) -> User:
    user = db_session.query(User).filter(User.firebase_uid == "user-mock-uid-0001").first()
    if not user:
        user = User(
            firebase_uid="user-mock-uid-0001",
            email="user@squishshield.local",
            display_name="Standard User",
            role="USER",
            is_active=True,
            last_seen_at=datetime.now(timezone.utc)
        )
        db_session.add(user)
        db_session.commit()
        db_session.refresh(user)
    return user


@pytest.fixture
def test_user2(db_session) -> User:
    user = db_session.query(User).filter(User.firebase_uid == "user-mock-two").first()
    if not user:
        user = User(
            firebase_uid="user-mock-two",
            email="user_two@squishshield.local",
            display_name="User Two",
            role="USER",
            is_active=True,
            last_seen_at=datetime.now(timezone.utc)
        )
        db_session.add(user)
        db_session.commit()
        db_session.refresh(user)
    return user


@pytest.fixture
def test_admin(db_session) -> User:
    admin = db_session.query(User).filter(User.firebase_uid == "admin-mock-uid-0001").first()
    if not admin:
        admin = User(
            firebase_uid="admin-mock-uid-0001",
            email="admin@squishshield.local",
            display_name="System Administrator",
            role="ADMIN",
            is_active=True,
            last_seen_at=datetime.now(timezone.utc)
        )
        db_session.add(admin)
        db_session.commit()
        db_session.refresh(admin)
    return admin


@pytest.fixture
def user_headers(test_user) -> dict:
    return {"Authorization": "Bearer mock-user-token"}


@pytest.fixture
def user2_headers(test_user2) -> dict:
    return {"Authorization": "Bearer mock-user-two"}


@pytest.fixture
def admin_headers(test_admin) -> dict:
    return {"Authorization": "Bearer mock-admin-token"}
