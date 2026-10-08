import os
from typing import List, Optional, Union
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Squish Shield Backend API"
    version: str = "1.0.0"
    environment: str = Field(default="development", validation_alias="ENVIRONMENT")
    log_level: str = Field(default="INFO", validation_alias="LOG_LEVEL")
    api_prefix: str = Field(default="/api", validation_alias="API_PREFIX")

    # Database
    database_url: str = Field(
        default="postgresql://postgres:postgres_dev_password@localhost:5432/quishing_smishing_db",
        validation_alias="DATABASE_URL",
    )
    test_database_url: str = Field(
        default="sqlite:///:memory:",
        validation_alias="TEST_DATABASE_URL",
    )
    db_pool_size: int = Field(default=10, validation_alias="DB_POOL_SIZE")
    db_max_overflow: int = Field(default=20, validation_alias="DB_MAX_OVERFLOW")

    # Security & Authentication
    jwt_secret_key: str = Field(
        default="dev-jwt-secret-key-squish-shield-phase7-secure-token-32-chars",
        validation_alias="JWT_SECRET_KEY",
    )
    jwt_algorithm: str = "HS256"
    firebase_project_id: str = Field(default="squish-shield", validation_alias="FIREBASE_PROJECT_ID")
    firebase_credentials_path: Optional[str] = Field(default=None, validation_alias="FIREBASE_CREDENTIALS")
    allow_mock_auth: bool = Field(default=True, validation_alias="ALLOW_MOCK_AUTH")
    admin_emails: str = Field(
        default="nithinnaikr2005@gmail.com,admin@squishshield.local,admin@shield.ai",
        validation_alias="ADMIN_EMAILS",
    )

    # CORS
    cors_origins: Union[List[str], str] = [
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3001",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
    ]

    # Rate Limiting (requests per minute)
    rate_limit: int = Field(default=120, validation_alias="RATE_LIMIT")
    rate_limit_admin: int = Field(default=240, validation_alias="RATE_LIMIT_ADMIN")

    @property
    def admin_emails_list(self) -> List[str]:
        return [e.strip().lower() for e in self.admin_emails.split(",") if e.strip()]

    @property
    def cors_origins_list(self) -> List[str]:
        if isinstance(self.cors_origins, list):
            return self.cors_origins
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def rate_limit_per_minute(self) -> int:
        return self.rate_limit

    model_config = SettingsConfigDict(
        env_file=(".env", "backend/.env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
