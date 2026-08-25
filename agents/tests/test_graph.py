"""
Tests for Graph-Builder & Ring-Detection Agent (POST /graph/detect-rings).

VERIFIES:
  1. Null / empty attribute handling — empty sets don't create false edges
  2. No shared attributes — 0 rings returned
  3. Single shared attribute cluster — 3 accounts sharing 1 device
  4. Multi-attribute & overlapping clusters — accounts linked via device + bank_ref
  5. Deterministic ring ID assignment & ordering tie-breaks
  6. API endpoint integration (200, 400 for empty, 422 for invalid format)
"""

import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from graph.models import AccountGraphInput
from graph.detector import detect_rings


# ══════════════════════════════════════════════════════════════════════════
# 1. Detector Unit Tests
# ══════════════════════════════════════════════════════════════════════════

class TestGraphDetectorLogic:
    """Tests for graph construction and ring detection logic."""

    def test_null_or_empty_attributes_create_no_edges(self):
        """Accounts with empty attribute lists must not be linked."""
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=[], ips=[], bank_refs=[]),
            AccountGraphInput(account_id="a2", device_ids=[], ips=[], bank_refs=[]),
            AccountGraphInput(account_id="a3", device_ids=["   "], ips=[], bank_refs=[]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_no_shared_attributes_yields_zero_rings(self):
        """Accounts with unique attributes have no connections."""
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=["d1"], ips=["1.1.1.1"], bank_refs=["b1"]),
            AccountGraphInput(account_id="a2", device_ids=["d2"], ips=["2.2.2.2"], bank_refs=["b2"]),
            AccountGraphInput(account_id="a3", device_ids=["d3"], ips=["3.3.3.3"], bank_refs=["b3"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_single_shared_device_forms_ring(self):
        """3 accounts sharing device_id 'd1' should form 1 ring."""
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=["d1"]),
            AccountGraphInput(account_id="a2", device_ids=["d1"]),
            AccountGraphInput(account_id="a3", device_ids=["d1"]),
            AccountGraphInput(account_id="a4", device_ids=["d99"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 1
        ring = rings[0]
        assert ring.ring_id == "ring-1"
        assert ring.account_ids == ["a1", "a2", "a3"]
        assert ring.shared_attrs == ["device_id"]
        assert 0.0 <= ring.ring_score <= 1.0

    def test_multi_attribute_linkage_preserves_all_evidence(self):
        """
        Account a1 links to a2 via device_id 'd1', and a2 links to a3 via bank_ref 'b2'.
        a1, a2, a3 form a connected cluster, containing both device_id and bank_ref evidence.
        """
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=["d1"]),
            AccountGraphInput(account_id="a2", device_ids=["d1"], bank_refs=["b2"]),
            AccountGraphInput(account_id="a3", bank_refs=["b2"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 1
        ring = rings[0]
        assert ring.account_ids == ["a1", "a2", "a3"]
        assert "device_id" in ring.shared_attrs
        assert "bank_ref" in ring.shared_attrs

    def test_deterministic_ordering_and_ring_ids(self):
        """
        Rings must be sorted by descending ring_score, then account_ids tie-break,
        and assigned ring-1, ring-2 deterministically.
        """
        # Ring A: 2 accounts sharing bank_ref (higher score)
        # Ring B: 2 accounts sharing ip (lower score)
        accounts = [
            AccountGraphInput(account_id="z1", ips=["9.9.9.9"]),
            AccountGraphInput(account_id="z2", ips=["9.9.9.9"]),
            AccountGraphInput(account_id="a1", bank_refs=["bank_shared"]),
            AccountGraphInput(account_id="a2", bank_refs=["bank_shared"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 2

        # Ring 1 should be the higher scoring bank_ref cluster
        assert rings[0].ring_id == "ring-1"
        assert rings[0].account_ids == ["a1", "a2"]
        assert "bank_ref" in rings[0].shared_attrs

        # Ring 2 should be the ip cluster
        assert rings[1].ring_id == "ring-2"
        assert rings[1].account_ids == ["z1", "z2"]
        assert "ip" in rings[1].shared_attrs


# ══════════════════════════════════════════════════════════════════════════
# 2. Endpoint Integration Tests
# ══════════════════════════════════════════════════════════════════════════

class TestGraphEndpoint:
    """Tests for POST /graph/detect-rings endpoint."""

    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_detect_rings_valid_request(self, client):
        payload = {
            "accounts": [
                {"account_id": "a1", "device_ids": ["d1"], "ips": ["1.2.3.4"], "bank_refs": ["b1"]},
                {"account_id": "a2", "device_ids": ["d1"], "ips": ["5.6.7.8"], "bank_refs": ["b2"]},
            ]
        }
        response = client.post("/graph/detect-rings", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert "rings" in data
        assert len(data["rings"]) == 1
        ring = data["rings"][0]
        assert ring["ring_id"] == "ring-1"
        assert ring["account_ids"] == ["a1", "a2"]
        assert ring["shared_attrs"] == ["device_id"]
        assert 0.0 <= ring["ring_score"] <= 1.0

    def test_detect_rings_empty_accounts_400(self, client):
        response = client.post("/graph/detect-rings", json={"accounts": []})
        assert response.status_code == 400

    def test_detect_rings_invalid_payload_422(self, client):
        response = client.post("/graph/detect-rings", json={"invalid_key": []})
        assert response.status_code == 422
