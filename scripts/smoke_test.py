#!/usr/bin/env python3
"""
Deployment Smoke & Health Verification Test Suite
Squish AI/ML-Based Quishing and Smishing Detection System (Phase 17 DevSecOps)

Validates target environment health, API readiness, security headers,
and error envelopes post-deployment without mutating production data.
"""

import os
import sys
import argparse
import requests

def parse_args():
    parser = argparse.ArgumentParser(description="Run post-deployment smoke tests against target environment.")
    parser.add_argument("--base-url", default=os.environ.get("TARGET_BASE_URL", "http://localhost:8000"),
                        help="Backend API base URL (e.g. http://localhost:8000 or https://api.squishshield.org)")
    parser.add_argument("--timeout", type=int, default=10, help="HTTP request timeout in seconds")
    return parser.parse_args()

def test_endpoint(name: str, url: str, expected_status: int, timeout: int) -> bool:
    print(f"\n[TEST] {name} -> GET {url}")
    try:
        resp = requests.get(url, timeout=timeout)
        status_ok = (resp.status_code == expected_status)
        print(f"  Status: {resp.status_code} (Expected: {expected_status}) -> {'PASS' if status_ok else 'FAIL'}")
        
        # Verify OWASP security headers
        headers = resp.headers
        sec_header_ok = True
        for header, expected_val in [
            ("X-Content-Type-Options", "nosniff"),
            ("X-Frame-Options", "DENY")
        ]:
            val = headers.get(header)
            if val != expected_val:
                print(f"  [WARN] Missing or unexpected header {header}: {val}")
                sec_header_ok = False
        
        if sec_header_ok:
            print("  Security Headers: nosniff and DENY present -> PASS")

        return status_ok and sec_header_ok
    except Exception as e:
        print(f"  [FAIL] Connection error: {e}")
        return False

def test_unauthenticated_protected_route(base_url: str, timeout: int) -> bool:
    url = f"{base_url.rstrip('/')}/api/v1/users/me"
    print(f"\n[TEST] Protected Route Authentication Guard -> GET {url}")
    try:
        resp = requests.get(url, timeout=timeout)
        # Should be 401 Unauthorized without token
        if resp.status_code == 401:
            print(f"  Status: {resp.status_code} (Unauthorized blocked properly) -> PASS")
            return True
        else:
            print(f"  [FAIL] Expected status 401, got {resp.status_code}")
            return False
    except Exception as e:
        print(f"  [FAIL] Connection error: {e}")
        return False

def test_admin_route_authorization_guard(base_url: str, timeout: int) -> bool:
    url = f"{base_url.rstrip('/')}/api/v1/admin/incidents"
    print(f"\n[TEST] Admin Route RBAC Guard -> GET {url}")
    try:
        resp = requests.get(url, timeout=timeout)
        # Should be 401 (unauthenticated) or 403 (unauthorized)
        if resp.status_code in (401, 403):
            print(f"  Status: {resp.status_code} (Unauthenticated/Unauthorized blocked properly) -> PASS")
            return True
        else:
            print(f"  [FAIL] Expected status 401 or 403, got {resp.status_code}")
            return False
    except Exception as e:
        print(f"  [FAIL] Connection error: {e}")
        return False

def main():
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    timeout = args.timeout

    print("=" * 80)
    print(f"DEPLOYMENT SMOKE TESTS: {base_url}")
    print("=" * 80)

    results = []
    
    # 1. Liveness check
    results.append(test_endpoint("Liveness Probe", f"{base_url}/health/live", 200, timeout))
    
    # 2. Readiness probe (verifies database connectivity)
    results.append(test_endpoint("Readiness Probe", f"{base_url}/health/ready", 200, timeout))

    # 3. Authentication barrier
    results.append(test_unauthenticated_protected_route(base_url, timeout))

    # 4. Admin authorization barrier
    results.append(test_admin_route_authorization_guard(base_url, timeout))

    print("\n" + "=" * 80)
    print("DEPLOYMENT SMOKE TEST SUMMARY")
    print("=" * 80)
    passed_count = sum(1 for r in results if r)
    total_count = len(results)
    print(f"Total Tests: {total_count} | Passed: {passed_count} | Failed: {total_count - passed_count}")

    if all(results):
        print("\n>>> ALL POST-DEPLOYMENT SMOKE TESTS PASSED. <<<")
        sys.exit(0)
    else:
        print("\n>>> CRITICAL: POST-DEPLOYMENT SMOKE TESTS FAILED. <<<")
        sys.exit(1)

if __name__ == "__main__":
    main()
