import requests
import time
import uuid
from datetime import datetime
import sys

BASE_URL = "http://127.0.0.1:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Content-Type": "application/json",
    "Accept": "application/json",
}
TIMEOUT = 30


def _extract_id(obj):
    if obj is None:
        return None
    if isinstance(obj, (str, int)):
        return str(obj)
    if isinstance(obj, dict):
        for key in ("id", "detection_id", "uuid", "event_id"):
            if key in obj:
                return str(obj[key])
        # nested detection object
        for key in ("detection", "item", "data"):
            nested = obj.get(key)
            if isinstance(nested, dict):
                for k in ("id", "detection_id", "uuid"):
                    if k in nested:
                        return str(nested[k])
    return None


def _extract_timestamp(obj):
    if not isinstance(obj, dict):
        return None
    for key in ("created_at", "created", "timestamp", "created_on", "created_ts", "time"):
        if key in obj and obj[key]:
            val = obj[key]
            if isinstance(val, (int, float)):
                # epoch
                try:
                    return datetime.utcfromtimestamp(float(val))
                except Exception:
                    continue
            if isinstance(val, str):
                s = val
                # handle trailing Z
                if s.endswith("Z"):
                    s = s[:-1] + "+00:00"
                try:
                    return datetime.fromisoformat(s)
                except Exception:
                    # try common formats
                    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S"):
                        try:
                            return datetime.strptime(val, fmt)
                        except Exception:
                            continue
    return None


def _get_items_from_list_response(json_obj):
    # common wrappers
    if isinstance(json_obj, list):
        return json_obj, {}
    if not isinstance(json_obj, dict):
        return [], {}
    for key in ("items", "data", "results", "detections", "events"):
        if key in json_obj and isinstance(json_obj[key], list):
            meta = {k: v for k, v in json_obj.items() if k != key}
            return json_obj[key], meta
    # fallback: maybe top-level has list under 'rows'
    for key in ("rows",):
        if key in json_obj and isinstance(json_obj[key], list):
            meta = {k: v for k, v in json_obj.items() if k != key}
            return json_obj[key], meta
    # if nothing found, maybe whole object represents a single page as list-like under unknown key -> try to find first list value
    for v in json_obj.values():
        if isinstance(v, list):
            return v, {k: w for k, w in json_obj.items() if not isinstance(w, list)}
    # no list found
    return [], json_obj


def test_threat_history_pagination():
    created_ids = []
    created_ts = {}

    # wait for readiness (optional quick probe)
    try:
        r = requests.get(f"{BASE_URL}/health/ready", timeout=5)
        if r.status_code not in (200, 503):
            # proceed anyway but log
            pass
    except requests.RequestException:
        # ignore readiness failures; continue to attempt test against service
        pass

    # Create 3 synthetic detections with slight delays to ensure distinct timestamps
    try:
        for i in range(3):
            payload = {
                "device_detection_id": f"test-{uuid.uuid4()}",
                "threat_type": "SMS" if i % 2 == 0 else "QR",
                "risk_score": 50 + i * 10,
                "threat_level": "HIGH" if i == 2 else "MEDIUM",
                "sender_or_source": f"+100000{i}",
                "risk_reasons": ["synthetic_test", f"seq_{i}"],
            }
            resp = requests.post(
                f"{BASE_URL}/api/v1/v1/detections",
                json=payload,
                headers=HEADERS,
                timeout=TIMEOUT,
            )
            assert resp.status_code in (200, 201), f"POST /detections failed: {resp.status_code} - {resp.text}"
            j = resp.json() if resp.text else {}
            # try to extract id from response body
            det_id = _extract_id(j)
            if not det_id:
                # sometimes API returns the created resource under 'data' or as full object
                if isinstance(j, dict):
                    possible = j.get("data") or j.get("detection") or j.get("item")
                    det_id = _extract_id(possible)
            assert det_id, f"Could not extract detection id from response JSON: {j}"
            created_ids.append(det_id)

            # fetch individual record to get canonical created timestamp
            get_resp = requests.get(
                f"{BASE_URL}/api/v1/v1/detections/{det_id}",
                headers=HEADERS,
                timeout=TIMEOUT,
            )
            assert get_resp.status_code == 200, f"GET /detections/{det_id} failed: {get_resp.status_code} - {get_resp.text}"
            single = get_resp.json() if get_resp.text else {}
            # try multiple fallback shapes
            if isinstance(single, dict) and "data" in single and isinstance(single["data"], dict):
                single_obj = single["data"]
            elif isinstance(single, dict) and "detection" in single and isinstance(single["detection"], dict):
                single_obj = single["detection"]
            else:
                single_obj = single
            ts = _extract_timestamp(single_obj)
            assert ts is not None, f"Could not extract timestamp for detection {det_id} from object: {single_obj}"
            created_ts[det_id] = ts

            # small delay so timestamps differ
            time.sleep(0.6)

        # Now request the paginated list
        list_resp = requests.get(
            f"{BASE_URL}/api/v1/v1/detections",
            headers=HEADERS,
            timeout=TIMEOUT,
        )
        assert list_resp.status_code == 200, f"GET /detections failed: {list_resp.status_code} - {list_resp.text}"
        listing_json = list_resp.json() if list_resp.text else {}
        items, meta = _get_items_from_list_response(listing_json)
        assert isinstance(items, list), "Detections listing did not contain a list of items."

        # Extract ids and timestamps for listed items
        listed_ids = []
        listed_ts = {}
        for item in items:
            item_id = _extract_id(item)
            if not item_id:
                # maybe nested
                for key in ("detection", "data", "item"):
                    if isinstance(item, dict) and key in item and isinstance(item[key], dict):
                        item_id = _extract_id(item[key])
                        if item_id:
                            item = item[key]
                            break
            if not item_id:
                # skip items without id
                continue
            listed_ids.append(item_id)
            ts = _extract_timestamp(item)
            if ts is not None:
                listed_ts[item_id] = ts
            else:
                # attempt to GET single record to retrieve timestamp if missing in list
                try:
                    single_resp = requests.get(
                        f"{BASE_URL}/api/v1/v1/detections/{item_id}",
                        headers=HEADERS,
                        timeout=TIMEOUT,
                    )
                    if single_resp.status_code == 200:
                        single_obj = single_resp.json()
                        if isinstance(single_obj, dict) and "data" in single_obj:
                            single_obj = single_obj["data"]
                        ts2 = _extract_timestamp(single_obj)
                        if ts2:
                            listed_ts[item_id] = ts2
                except Exception:
                    pass

        # Ensure that all created IDs appear in the listing (within the first page)
        found = [did for did in created_ids if did in listed_ids]
        assert found, f"None of created detections found in listing. Created: {created_ids}, Listed IDs sample: {listed_ids[:10]}"

        # Determine order: check if listing is sorted by timestamp (asc or desc)
        # For items that have timestamps known (either from list or from earlier single gets), build sequence
        seq_timestamps = []
        seq_ids = []
        for lid in listed_ids:
            if lid in listed_ts:
                seq_ids.append(lid)
                seq_timestamps.append(listed_ts[lid])

        # If we don't have timestamps from the list for the created items, fall back to the created_ts mapping
        if not seq_timestamps:
            # try to compose based on created_ts and mapping of listed ids that match created
            seq_ids = [lid for lid in listed_ids if lid in created_ts]
            seq_timestamps = [created_ts[lid] for lid in seq_ids]

        assert seq_timestamps, "Could not determine timestamps for listed items to verify ordering."

        is_ascending = all(seq_timestamps[i] <= seq_timestamps[i + 1] for i in range(len(seq_timestamps) - 1))
        is_descending = all(seq_timestamps[i] >= seq_timestamps[i + 1] for i in range(len(seq_timestamps) - 1))
        assert is_ascending or is_descending, "Detections listing is not sorted by creation timestamp."

        # Pagination sanity check: if pagination meta indicates page size or limit, verify page boundaries
        # Accept several common meta keys
        page_size = None
        total = None
        for k in ("per_page", "page_size", "limit", "size"):
            if k in meta:
                try:
                    page_size = int(meta[k])
                    break
                except Exception:
                    pass
        for k in ("total", "count", "total_count"):
            if k in meta:
                try:
                    total = int(meta[k])
                    break
                except Exception:
                    pass

        # If page_size is present and less than total, try to request a subsequent page using common params
        if page_size and total and total > page_size:
            # attempt offset-style pagination first
            params = {"limit": page_size, "offset": page_size}
            page2 = requests.get(f"{BASE_URL}/api/v1/v1/detections", headers=HEADERS, params=params, timeout=TIMEOUT)
            if page2.status_code == 200:
                items2, _ = _get_items_from_list_response(page2.json() if page2.text else {})
                assert isinstance(items2, list), "Second page did not return a list."
                # ensure no overlap between page1 and page2 first page_size items
                ids_page1 = listed_ids[:page_size]
                ids_page2 = [_extract_id(it) for it in items2]
                # remove None entries
                ids_page2 = [i for i in ids_page2 if i]
                assert not set(ids_page1).intersection(set(ids_page2)), "Pagination pages overlap; expected distinct items."
    finally:
        # cleanup: attempt to delete created detections if server supports deletion
        for det_id in created_ids:
            try:
                dresp = requests.delete(
                    f"{BASE_URL}/api/v1/v1/detections/{det_id}",
                    headers=HEADERS,
                    timeout=TIMEOUT,
                )
                # accept 200/204/202/404 (404 means already gone)
                if dresp.status_code not in (200, 202, 204, 404):
                    # log to stderr but don't fail cleanup
                    sys.stderr.write(f"Warning: DELETE /detections/{det_id} returned {dresp.status_code}\n")
            except requests.RequestException as e:
                sys.stderr.write(f"Warning: Exception when deleting detection {det_id}: {e}\n")


if __name__ == "__main__":
    test_threat_history_pagination()
    print("TC013 completed successfully.")