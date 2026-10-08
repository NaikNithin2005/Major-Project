import os
import logging
from typing import Dict, Any, Optional
from datetime import datetime, timezone, timedelta
from jose import jwt, JWTError
from app.config import settings

logger = logging.getLogger(__name__)

# Firebase Admin SDK initialization if credentials provided
firebase_initialized = False
try:
    import firebase_admin
    from firebase_admin import credentials, auth as firebase_auth

    # Detect service account JSON from settings or default locations
    possible_cred_paths = [
        settings.firebase_credentials_path,
        "firebase-service-account.json",
        os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "firebase-service-account.json"),
        os.path.join(os.getcwd(), "backend", "firebase-service-account.json"),
        os.path.join(os.getcwd(), "firebase-service-account.json")
    ]
    resolved_cred_path = next((p for p in possible_cred_paths if p and os.path.exists(p)), None)

    if resolved_cred_path:
        cred = credentials.Certificate(resolved_cred_path)
        # Avoid duplicate default app initialization
        try:
            firebase_admin.get_app()
        except ValueError:
            firebase_admin.initialize_app(cred, {"projectId": settings.firebase_project_id})
        firebase_initialized = True
        logger.info(f"Firebase Admin SDK initialized successfully with service account: {resolved_cred_path}")
    else:
        try:
            try:
                firebase_admin.get_app()
            except ValueError:
                firebase_admin.initialize_app(options={"projectId": settings.firebase_project_id})
            firebase_initialized = True
            logger.info(f"Firebase Admin SDK initialized with project ID '{settings.firebase_project_id}' (public token verification mode).")
        except Exception as e_init:
            logger.info(f"Firebase default app init skipped ({e_init}). Standalone JWT / mock verification enabled.")
except Exception as e:
    logger.info(f"Firebase Admin SDK initialization skipped ({e}). Standalone JWT / mock verification enabled.")


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
    if not firebase_initialized:
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

    # 1. Check Mock Developer Tokens for offline-first testing
    if settings.allow_mock_auth:
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

    # 4. In development mode, safely extract claims from Google Firebase tokens if live verification network failed
    if settings.allow_mock_auth or settings.environment == "development":
        try:
            unverified = jwt.get_unverified_claims(clean_token)
            if unverified.get("iss", "").startswith("https://securetoken.google.com/") and (unverified.get("user_id") or unverified.get("sub")):
                return {
                    "uid": unverified.get("user_id") or unverified.get("sub"),
                    "email": unverified.get("email", ""),
                    "name": unverified.get("name"),
                    "role": unverified.get("role", "USER")
                }
        except Exception:
            pass

    return None
