import logging
from typing import Generator
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, Session
from app.config import settings

logger = logging.getLogger(__name__)

def init_engine():
    db_url = settings.database_url
    if db_url.startswith("sqlite"):
        connect_args = {"check_same_thread": False}
        return create_engine(db_url, connect_args=connect_args, echo=False)

    try:
        eng = create_engine(
            db_url,
            pool_size=settings.db_pool_size,
            max_overflow=settings.db_max_overflow,
            pool_pre_ping=True,
            echo=False,
            connect_args={"connect_timeout": 2} if "postgresql" in db_url else {}
        )
        with eng.connect() as conn:
            conn.execute(text("SELECT 1"))
        return eng
    except Exception as e:
        logger.warning(
            f"Primary database ({db_url}) unreachable ({e}). "
            "Falling back to local SQLite database (sqlite:///./squish_shield_dev.db)."
        )
        return create_engine(
            "sqlite:///./squish_shield_dev.db",
            connect_args={"check_same_thread": False},
            echo=False
        )


engine = init_engine()
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def ensure_tables():
    """Ensure database tables are provisioned and initial seed data exists in development."""
    try:
        import app.models  # noqa: F401
        from app.database.base import Base
        Base.metadata.create_all(bind=engine)

        # Non-destructive schema updates for existing tables
        with engine.connect() as conn:
            for sql in [
                "ALTER TABLE users ADD COLUMN phone_number VARCHAR(32)",
                "ALTER TABLE threat_events ADD COLUMN user_action VARCHAR(32)",
                "ALTER TABLE threat_events ADD COLUMN updated_at TIMESTAMP",
            ]:
                try:
                    conn.execute(text(sql))
                    conn.commit()
                except Exception:
                    pass

        if settings.environment == "development":
            from app.database.seed import seed_initial_data
            with SessionLocal() as db:
                seed_initial_data(db)
    except Exception as err:
        logger.warning(f"Could not auto-create database tables or seed data: {err}")




def get_db() -> Generator[Session, None, None]:
    """
    FastAPI dependency yielding a managed SQLAlchemy database session.
    Automatically commits on normal exit and rolls back on exception.
    """
    db = SessionLocal()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def check_db_health() -> bool:
    """Check database connectivity with a lightweight ping query."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception as e:
        logger.warning(f"Database health check failed: {e}")
        return False
