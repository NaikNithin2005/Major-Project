import os
import json
import base64
import logging
from typing import Dict, Any, Optional, Tuple
from datetime import datetime, timezone, timedelta
from jose import jwt, JWTError
from app.config import settings

logger = logging.getLogger(__name__)

firebase_initialized = False
firebase_init_mode = "NONE"


def resolve_firebase_credentials() -> Tuple[Optional[Dict[str, Any]], str]:
    """
    Detect Firebase Service Account credentials from multiple production-friendly sources:
    1. FIREBASE_CREDENTIALS / FIREBASE_CREDENTIALS_JSON as inline JSON string (e.g., from container secret managers).
    2. FIREBASE_CREDENTIALS_BASE64 as base64-encoded JSON string.
    3. File path specified in FIREBASE_CREDENTIALS or settings.firebase_credentials_path.
    4. Default file locations (e.g. firebase-service-account.json).
    """
    # 1. Inline JSON string from environment variables
    for env_var in ["FIREBASE_CREDENTIALS", "FIREBASE_CREDENTIALS_JSON"]:
        val = os.environ.get(env_var, "").strip()
        if val.startswith("{") and "private_key" in val:
            try:
                cert_dict = json.loads(val)
                return cert_dict, "INLINE_JSON"
            except Exception as e:
                logger.warning(f"Failed to parse inline JSON from {env_var}: {e}")

    # 2. Base64-encoded JSON
    b64_val = os.environ.get("FIREBASE_CREDENTIALS_BASE64", "").strip()
    if b64_val:
        try:
            decoded = base64.b64decode(b64_val).decode("utf-8")
            cert_dict = json.loads(decoded)
            return cert_dict, "BASE64_JSON"
        except Exception as e:
            logger.warning(f"Failed to parse credentials from FIREBASE_CREDENTIALS_BASE64: {e}")

    # 3. File paths
    possible_paths = [
        settings.firebase_credentials_path,
        os.environ.get("FIREBASE_CREDENTIALS"),
        os.environ.get("FIREBASE_CREDENTIALS_PATH"),
        "firebase-service-account.json",
        os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "firebase-service-account.json"),
        os.path.join(os.getcwd(), "backend", "firebase-service-account.json"),
        os.path.join(os.getcwd(), "firebase-service-account.json")
    ]
    for p in possible_paths:
        if p and not p.startswith("{") and os.path.exists(p) and os.path.isfile(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    cert_dict = json.load(f)
                return cert_dict, f"FILE:{p}"
            except Exception as e:
                logger.warning(f"Failed to read service account file {p}: {e}")

    return None, "NONE"


# Initialize Firebase Admin SDK
try:
    import firebase_admin
    from firebase_admin import credentials, auth as firebase_auth

    cert_data, source_type = resolve_firebase_credentials()
    if cert_data:
        cred = credentials.Certificate(cert_data)
        try:
            firebase_admin.get_app()
        except ValueError:
            firebase_admin.initialize_app(cred, {"projectId": settings.firebase_project_id})
        firebase_initialized = True
        firebase_init_mode = source_type
        logger.info(f"Firebase Admin SDK initialized successfully via {source_type} (project: {settings.firebase_project_id})")
    else:
        # Check Application Default Credentials (GCP Cloud Run / GKE / App Engine)
        adc_initialized = False
        try:
            adc = credentials.ApplicationDefault()
            try:
                firebase_admin.get_app()
            except ValueError:
                firebase_admin.initialize_app(adc, {"projectId": settings.firebase_project_id})
            firebase_initialized = True
            firebase_init_mode = "APPLICATION_DEFAULT_CREDENTIALS"
            adc_initialized = True
            logger.info("Firebase Admin SDK initialized via Application Default Credentials (ADC).")
        except Exception:
            pass

        if not adc_initialized:
            try:
                try:
                    firebase_admin.get_app()
                except ValueError:
                    firebase_admin.initialize_app(options={"projectId": settings.firebase_project_id})
                firebase_initialized = True
                firebase_init_mode = "PROJECT_ID_PUBLIC_MODE"
                logger.info(f"Firebase Admin SDK initialized with project ID '{settings.firebase_project_id}' (public token verification mode).")
            except Exception as e_init:
                logger.info(f"Firebase default app init skipped ({e_init}). Standalone JWT / mock verification enabled.")
except Exception as e:
    logger.info(f"Firebase Admin SDK initialization skipped ({e}). Standalone JWT / mock verification enabled.")


def get_auth_system_status() -> Dict[str, Any]:
    """
    Returns an observability report of the authentication subsystem configuration and readiness.
    """
    is_prod = settings.environment == "production"
    status = {
        "environment": settings.environment,
        "firebase_initialized": firebase_initialized,
        "init_mode": firebase_init_mode,
        "project_id": settings.firebase_project_id,
        "mock_auth_active": settings.allow_mock_auth and not is_prod,
        "production_ready": True,
        "details": []
    }

    if is_prod:
        if not firebase_initialized:
            status["production_ready"] = False
            status["details"].append("Firebase Admin SDK is not initialized.")
        else:
            status["details"].append(f"Operating in {firebase_init_mode} mode. Mock authentication strictly blocked.")
    else:
        status["details"].append(f"Operating in development/testing mode ({firebase_init_mode}).")

    return status


def create_access_token(data: Dict[str, Any], expires_delta: Optional[timedelta] = None) -> str:
    """Create a signed JWT token (used for testing or development authentication)."""
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + (expires_delta or timedelta(hours=24))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)


def create_custom_firebase_token(uid: str, claims: Optional[Dict[str, Any]] = None) -> Optional[str]:
    """
    Generate a signed Firebase custom token using the initialized Firebase Admin SDK.
    Useful for backend-to-client token minting and automated test suites.
    """
    if not firebase_initialized or "FILE" not in firebase_init_mode and "JSON" not in firebase_init_mode:
        return None
    try:
        token_bytes = firebase_auth.create_custom_token(uid, claims or {})
        return token_bytes.decode("utf-8") if isinstance(token_bytes, bytes) else str(token_bytes)
    except Exception as e:
        logger.warning(f"Failed to generate Firebase custom token: {e}")
        return None


def verify_id_token(token: str) -> Optional[Dict[str, Any]]:
    """
    Verify a Firebase ID token or internal development JWT.
    Returns decoded token dictionary containing 'uid', 'email', 'role', or None if invalid.
    """
    if not token or not token.strip():
        return None

    clean_token = token.strip()

    # 1. Check Mock Developer Tokens for offline-first testing (strictly blocked in production)
    if settings.allow_mock_auth and settings.environment != "production":
        if clean_token == "mock-admin-token":
            return {
                "uid": "admin-mock-uid-0001",
                "email": "admin@squishshield.local",
                "name": "System Administrator",
                "role": "ADMIN"
            }
        elif clean_token == "mock-user-token":
            return {
                "uid": "user-mock-uid-0001",
                "email": "user@squishshield.local",
                "name": "Standard User",
                "role": "USER"
            }
        elif clean_token.startswith("mock-user-"):
            uid_suffix = clean_token.removeprefix("mock-user-")
            return {
                "uid": f"user-mock-{uid_suffix}",
                "email": f"user_{uid_suffix}@squishshield.local",
                "name": f"User {uid_suffix}",
                "role": "USER"
            }

    # 2. Verify via Live Firebase Admin SDK if active
    if firebase_initialized:
        try:
            decoded = firebase_auth.verify_id_token(clean_token)
            return {
                "uid": decoded.get("uid"),
                "email": decoded.get("email"),
                "name": decoded.get("name"),
                "role": decoded.get("role", "USER")
            }
        except Exception as e:
            logger.debug(f"Firebase Admin SDK token verification skipped/failed: {e}")

    # 3. Verify signed JWT (HS256 dev token or custom minted token)
    try:
        payload = jwt.decode(
            clean_token,
            settings.jwt_secret_key,
            algorithms=[settings.jwt_algorithm]
        )
        return {
            "uid": payload.get("sub") or payload.get("uid"),
            "email": payload.get("email", ""),
            "name": payload.get("name"),
            "role": payload.get("role", "USER")
        }
    except JWTError:
        pass

    # 4. In development mode only, safely extract claims from Google Firebase tokens if live verification network failed.
    # Security Rule: Never trust client-supplied role claims on unverified tokens; always pin role to USER.
    if (settings.allow_mock_auth or settings.environment == "development") and settings.environment != "production":
        try:
            unverified = jwt.get_unverified_claims(clean_token)
            if unverified.get("iss", "").startswith("https://securetoken.google.com/") and (unverified.get("user_id") or unverified.get("sub")):
                return {
                    "uid": unverified.get("user_id") or unverified.get("sub"),
                    "email": unverified.get("email", ""),
                    "name": unverified.get("name"),
                    "role": "USER"  # Security fix: strictly assign USER role on unverified tokens
                }
        except Exception:
            pass

    return None

