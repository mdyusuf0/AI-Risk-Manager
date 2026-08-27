import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from scoring.models import Transaction
from scoring.scorer import score_transaction, score_batch

class TestScorerLogic:
    def test_low_amount_with_all_fields_gets_low_score(self):
        tx = Transaction(id="t1", amount=50.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        score = score_transaction(tx)
        assert 0.0 <= score <= 0.4

    def test_high_amount_gets_higher_score(self):
        low_tx = Transaction(id="t1", amount=50.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        high_tx = Transaction(id="t2", amount=50000.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        assert score_transaction(high_tx) > score_transaction(low_tx)

    def test_missing_device_increases_score(self):
        with_device = Transaction(id="t1", amount=500.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        without_device = Transaction(id="t2", amount=500.0, device_id=None, ip="1.2.3.4", account_id="a1")
        assert score_transaction(without_device) > score_transaction(with_device)

    def test_missing_ip_increases_score(self):
        with_ip = Transaction(id="t1", amount=500.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        without_ip = Transaction(id="t2", amount=500.0, device_id="d1", ip=None, account_id="a1")
        assert score_transaction(without_ip) > score_transaction(with_ip)

    def test_all_missing_signals_gets_high_score(self):
        tx = Transaction(id="t1", amount=20000.0, device_id=None, ip=None, account_id="a1")
        score = score_transaction(tx)
        assert score >= 0.7

    def test_score_always_between_0_and_1(self):
        extreme_tx = Transaction(id="t1", amount=999999999.0, device_id=None, ip=None, account_id="a1")
        score = score_transaction(extreme_tx)
        assert 0.0 <= score <= 1.0

    def test_zero_amount_gets_low_score(self):
        tx = Transaction(id="t1", amount=0.0, device_id="d1", ip="1.2.3.4", account_id="a1")
        score = score_transaction(tx)
        assert score < 0.3

    def test_score_batch_returns_correct_ids(self):
        transactions = [
            Transaction(id="t1", amount=100.0, device_id="d1", ip="1.2.3.4", account_id="a1"),
            Transaction(id="t2", amount=200.0, device_id="d2", ip="5.6.7.8", account_id="a2"),
        ]
        results = score_batch(transactions)
        assert len(results) == 2
        assert results[0]["id"] == "t1"
        assert results[1]["id"] == "t2"

    def test_score_batch_all_scores_in_range(self):
        transactions = [
            Transaction(id=f"t{i}", amount=i * 1000, device_id=None, ip=None, account_id=f"a{i}")
            for i in range(10)
        ]
        results = score_batch(transactions)
        for r in results:
            assert 0.0 <= r["risk_score"] <= 1.0

class TestScoringEndpoint:
    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_valid_request_returns_200(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": "d1", "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        assert response.status_code == 200

    def test_response_has_correct_shape(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": "d1", "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        data = response.json()
        assert "scores" in data
        assert len(data["scores"]) == 1
        assert "id" in data["scores"][0]
        assert "risk_score" in data["scores"][0]
        assert data["scores"][0]["id"] == "t1"

    def test_response_score_in_range(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 5000.0, "device_id": None, "ip": None, "account_id": "a1"}
            ]
        })
        score = response.json()["scores"][0]["risk_score"]
        assert 0.0 <= score <= 1.0

    def test_multiple_transactions(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 100.0, "device_id": "d1", "ip": "1.2.3.4", "account_id": "a1"},
                {"id": "t2", "amount": 9999.0, "device_id": None, "ip": None, "account_id": "a2"},
                {"id": "t3", "amount": 50.0, "device_id": "d3", "ip": "10.0.0.1", "account_id": "a3"},
            ]
        })
        assert response.status_code == 200
        scores = response.json()["scores"]
        assert len(scores) == 3
        assert [s["id"] for s in scores] == ["t1", "t2", "t3"]

    def test_nullable_fields_accepted(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": None, "ip": None, "account_id": "a1"}
            ]
        })
        assert response.status_code == 200

    def test_empty_transactions_returns_400(self, client):
        response = client.post("/score/baseline", json={"transactions": []})
        assert response.status_code == 400

    def test_missing_required_field_returns_422(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [{"id": "t1", "account_id": "a1"}]
        })
        assert response.status_code == 422

    def test_field_names_are_snake_case(self, client):
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 100.0, "device_id": "d1", "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        data = response.json()
        score = data["scores"][0]
        assert "risk_score" in score
        assert "id" in score
