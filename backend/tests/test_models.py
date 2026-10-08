def test_model_registration_and_retrieval(client, admin_headers, user_headers):
    payload = {
        "name": "TinyBERT-Smishing",
        "version": "v2.0.0",
        "type": "ONNX_TRANSFORMER",
        "status": "ACTIVE",
        "artifact_hash": "sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        "metadata_json": {"f1_score": 0.96, "latency_ms": 14.2}
    }
    # Register via admin
    reg_resp = client.post("/api/v1/admin/models", json=payload, headers=admin_headers)
    assert reg_resp.status_code == 201
    model_id = reg_resp.json()["id"]

    # List via user
    list_resp = client.get("/api/v1/models", headers=user_headers)
    assert list_resp.status_code == 200
    models = list_resp.json()
    assert any(m["id"] == model_id for m in models)

    # Get by ID via user
    get_resp = client.get(f"/api/v1/models/{model_id}", headers=user_headers)
    assert get_resp.status_code == 200
    assert get_resp.json()["name"] == "TinyBERT-Smishing"


def test_model_not_found(client, user_headers):
    resp = client.get("/api/v1/models/non-existent-uuid", headers=user_headers)
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] in ("NOT_FOUND", "MODEL_NOT_FOUND")
