from fastapi import APIRouter, HTTPException
from scoring.models import BaselineScoreRequest, BaselineScoreResponse, TransactionScore
from scoring.scorer import score_batch
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/score", tags=["Baseline Scoring"])

@router.post("/baseline", response_model=BaselineScoreResponse)
def score_baseline(request: BaselineScoreRequest):
    if not request.transactions:
        raise HTTPException(status_code=400, detail="at least one transaction required")

    raw_scores = score_batch(request.transactions)
    scores = [TransactionScore(**s) for s in raw_scores]
    return BaselineScoreResponse(scores=scores)
