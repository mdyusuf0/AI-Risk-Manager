import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import app
from eval.models import EvaluateRequest, PredictionItem, GroundTruthItem, CostConfig
from eval.evaluator import compute_metrics

class TestEvaluationLogic:
    def test_standard_metrics_calculation(self):
        req = EvaluateRequest(
            predictions=[
                PredictionItem(id="a1", flagged=True),
                PredictionItem(id="a2", flagged=True),
                PredictionItem(id="a3", flagged=False),
                PredictionItem(id="a4", flagged=False),
            ],
            ground_truth=[
                GroundTruthItem(id="a1", is_fraud=True),
                GroundTruthItem(id="a2", is_fraud=False), # FP
                GroundTruthItem(id="a3", is_fraud=True),  # FN
                GroundTruthItem(id="a4", is_fraud=False), # TN
            ],
            cost_config=CostConfig(cost_per_false_positive=100.0)
        )
        res = compute_metrics(req)
        assert res.precision == 0.5  # 1 TP / 2 flagged
        assert res.recall == 0.5     # 1 TP / 2 actual fraud
        assert res.false_positive_cost_estimate == 100.0 # 1 FP * 100

    def test_no_accounts_flagged_vacuous_precision(self):
        req = EvaluateRequest(
            predictions=[
                PredictionItem(id="a1", flagged=False),
                PredictionItem(id="a2", flagged=False),
            ],
            ground_truth=[
                GroundTruthItem(id="a1", is_fraud=True),
                GroundTruthItem(id="a2", is_fraud=False),
            ]
        )
        res = compute_metrics(req)
        assert res.precision == 1.0
        assert res.recall == 0.0
        assert res.false_positive_cost_estimate == 0.0

    def test_mismatched_ids_raises_error(self):
        req = EvaluateRequest(
            predictions=[PredictionItem(id="a1", flagged=True)],
            ground_truth=[GroundTruthItem(id="a2", is_fraud=True)]
        )
        with pytest.raises(ValueError) as exc:
            compute_metrics(req)
        assert "mismatched IDs" in str(exc.value)

    def test_duplicate_prediction_ids_raises_error(self):
        req = EvaluateRequest(
            predictions=[
                PredictionItem(id="a1", flagged=True),
                PredictionItem(id="a1", flagged=False),
            ],
            ground_truth=[GroundTruthItem(id="a1", is_fraud=True)]
        )
        with pytest.raises(ValueError) as exc:
            compute_metrics(req)
        assert "duplicate IDs" in str(exc.value)

class TestEvaluationEndpoint:
    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_evaluate_endpoint_success(self, client):
        payload = {
            "predictions": [
                {"id": "a1", "flagged": True},
                {"id": "a2", "flagged": False}
            ],
            "ground_truth": [
                {"id": "a1", "is_fraud": True},
                {"id": "a2", "is_fraud": False}
            ]
        }
        res = client.post("/evaluate", json=payload)
        assert res.status_code == 200
        data = res.json()
        assert "precision" in data
        assert "recall" in data
        assert "false_positive_cost_estimate" in data
        assert data["precision"] == 1.0
        assert data["recall"] == 1.0
        assert data["false_positive_cost_estimate"] == 0.0

    def test_evaluate_endpoint_mismatch_returns_400(self, client):
        payload = {
            "predictions": [{"id": "a1", "flagged": True}],
            "ground_truth": [{"id": "a99", "is_fraud": True}]
        }
        res = client.post("/evaluate", json=payload)
        assert res.status_code == 400
