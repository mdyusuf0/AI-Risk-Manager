import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from graph.models import AccountGraphInput
from graph.detector import detect_rings

class TestGraphDetectorLogic:
    def test_null_or_empty_attributes_create_no_edges(self):
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=[], ips=[], bank_refs=[]),
            AccountGraphInput(account_id="a2", device_ids=[], ips=[], bank_refs=[]),
            AccountGraphInput(account_id="a3", device_ids=["   "], ips=[], bank_refs=[]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_no_shared_attributes_yields_zero_rings(self):
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=["d1"], ips=["1.1.1.1"], bank_refs=["b1"]),
            AccountGraphInput(account_id="a2", device_ids=["d2"], ips=["2.2.2.2"], bank_refs=["b2"]),
            AccountGraphInput(account_id="a3", device_ids=["d3"], ips=["3.3.3.3"], bank_refs=["b3"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_single_shared_device_forms_ring(self):
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
        assert sorted(ring.account_ids) == ["a1", "a2", "a3"]
        assert ring.shared_attrs == ["device_id"]
        assert 0.0 <= ring.ring_score <= 1.0

    def test_multi_attribute_linkage_preserves_all_evidence(self):
        accounts = [
            AccountGraphInput(account_id="a1", device_ids=["d1"]),
            AccountGraphInput(account_id="a2", device_ids=["d1"], bank_refs=["b2"]),
            AccountGraphInput(account_id="a3", bank_refs=["b2"]),
            AccountGraphInput(account_id="a4", device_ids=["d1"]), # Add a4 to ensure cluster >= 3
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 1
        ring = rings[0]
        assert sorted(ring.account_ids) == ["a1", "a2", "a3", "a4"]
        assert "device_id" in ring.shared_attrs
        assert "bank_ref" in ring.shared_attrs

    def test_deterministic_ordering_and_ring_ids(self):
        accounts = [
            AccountGraphInput(account_id="z1", ips=["9.9.9.9"]),
            AccountGraphInput(account_id="z2", ips=["9.9.9.9"]),
            AccountGraphInput(account_id="z3", ips=["9.9.9.9"]),
            AccountGraphInput(account_id="a1", bank_refs=["bank_shared"]),
            AccountGraphInput(account_id="a2", bank_refs=["bank_shared"]),
            AccountGraphInput(account_id="a3", bank_refs=["bank_shared"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 2
        # score for bank_ref ring = 0.40 + 0.25 = 0.65
        # score for ip ring = 0.40 + 0.05 = 0.45 -> capped at 0.55
        # bank_ref ring comes first (higher score)
        assert rings[0].ring_id == "ring-1"
        assert sorted(rings[0].account_ids) == ["a1", "a2", "a3"]
        assert "bank_ref" in rings[0].shared_attrs
        assert rings[1].ring_id == "ring-2"
        assert sorted(rings[1].account_ids) == ["z1", "z2", "z3"]
        assert "ip" in rings[1].shared_attrs

    # NEW TESTS
    def test_ip_only_three_accounts_below_threshold(self):
        accounts = [
            AccountGraphInput(account_id="ip1", ips=["1.1.1.1"]),
            AccountGraphInput(account_id="ip2", ips=["1.1.1.1"]),
            AccountGraphInput(account_id="ip3", ips=["1.1.1.1"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 1
        assert rings[0].ring_score < 0.60
        assert rings[0].ring_score == 0.45 # 0.40 + 0.05

    def test_two_account_weak_link_no_ring(self):
        accounts = [
            AccountGraphInput(account_id="w1", device_ids=["dev_weak"]),
            AccountGraphInput(account_id="w2", device_ids=["dev_weak"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_disconnected_legitimate_accounts(self):
        accounts = [
            AccountGraphInput(account_id="legit1", device_ids=["d1"], ips=["i1"], bank_refs=["b1"]),
            AccountGraphInput(account_id="legit2", device_ids=["d2"], ips=["i2"], bank_refs=["b2"]),
            AccountGraphInput(account_id="legit3", device_ids=["d3"], ips=["i3"], bank_refs=["b3"]),
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 0

    def test_louvain_deterministic_with_seed(self):
        accounts = [
            AccountGraphInput(account_id=f"a{i}", device_ids=["d1"]) for i in range(10)
        ] + [
            AccountGraphInput(account_id=f"b{i}", bank_refs=["b1"]) for i in range(10)
        ]
        rings1 = detect_rings(accounts)
        rings2 = detect_rings(accounts)
        assert [r.account_ids for r in rings1] == [r.account_ids for r in rings2]

    def test_public_shared_ip_not_flagged(self):
        # 10 accounts sharing only IP
        accounts = [
            AccountGraphInput(account_id=f"pub{i}", ips=["public_ip"]) for i in range(10)
        ]
        rings = detect_rings(accounts)
        assert len(rings) == 1
        assert rings[0].ring_score <= 0.55


class TestGraphEndpoint:
    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_detect_rings_valid_request(self, client):
        payload = {
            "accounts": [
                {"account_id": "a1", "device_ids": ["d1"], "ips": ["1.2.3.4"], "bank_refs": ["b1"]},
                {"account_id": "a2", "device_ids": ["d1"], "ips": ["5.6.7.8"], "bank_refs": ["b2"]},
                {"account_id": "a3", "device_ids": ["d1"], "ips": ["9.10.11.12"], "bank_refs": ["b3"]},
            ]
        }
        response = client.post("/graph/detect-rings", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert "rings" in data
        assert len(data["rings"]) == 1
        ring = data["rings"][0]
        assert ring["ring_id"] == "ring-1"
        assert sorted(ring["account_ids"]) == ["a1", "a2", "a3"]
        assert ring["shared_attrs"] == ["device_id"]
        assert 0.0 <= ring["ring_score"] <= 1.0

    def test_detect_rings_empty_accounts_400(self, client):
        response = client.post("/graph/detect-rings", json={"accounts": []})
        assert response.status_code == 400

    def test_detect_rings_invalid_payload_422(self, client):
        response = client.post("/graph/detect-rings", json={"invalid_key": []})
        assert response.status_code == 422

