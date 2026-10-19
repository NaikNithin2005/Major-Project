"""
Safe URL Normalizer, Canonicalizer & SSRF Validator for Phase 14.
Provides RFC 3986 compliant URL normalization, Punycode IDN conversion,
query parameter privacy scrubbing, and SSRF loopback/private IP detection.
"""

import ipaddress
import re
import urllib.parse
from typing import Dict, Any, Optional, Tuple
import hashlib


# Sensitive query parameters to scrub to preserve user privacy
SENSITIVE_PARAM_NAMES = {
    "token", "access_token", "auth", "key", "api_key", "apikey",
    "password", "pass", "pwd", "secret", "session", "sessionid",
    "sess", "sid", "sig", "signature", "code", "otp", "pin",
    "id_token", "refresh_token", "credential", "auth_token"
}

# Dangerous protocols that should be rejected or flagged
DANGEROUS_SCHEMES = {"javascript", "data", "file", "blob", "vbscript", "about", "intent", "smsto", "tel", "content"}
ALLOWED_SCHEMES = {"http", "https"}


class UrlNormalizationResult:
    def __init__(
        self,
        raw_url: str,
        normalized_url: str,
        domain: str,
        hostname: str,
        scheme: str,
        port: Optional[int],
        path: str,
        query: str,
        is_valid: bool,
        is_ssrf_risk: bool,
        ssrf_reason: Optional[str] = None,
        canonical_hash: str = "",
        domain_hash: str = "",
        error: Optional[str] = None
    ):
        self.raw_url = raw_url
        self.normalized_url = normalized_url
        self.domain = domain
        self.hostname = hostname
        self.scheme = scheme
        self.port = port
        self.path = path
        self.query = query
        self.is_valid = is_valid
        self.is_ssrf_risk = is_ssrf_risk
        self.ssrf_reason = ssrf_reason
        self.canonical_hash = canonical_hash
        self.domain_hash = domain_hash
        self.error = error

    def to_dict(self) -> Dict[str, Any]:
        return {
            "raw_url": self.raw_url,
            "normalized_url": self.normalized_url,
            "domain": self.domain,
            "hostname": self.hostname,
            "scheme": self.scheme,
            "port": self.port,
            "path": self.path,
            "is_valid": self.is_valid,
            "is_ssrf_risk": self.is_ssrf_risk,
            "ssrf_reason": self.ssrf_reason,
            "canonical_hash": self.canonical_hash,
            "domain_hash": self.domain_hash,
            "error": self.error
        }


class SafeUrlNormalizer:
    @staticmethod
    def is_private_or_loopback_ip(hostname: str) -> Tuple[bool, Optional[str]]:
        """
        Check if hostname is a private, loopback, link-local, or reserved IP address (SSRF guard).
        Handles IPv4, IPv6, IPv4-mapped IPv6, decimal integers, hex notation, and octal dot-decimal.
        """
        cleaned_host = hostname.strip("[]")
        
        # Check standard IP address representations
        try:
            ip = ipaddress.ip_address(cleaned_host)
            # Check IPv4-mapped IPv6 (e.g. ::ffff:127.0.0.1 or ::ffff:169.254.169.254)
            if hasattr(ip, "ipv4_mapped") and ip.ipv4_mapped:
                mapped = ip.ipv4_mapped
                if mapped.is_loopback:
                    return True, f"IPv4-mapped loopback address ({mapped}) detected"
                if mapped.is_private:
                    return True, f"IPv4-mapped private RFC 1918 address ({mapped}) detected"
                if mapped.is_link_local:
                    return True, f"IPv4-mapped link-local address ({mapped}) detected"

            if ip.is_loopback:
                return True, f"Loopback IP address ({ip}) detected"
            if ip.is_private:
                return True, f"Private network RFC 1918 / RFC 4193 IP address ({ip}) detected"
            if ip.is_link_local:
                return True, f"Link-local address ({ip}) detected"
            if ip.is_multicast:
                return True, f"Multicast address ({ip}) detected"
            if ip.is_reserved:
                return True, f"Reserved IP address ({ip}) detected"
            if ip.is_unspecified:
                return True, f"Unspecified address ({ip}) detected"
            return False, None
        except ValueError:
            pass

        # Check for dotted hex/octal notation (e.g. 0x7f.0.0.1, 0177.0.0.1)
        dot_parts = cleaned_host.split(".")
        if len(dot_parts) == 4:
            try:
                def _parse_part(p: str) -> int:
                    if p.startswith("0x") or p.startswith("0X"):
                        return int(p, 16)
                    elif p.startswith("0o") or p.startswith("0O"):
                        return int(p, 8)
                    elif p.startswith("0") and len(p) > 1 and p.isdigit():
                        return int(p, 8)
                    else:
                        return int(p, 10)

                nums = [_parse_part(p) for p in dot_parts]
                if all(0 <= n <= 255 for n in nums):
                    oct_ip = ipaddress.IPv4Address(bytes(nums))
                    if oct_ip.is_loopback:
                        return True, f"Dotted-encoded loopback IP ({oct_ip}) detected"
                    if oct_ip.is_private:
                        return True, f"Dotted-encoded private IP ({oct_ip}) detected"
                    if oct_ip.is_link_local:
                        return True, f"Dotted-encoded link-local IP ({oct_ip}) detected"
            except (ValueError, Exception):
                pass

        # Check for hex or octal encoded raw integer IP representations (e.g. 0x7f000001, 2130706433)
        if re.match(r"^0x[0-9a-fA-F]+$", cleaned_host) or (cleaned_host.isdigit() and len(cleaned_host) > 4):
            try:
                numeric_val = int(cleaned_host, 0)
                ip = ipaddress.IPv4Address(numeric_val)
                if ip.is_loopback or ip.is_private or ip.is_link_local:
                    return True, f"Encoded private/loopback IPv4 ({ip}) detected"
            except (ValueError, OverflowError):
                pass

        # Check special localhost strings
        lower_host = cleaned_host.lower()
        if lower_host in {"localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback"}:
            return True, "Localhost hostname detected"

        return False, None

    @classmethod
    def normalize_url(cls, raw_url: str) -> UrlNormalizationResult:
        """
        Normalize and canonicalize a URL safely:
        1. Parse scheme, authority, path, and query parameters.
        2. Convert hostname to lowercase and Punycode for IDN domains.
        3. Strip default ports (80 for HTTP, 443 for HTTPS).
        4. Resolve path dots and collapse multiple slashes.
        5. Scrub privacy-sensitive query parameters.
        6. Validate against SSRF loopback/private ranges.
        7. Compute deterministic SHA-256 canonical fingerprints.
        """
        if not raw_url or not isinstance(raw_url, str):
            return UrlNormalizationResult(
                raw_url=str(raw_url or ""),
                normalized_url="",
                domain="",
                hostname="",
                scheme="",
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=False,
                error="Empty or non-string URL provided"
            )

        trimmed = raw_url.strip()

        # Check if URL starts with an explicit scheme
        scheme_prefix_match = re.match(r"^([a-zA-Z][a-zA-Z0-9+\-.]*):", trimmed)
        if scheme_prefix_match:
            raw_scheme = scheme_prefix_match.group(1).lower()
            if raw_scheme in DANGEROUS_SCHEMES:
                return UrlNormalizationResult(
                    raw_url=raw_url,
                    normalized_url="",
                    domain="",
                    hostname="",
                    scheme=raw_scheme,
                    port=None,
                    path="",
                    query="",
                    is_valid=False,
                    is_ssrf_risk=True,
                    ssrf_reason=f"Dangerous protocol scheme '{raw_scheme}' detected",
                    error=f"Dangerous protocol scheme: {raw_scheme}"
                )

        # Handle defanged URLs (e.g. hxxp:// or hxxps:// or domain[.]com)
        defanged = re.sub(r"^hxxp", "http", trimmed, flags=re.IGNORECASE)
        defanged = defanged.replace("[.]", ".").replace("[:]", ":")

        # Prefix scheme if missing for parsing
        if not re.match(r"^[a-zA-Z][a-zA-Z0-9+\-.]*://", defanged):
            defanged = "http://" + defanged

        try:
            parsed = urllib.parse.urlsplit(defanged)
        except Exception as e:
            return UrlNormalizationResult(
                raw_url=raw_url,
                normalized_url="",
                domain="",
                hostname="",
                scheme="",
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=False,
                error=f"Malformed URL: {str(e)}"
            )

        scheme = (parsed.scheme or "").lower()
        if scheme in DANGEROUS_SCHEMES:
            return UrlNormalizationResult(
                raw_url=raw_url,
                normalized_url="",
                domain="",
                hostname="",
                scheme=scheme,
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=True,
                ssrf_reason=f"Dangerous protocol scheme '{scheme}' detected",
                error=f"Dangerous protocol scheme: {scheme}"
            )

        if scheme not in ALLOWED_SCHEMES:
            return UrlNormalizationResult(
                raw_url=raw_url,
                normalized_url="",
                domain="",
                hostname="",
                scheme=scheme,
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=False,
                error=f"Unsupported URL scheme: {scheme}"
            )

        try:
            raw_hostname = (parsed.hostname or "").strip()
        except (ValueError, Exception):
            raw_hostname = ""

        if not raw_hostname:
            return UrlNormalizationResult(
                raw_url=raw_url,
                normalized_url="",
                domain="",
                hostname="",
                scheme=scheme,
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=False,
                error="Missing hostname in URL"
            )

        # IDN / Punycode conversion
        try:
            punycode_host = raw_hostname.encode("idna").decode("ascii").lower()
        except Exception:
            punycode_host = raw_hostname.lower()

        # SSRF checks
        is_ssrf, ssrf_reason = cls.is_private_or_loopback_ip(punycode_host)

        # Port normalization
        try:
            port = parsed.port
        except (ValueError, Exception):
            return UrlNormalizationResult(
                raw_url=raw_url,
                normalized_url="",
                domain="",
                hostname=punycode_host,
                scheme=scheme,
                port=None,
                path="",
                query="",
                is_valid=False,
                is_ssrf_risk=False,
                error="Invalid port specification in URL"
            )

        if (scheme == "http" and port == 80) or (scheme == "https" and port == 443):
            port = None

        netloc = punycode_host
        if port is not None:
            netloc = f"{punycode_host}:{port}"

        # Path normalization (collapse slashes and resolve dots)
        path = parsed.path or "/"
        # Replace multiple slashes with a single slash
        path = re.sub(r"/+", "/", path)
        if not path.startswith("/"):
            path = "/" + path

        # Query normalization and privacy scrubbing
        query = ""
        if parsed.query:
            query_pairs = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
            scrubbed_pairs = []
            for k, v in query_pairs:
                if k.lower() in SENSITIVE_PARAM_NAMES:
                    scrubbed_pairs.append((k, "[REDACTED]"))
                else:
                    scrubbed_pairs.append((k, v))
            scrubbed_pairs.sort(key=lambda x: x[0])
            query = urllib.parse.urlencode(scrubbed_pairs)

        # Build normalized URL
        normalized_url = urllib.parse.urlunsplit((scheme, netloc, path, query, ""))

        # Derive registered domain (simple fallback)
        host_parts = punycode_host.split(".")
        if len(host_parts) >= 2 and not cls._is_ip_str(punycode_host):
            domain = ".".join(host_parts[-2:])
        else:
            domain = punycode_host

        # Compute cryptographic canonical hashes
        canonical_hash = hashlib.sha256(normalized_url.encode("utf-8")).hexdigest()
        domain_hash = hashlib.sha256(punycode_host.encode("utf-8")).hexdigest()

        return UrlNormalizationResult(
            raw_url=raw_url,
            normalized_url=normalized_url,
            domain=domain,
            hostname=punycode_host,
            scheme=scheme,
            port=port,
            path=path,
            query=query,
            is_valid=True,
            is_ssrf_risk=is_ssrf,
            ssrf_reason=ssrf_reason,
            canonical_hash=canonical_hash,
            domain_hash=domain_hash
        )

    @staticmethod
    def _is_ip_str(host: str) -> bool:
        try:
            ipaddress.ip_address(host.strip("[]"))
            return True
        except ValueError:
            return False
