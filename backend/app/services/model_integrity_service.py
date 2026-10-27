import os
import json
import hashlib
import logging
from typing import Dict, Any, List

logger = logging.getLogger(__name__)

# Search paths for MODEL_MANIFEST.json
MANIFEST_CANDIDATE_PATHS = [
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "models", "MODEL_MANIFEST.json")),
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "models", "MODEL_MANIFEST.json")),
    "models/MODEL_MANIFEST.json",
]


class ModelIntegrityService:
    """
    Verifies that on-device / backend AI model artifacts match certified cryptographic checksums
    and prevents loading corrupted, truncated, or tampered ONNX models.
    """

    @classmethod
    def get_manifest_path(cls) -> str:
        for p in MANIFEST_CANDIDATE_PATHS:
            if os.path.exists(p):
                return p
        return MANIFEST_CANDIDATE_PATHS[0]

    @classmethod
    def compute_sha256(cls, file_path: str) -> str:
        sha256 = hashlib.sha256()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(65536), b""):
                sha256.update(chunk)
        return sha256.hexdigest().lower()

    @classmethod
    def verify_all_models(cls) -> Dict[str, Any]:
        manifest_path = cls.get_manifest_path()
        if not os.path.exists(manifest_path):
            return {
                "status": "MANIFEST_MISSING",
                "manifest_path": manifest_path,
                "verified_count": 0,
                "total_models": 0,
                "all_healthy": False,
                "models": [],
            }

        try:
            with open(manifest_path, "r", encoding="utf-8") as f:
                manifest_data = json.load(f)
        except Exception as e:
            logger.error("Failed to parse model manifest: %s", e)
            return {
                "status": "MANIFEST_ERROR",
                "error": str(e),
                "all_healthy": False,
                "models": [],
            }

        manifest_dir = os.path.dirname(manifest_path)
        base_dir = os.path.dirname(manifest_dir)

        results: List[Dict[str, Any]] = []
        all_healthy = True

        models_dict = manifest_data.get("models", {})
        # If models is a list (legacy) or dict (v2 manifest)
        model_items = models_dict.items() if isinstance(models_dict, dict) else [(m.get("model_id", "unknown"), m) for m in models_dict]

        for model_key, m_info in model_items:
            files_info = m_info.get("files", {})
            onnx_spec = files_info.get("onnx", {}) if isinstance(files_info, dict) else {}
            if not onnx_spec and "artifact_path" in m_info:
                onnx_spec = {
                    "path": m_info.get("artifact_path"),
                    "sha256": m_info.get("sha256"),
                    "size_bytes": m_info.get("file_size_bytes"),
                }

            artifact_rel_path = onnx_spec.get("path", "")
            expected_hash = onnx_spec.get("sha256", "").lower()
            expected_size = onnx_spec.get("size_bytes", 0)

            candidate_paths = [
                os.path.join(base_dir, artifact_rel_path),
                os.path.join(manifest_dir, os.path.basename(artifact_rel_path)),
                os.path.abspath(artifact_rel_path),
                os.path.join(base_dir, "android", "app", "src", "main", "assets", "models", os.path.basename(artifact_rel_path)),
            ]
            actual_path = None
            for cp in candidate_paths:
                if os.path.exists(cp):
                    actual_path = cp
                    break

            if not actual_path:
                all_healthy = False
                results.append({
                    "model_id": model_key,
                    "expected_path": artifact_rel_path,
                    "status": "FILE_MISSING",
                    "verified": False,
                })
                continue

            try:
                actual_size = os.path.getsize(actual_path)
                actual_hash = cls.compute_sha256(actual_path)
                hash_match = (actual_hash == expected_hash)
                size_match = (actual_size == expected_size)

                is_valid = hash_match and size_match
                if not is_valid:
                    all_healthy = False

                results.append({
                    "model": "tinybert" if "tinybert" in model_key.lower() else ("xgboost" if "xgboost" in model_key.lower() else "isolation forest"),
                    "model_id": model_key,
                    "name": m_info.get("name", model_key),
                    "version": m_info.get("version", "v2"),
                    "artifact_path": actual_path,
                    "file_size_bytes": actual_size,
                    "size_matches": size_match,
                    "hash_matches": hash_match,
                    "sha256": actual_hash,
                    "verified": is_valid,
                    "status": "VERIFIED" if is_valid else "CHECKSUM_MISMATCH",
                })
            except Exception as ex:
                all_healthy = False
                results.append({
                    "model": "tinybert" if "tinybert" in model_key.lower() else ("xgboost" if "xgboost" in model_key.lower() else "isolation forest"),
                    "model_id": model_key,
                    "status": "READ_ERROR",
                    "error": str(ex),
                    "verified": False,
                })

        verified_count = sum(1 for r in results if r.get("verified"))
        pct = 100.0 if all_healthy and len(results) > 0 else (round((verified_count / len(results) * 100), 1) if results else 0.0)

        return {
            "status": "HEALTHY" if all_healthy else "INTEGRITY_VIOLATION",
            "manifest_version": manifest_data.get("manifest_version", "1.0.0"),
            "release_version": manifest_data.get("release_version", "v2"),
            "total_models": len(results),
            "verified_count": verified_count,
            "percent_verified": pct,
            "all_healthy": all_healthy,
            "models": results,
            "results": results,
            "summary": {
                "percent_verified": pct,
                "total": len(results),
                "verified": verified_count,
            }
        }
