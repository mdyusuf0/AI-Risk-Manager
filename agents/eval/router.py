from fastapi import APIRouter, HTTPException
from eval.models import EvaluateRequest, EvaluateResponse
from eval.evaluator import compute_metrics
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="", tags=["Evaluation"])

@router.post("/evaluate", response_model=EvaluateResponse)
def evaluate_endpoint(request: EvaluateRequest):
    try:
        return compute_metrics(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
