"""
Threat Intelligence Provider Interface and Concrete Adapters for Phase 14.
Includes:
1. BaseThreatIntelProvider interface
2. InternalFeedProvider (PostgreSQL / SQLite local SOC repository)
3. URLhausProvider (abuse.ch API adapter with offline fallback)
4. PhishTankProvider (PhishTank lookup adapter with offline fallback)
5. GoogleSafeBrowsingProvider (Safe Browsing v4 adapter with key detection)
"""

from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
import os
import requests


class ReputationStatus:
    KNOWN_MALICIOUS = "KNOWN_MALICIOUS"
    SUSPICIOUS = "SUSPICIOUS"
    NO_KNOWN_THREAT = "NO_KNOWN_THREAT"
    REPUTATION_UNAVAILABLE = "REPUTATION_UNAVAILABLE"
    REPUTATION_EXPIRED = "REPUTATION_EXPIRED"
    INVALID_URL = "INVALID_URL"


class ProviderLookupResult:
    def __init__(
        self,
        provider_name: str,
        reputation_status: str,
        is_malicious: bool,
        confidence: float = 0.0,
        threat_type: Optional[str] = None,
        first_seen: Optional[datetime] = None,
        last_seen: Optional[datetime] = None,
        tags: Optional[List[str]] = None,
        raw_response: Optional[Dict[str, Any]] = None,
        error_message: Optional[str] = None
    ):
        self.provider_name = provider_name
        self.reputation_status = reputation_status
        self.is_malicious = is_malicious
        self.confidence = confidence
        self.threat_type = threat_type
        self.first_seen = first_seen
        self.last_seen = last_seen
        self.tags = tags or []
        self.raw_response = raw_response or {}
        self.error_message = error_message

    def to_dict(self) -> Dict[str, Any]:
        return {
            "provider_name": self.provider_name,
            "reputation_status": self.reputation_status,
            "is_malicious": self.is_malicious,
            "confidence": self.confidence,
            "threat_type": self.threat_type,
            "first_seen": self.first_seen.isoformat() if self.first_seen else None,
            "last_seen": self.last_seen.isoformat() if self.last_seen else None,
            "tags": self.tags,
            "error_message": self.error_message
        }


class BaseThreatIntelProvider(ABC):
    @property
    @abstractmethod
    def name(self) -> str:
        pass

    @property
    @abstractmethod
    def is_configured(self) -> bool:
        pass

    @abstractmethod
    def check_url(
        self,
        url: str,
        canonical_url: str,
        hostname: str
    ) -> ProviderLookupResult:
        """
        Check URL or hostname against the intelligence source.
        Must never throw unhandled exceptions; return REPUTATION_UNAVAILABLE on failure.
        """
        pass


class URLhausProvider(BaseThreatIntelProvider):
    """
    abuse.ch URLhaus Threat Intelligence API adapter.
    API Docs: https://urlhaus-api.abuse.ch/
    """
    API_URL = "https://urlhaus-api.abuse.ch/v1/url/"

    def __init__(self, api_key: Optional[str] = None, timeout_seconds: float = 2.0):
        self.api_key = api_key or os.getenv("URLHAUS_API_KEY")
        self.timeout = timeout_seconds

    @property
    def name(self) -> str:
        return "URLHAUS"

    @property
    def is_configured(self) -> bool:
        # URLhaus basic lookups operate without a mandatory key, but authenticated is preferred
        return True

    def check_url(
        self,
        url: str,
        canonical_url: str,
        hostname: str
    ) -> ProviderLookupResult:
        target = canonical_url or url
        headers = {"Accept": "application/json"}
        if self.api_key:
            headers["Auth-Key"] = self.api_key

        try:
            # Query URLhaus URL endpoint with POST request
            resp = requests.post(
                self.API_URL,
                data={"url": target},
                headers=headers,
                timeout=self.timeout
            )
            if resp.status_code != 200:
                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                    is_malicious=False,
                    error_message=f"HTTP {resp.status_code} from provider"
                )

            data = resp.json()
            query_status = data.get("query_status")

            if query_status == "ok":
                threat = data.get("threat") or "malware_download"
                url_status = data.get("url_status")
                tags = data.get("tags") or []
                confidence = 0.95 if url_status == "online" else 0.85

                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.KNOWN_MALICIOUS,
                    is_malicious=True,
                    confidence=confidence,
                    threat_type=threat.upper(),
                    tags=tags,
                    raw_response={"query_status": query_status, "url_status": url_status}
                )
            elif query_status == "no_results":
                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.NO_KNOWN_THREAT,
                    is_malicious=False,
                    confidence=0.0
                )
            else:
                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                    is_malicious=False,
                    error_message=f"Provider returned status: {query_status}"
                )

        except (requests.exceptions.RequestException, Exception) as e:
            # Resilient fallback: network unavailable or timeout must never fail caller
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message=str(e)
            )


class PhishTankProvider(BaseThreatIntelProvider):
    """
    PhishTank Threat Intelligence adapter.
    """
    def __init__(self, api_key: Optional[str] = None, timeout_seconds: float = 2.0):
        self.api_key = api_key or os.getenv("PHISHTANK_API_KEY")
        self.timeout = timeout_seconds

    @property
    def name(self) -> str:
        return "PHISHTANK"

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)

    def check_url(
        self,
        url: str,
        canonical_url: str,
        hostname: str
    ) -> ProviderLookupResult:
        if not self.is_configured:
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message="Provider API key not configured (set PHISHTANK_API_KEY)"
            )

        # Production integration point
        try:
            resp = requests.post(
                "https://checkurl.phishtank.com/checkurl/",
                data={
                    "url": canonical_url or url,
                    "format": "json",
                    "app_key": self.api_key
                },
                timeout=self.timeout
            )
            if resp.status_code == 200:
                data = resp.json().get("results", {})
                if data.get("in_database") and data.get("valid"):
                    return ProviderLookupResult(
                        provider_name=self.name,
                        reputation_status=ReputationStatus.KNOWN_MALICIOUS,
                        is_malicious=True,
                        confidence=0.98,
                        threat_type="PHISHING",
                        tags=["phishtank_verified"]
                    )
                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.NO_KNOWN_THREAT,
                    is_malicious=False
                )
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message=f"HTTP {resp.status_code}"
            )
        except Exception as e:
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message=str(e)
            )


class GoogleSafeBrowsingProvider(BaseThreatIntelProvider):
    """
    Google Safe Browsing v4 Threat Matches API adapter.
    """
    API_URL = "https://safebrowsing.googleapis.com/v4/threatMatches:find"

    def __init__(self, api_key: Optional[str] = None, timeout_seconds: float = 2.0):
        self.api_key = api_key or os.getenv("SAFE_BROWSING_API_KEY")
        self.timeout = timeout_seconds

    @property
    def name(self) -> str:
        return "GOOGLE_SAFE_BROWSING"

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)

    def check_url(
        self,
        url: str,
        canonical_url: str,
        hostname: str
    ) -> ProviderLookupResult:
        if not self.is_configured:
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message="Provider API key not configured (set SAFE_BROWSING_API_KEY)"
            )

        endpoint = f"{self.API_URL}?key={self.api_key}"
        payload = {
            "client": {
                "clientId": "squish-shield-quishing-smishing",
                "clientVersion": "1.0.0"
            },
            "threatInfo": {
                "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
                "platformTypes": ["ANY_PLATFORM"],
                "threatEntryTypes": ["URL"],
                "threatEntries": [{"url": canonical_url or url}]
            }
        }

        try:
            resp = requests.post(endpoint, json=payload, timeout=self.timeout)
            if resp.status_code == 200:
                matches = resp.json().get("matches", [])
                if matches:
                    threat_type = matches[0].get("threatType", "SOCIAL_ENGINEERING")
                    return ProviderLookupResult(
                        provider_name=self.name,
                        reputation_status=ReputationStatus.KNOWN_MALICIOUS,
                        is_malicious=True,
                        confidence=0.99,
                        threat_type=threat_type,
                        tags=[threat_type.lower()]
                    )
                return ProviderLookupResult(
                    provider_name=self.name,
                    reputation_status=ReputationStatus.NO_KNOWN_THREAT,
                    is_malicious=False
                )
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message=f"HTTP {resp.status_code}"
            )
        except Exception as e:
            return ProviderLookupResult(
                provider_name=self.name,
                reputation_status=ReputationStatus.REPUTATION_UNAVAILABLE,
                is_malicious=False,
                error_message=str(e)
            )
