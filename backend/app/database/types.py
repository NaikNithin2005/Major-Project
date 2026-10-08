from sqlalchemy import JSON
from sqlalchemy.dialects.postgresql import JSONB

# Uses native JSONB on PostgreSQL and JSON on SQLite/other dialects
JSONBCompat = JSON().with_variant(JSONB, "postgresql")
