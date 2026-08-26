import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    app_name: str = "Quishing & Smishing Detection System Backend"
    version: str = "0.1.0"
    environment: str = os.getenv("ENVIRONMENT", "development")
    log_level: str = os.getenv("LOG_LEVEL", "INFO")
    
    postgres_host: str = os.getenv("POSTGRES_HOST", "localhost")
    postgres_port: int = int(os.getenv("POSTGRES_PORT", 5432))
    postgres_db: str = os.getenv("POSTGRES_DB", "quishing_smishing_db")
    postgres_user: str = os.getenv("POSTGRES_USER", "postgres")
    postgres_password: str = os.getenv("POSTGRES_PASSWORD", "")
    
    class Config:
        env_file = ".env"

settings = Settings()
