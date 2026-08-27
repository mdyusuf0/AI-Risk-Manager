import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from explain.models import ExplainRequest
from explain.explainer import generate_explanation

class TestExplainerLogic:
    def test_single_shared_attr_no_time_window(self):
        req = ExplainRequest(
            ring_id="ring-1",
            account_ids=["a1", "a2", "a3"],
            shared_attrs=["device_id"],
        )
        text = generate_explanation(req)
        assert "ring-1" in text
        assert "3 accounts" in text
        assert "device fingerprint" in text
        assert "window" not in text

    def test_single_shared_attr_with_time_window(self):
        req = ExplainRequest(
            ring_id="ring-2",
            account_ids=["a1", "a2"],
            shared_attrs=["ip"],
            time_window_days=5,
        )
        text = generate_explanation(req)
        assert "5-day window" in text
        assert "IP address" in text

    def test_multiple_shared_attrs(self):
        req = ExplainRequest(
            ring_id="ring-3",
            account_ids=["a1", "a2"],
            shared_attrs=["device_id", "bank_ref"],
        )
        text = generate_explanation(req)
        assert "device fingerprint" in text
        assert "bank account" in text

    def test_three_shared_attrs_uses_oxford_comma(self):
        req = ExplainRequest(
            ring_id="ring-4",
            account_ids=["a1", "a2"],
            shared_attrs=["device_id", "ip", "bank_ref"],
        )
        text = generate_explanation(req)
        assert "device fingerprint, IP address, and bank account" in text

    def test_null_time_window_omits_time_evidence(self):
        req = ExplainRequest(
            ring_id="ring-5",
            account_ids=["a1", "a2"],
            shared_attrs=["device_id"],
            time_window_days=None,
        )
        text = generate_explanation(req)
        assert "window" not in text
        assert "day" not in text

    def test_zero_time_window_omits_time_evidence(self):
        req = ExplainRequest(
            ring_id="ring-6",
            account_ids=["a1", "a2"],
            shared_attrs=["ip"],
            time_window_days=0,
        )
        text = generate_explanation(req)
        assert "window" not in text

class TestExplainEndpoint:
    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_explain_valid_request(self, client):
        payload = {
            "ring_id": "ring-1",
            "account_ids": ["a1", "a7", "a12"],
            "shared_attrs": ["device_id"],
            "time_window_days": 3,
        }
        res = client.post("/explain", json=payload)
        assert res.status_code == 200
        data = res.json()
        assert "explanation" in data
        assert "ring-1" in data["explanation"]
        assert "3-day window" in data["explanation"]

    def test_explain_empty_accounts_400(self, client):
        payload = {
            "ring_id": "ring-1",
            "account_ids": [],
            "shared_attrs": ["device_id"],
        }
        res = client.post("/explain", json=payload)
        assert res.status_code == 400

    def test_explain_empty_attrs_400(self, client):
        payload = {
            "ring_id": "ring-1",
            "account_ids": ["a1"],
            "shared_attrs": [],
        }
        res = client.post("/explain", json=payload)
        assert res.status_code == 400

    def test_explain_no_time_window_field(self, client):
        payload = {
            "ring_id": "ring-1",
            "account_ids": ["a1", "a2"],
            "shared_attrs": ["bank_ref"],
        }
        res = client.post("/explain", json=payload)
        assert res.status_code == 200
        assert "window" not in res.json()["explanation"]
