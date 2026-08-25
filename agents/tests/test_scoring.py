"""
Tests for the Baseline Scoring Agent.

JAVA PARALLEL:
  These are like your JUnit 5 tests, but using pytest (Python's most popular
  test framework). Key differences:

  JUnit:                              pytest:
    @Test                               def test_xxx():  (just prefix with test_)
    assertEquals(expected, actual)      assert actual == expected
    @BeforeEach setUp()                 (just call setup code in each test)
    assertThat(x).isCloseTo(y, 0.01)   assert abs(x - y) < 0.01

  pytest is simpler — no annotations, no class required, just functions
  that start with "test_". If an assert fails, pytest shows you the values.

HOW TO RUN:
  cd agents/
  pip install pytest httpx   (httpx is needed for FastAPI test client)
  pytest tests/ -v           (-v = verbose, shows each test name)

WHAT WE'RE TESTING:
  1. Scorer logic (unit tests) — does the heuristic produce sensible scores?
  2. API endpoint (integration tests) — does the FastAPI route work end-to-end?
  3. Contract compliance — do responses match API_CONTRACT.md exactly?
  4. Edge cases — empty transactions, null fields, extreme amounts
"""

import pytest
from fastapi.testclient import TestClient

# ── Import our modules ────────────────────────────────────────────────────
# In Python, imports look like Java imports but use dots for packages:
#   Java:   import com.sentinel.ingestion.service.IngestionService;
#   Python: from scoring.scorer import score_transaction
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from scoring.models import Transaction
from scoring.scorer import score_transaction, score_batch


# ══════════════════════════════════════════════════════════════════════════
# 1. Scorer Logic — Unit Tests
# ══════════════════════════════════════════════════════════════════════════

class TestScorerLogic:
    """Tests for the scoring heuristic itself (no HTTP involved)."""

    def test_low_amount_with_all_fields_gets_low_score(self):
        """A normal-looking transaction should score low (not suspicious)."""
        tx = Transaction(
            id="t1", amount=50.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        score = score_transaction(tx)
        assert 0.0 <= score <= 0.4, f"Expected low risk, got {score}"

    def test_high_amount_gets_higher_score(self):
        """A very high amount should increase the risk score."""
        low_tx = Transaction(
            id="t1", amount=50.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        high_tx = Transaction(
            id="t2", amount=50000.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        assert score_transaction(high_tx) > score_transaction(low_tx)

    def test_missing_device_increases_score(self):
        """Missing device_id should add risk (can't track the device)."""
        with_device = Transaction(
            id="t1", amount=500.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        without_device = Transaction(
            id="t2", amount=500.0, device_id=None, ip="1.2.3.4", account_id="a1"
        )
        assert score_transaction(without_device) > score_transaction(with_device)

    def test_missing_ip_increases_score(self):
        """Missing IP should add risk (can't trace origin)."""
        with_ip = Transaction(
            id="t1", amount=500.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        without_ip = Transaction(
            id="t2", amount=500.0, device_id="d1", ip=None, account_id="a1"
        )
        assert score_transaction(without_ip) > score_transaction(with_ip)

    def test_all_missing_signals_gets_high_score(self):
        """Missing device + missing IP + high amount = very suspicious."""
        tx = Transaction(
            id="t1", amount=20000.0, device_id=None, ip=None, account_id="a1"
        )
        score = score_transaction(tx)
        assert score >= 0.7, f"Expected high risk, got {score}"

    def test_score_always_between_0_and_1(self):
        """Score must be clamped to [0.0, 1.0] regardless of inputs."""
        extreme_tx = Transaction(
            id="t1", amount=999999999.0, device_id=None, ip=None, account_id="a1"
        )
        score = score_transaction(extreme_tx)
        assert 0.0 <= score <= 1.0

    def test_zero_amount_gets_low_score(self):
        """A zero-amount transaction should not be considered high risk."""
        tx = Transaction(
            id="t1", amount=0.0, device_id="d1", ip="1.2.3.4", account_id="a1"
        )
        score = score_transaction(tx)
        assert score < 0.3, f"Expected low risk for zero amount, got {score}"

    def test_score_batch_returns_correct_ids(self):
        """score_batch should return scores with matching IDs."""
        transactions = [
            Transaction(id="t1", amount=100.0, device_id="d1", ip="1.2.3.4", account_id="a1"),
            Transaction(id="t2", amount=200.0, device_id="d2", ip="5.6.7.8", account_id="a2"),
        ]
        results = score_batch(transactions)
        assert len(results) == 2
        assert results[0]["id"] == "t1"
        assert results[1]["id"] == "t2"

    def test_score_batch_all_scores_in_range(self):
        """Every score in a batch must be between 0 and 1."""
        transactions = [
            Transaction(id=f"t{i}", amount=i * 1000, device_id=None, ip=None, account_id=f"a{i}")
            for i in range(10)
        ]
        results = score_batch(transactions)
        for r in results:
            assert 0.0 <= r["risk_score"] <= 1.0, f"Score out of range: {r}"


# ══════════════════════════════════════════════════════════════════════════
# 2. API Endpoint — Integration Tests
# ══════════════════════════════════════════════════════════════════════════

class TestScoringEndpoint:
    """Tests for the POST /score/baseline HTTP endpoint."""

    @pytest.fixture
    def client(self):
        """
        Create a test client for FastAPI.

        JAVA PARALLEL:
          This is like MockMvc in Spring Boot testing:
            mockMvc.perform(post("/score/baseline").content(...))
          But TestClient actually runs the full FastAPI app in-process —
          it's more like @SpringBootTest with TestRestTemplate.
        """
        return TestClient(app)

    def test_valid_request_returns_200(self, client):
        """A well-formed request should return 200 with scores."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": "d1",
                 "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        assert response.status_code == 200

    def test_response_has_correct_shape(self, client):
        """Response must match API contract: { "scores": [ { "id", "risk_score" } ] }"""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": "d1",
                 "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        data = response.json()
        assert "scores" in data
        assert len(data["scores"]) == 1
        assert "id" in data["scores"][0]
        assert "risk_score" in data["scores"][0]
        assert data["scores"][0]["id"] == "t1"

    def test_response_score_in_range(self, client):
        """risk_score must be between 0.0 and 1.0 per API contract."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 5000.0, "device_id": None,
                 "ip": None, "account_id": "a1"}
            ]
        })
        score = response.json()["scores"][0]["risk_score"]
        assert 0.0 <= score <= 1.0

    def test_multiple_transactions(self, client):
        """Batch of transactions should return one score per transaction."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 100.0, "device_id": "d1",
                 "ip": "1.2.3.4", "account_id": "a1"},
                {"id": "t2", "amount": 9999.0, "device_id": None,
                 "ip": None, "account_id": "a2"},
                {"id": "t3", "amount": 50.0, "device_id": "d3",
                 "ip": "10.0.0.1", "account_id": "a3"},
            ]
        })
        assert response.status_code == 200
        scores = response.json()["scores"]
        assert len(scores) == 3
        # IDs should match and be in order
        assert [s["id"] for s in scores] == ["t1", "t2", "t3"]

    def test_nullable_fields_accepted(self, client):
        """device_id and ip can be null (None) per API contract."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 500.0, "device_id": None,
                 "ip": None, "account_id": "a1"}
            ]
        })
        assert response.status_code == 200

    def test_empty_transactions_returns_400(self, client):
        """Empty transaction list should return 400."""
        response = client.post("/score/baseline", json={
            "transactions": []
        })
        assert response.status_code == 400

    def test_missing_required_field_returns_422(self, client):
        """Missing a required field (e.g., 'amount') should return 422 (Pydantic validation)."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "account_id": "a1"}
                # 'amount' is missing — Pydantic should reject this
            ]
        })
        assert response.status_code == 422

    def test_field_names_are_snake_case(self, client):
        """API contract requires snake_case field names (not camelCase)."""
        response = client.post("/score/baseline", json={
            "transactions": [
                {"id": "t1", "amount": 100.0, "device_id": "d1",
                 "ip": "1.2.3.4", "account_id": "a1"}
            ]
        })
        data = response.json()
        score = data["scores"][0]
        # Verify snake_case — these keys must exist exactly as named
        assert "risk_score" in score, "Expected 'risk_score' (snake_case), not 'riskScore'"
        assert "id" in score
