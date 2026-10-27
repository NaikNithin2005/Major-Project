import logging
import sqlite3
from typing import Generator, Dict, Any
from sqlalchemy import create_engine, text, event, Engine
from sqlalchemy.orm import sessionmaker, Session
from app.config import settings

logger = logging.getLogger(__name__)


@event.listens_for(Engine, "connect")
def configure_sqlite_connection(dbapi_connection, connection_record):
    """
    Configure high-concurrency PRAGMAs for SQLite connections:
    - WAL mode: Readers do not block writers, and writers do not block readers.
    - synchronous=NORMAL: Eliminates excessive disk flushes during transactions.
    - busy_timeout=30000: Queues concurrent writes for up to 30 seconds rather than failing immediately.
    - foreign_keys=ON: Enforces relational constraints.
    - cache_size=-64000: Allocates 64MB memory page cache.
    - temp_store=MEMORY: Keeps temp tables and indices in memory.
    """
    if isinstance(dbapi_connection, sqlite3.Connection):
        cursor = dbapi_connection.cursor()
        try:
            cursor.execute("PRAGMA journal_mode=WAL")
            cursor.execute("PRAGMA synchronous=NORMAL")
            cursor.execute("PRAGMA busy_timeout=30000")
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.execute("PRAGMA cache_size=-64000")
            cursor.execute("PRAGMA temp_store=MEMORY")
        except Exception as e:
            logger.debug(f"SQLite PRAGMA setup notice: {e}")
        finally:
            cursor.close()


def init_engine():
    db_url = settings.database_url
    if db_url.startswith("sqlite"):
        connect_args = {"check_same_thread": False, "timeout": 30.0}
        return create_engine(db_url, connect_args=connect_args, echo=False)

    try:
        pg_connect_args = {
            "connect_timeout": 5,
            "keepalives": 1,
            "keepalives_idle": 30,
            "keepalives_interval": 10,
            "keepalives_count": 5,
        } if "postgresql" in db_url else {}

        eng = create_engine(
            db_url,
            pool_size=settings.db_pool_size,
            max_overflow=settings.db_max_overflow,
            pool_timeout=30.0,
            pool_recycle=1800,
            pool_pre_ping=True,
            echo=False,
            connect_args=pg_connect_args
        )
        with eng.connect() as conn:
            conn.execute(text("SELECT 1"))
        logger.info(f"Connected to primary database ({db_url.split('@')[-1] if '@' in db_url else db_url}) with connection pooling.")
        return eng
    except Exception as e:
        logger.warning(
            f"Primary database ({db_url}) unreachable ({e}). "
            "Falling back to local SQLite database in WAL mode (sqlite:///./squish_shield_dev.db)."
        )
        return create_engine(
            "sqlite:///./squish_shield_dev.db",
            connect_args={"check_same_thread": False, "timeout": 30.0},
            echo=False
        )


engine = init_engine()
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db_pool_status() -> Dict[str, Any]:
    """Retrieve connection pool metrics for observability and diagnostics."""
    pool = engine.pool
    is_sqlite = engine.dialect.name == "sqlite"

    def _val(attr_name):
        if not hasattr(pool, attr_name):
            return None
        v = getattr(pool, attr_name)
        return v() if callable(v) else v

    return {
        "dialect": engine.dialect.name,
        "is_sqlite": is_sqlite,
        "wal_enabled": is_sqlite,
        "pool_size": _val("size"),
        "checkedin_connections": _val("checkedin"),
        "checkedout_connections": _val("checkedout"),
        "overflow": _val("overflow"),
    }


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
                "ALTER TABLE threat_intel ADD COLUMN verification_status VARCHAR(32) DEFAULT 'VERIFIED'",
                "ALTER TABLE threat_intel ADD COLUMN expires_at TIMESTAMP",
                "ALTER TABLE threat_intel ADD COLUMN revocation_reason VARCHAR(512)",
                "ALTER TABLE threat_intel ADD COLUMN promoted_from_report_id VARCHAR(36)",
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
