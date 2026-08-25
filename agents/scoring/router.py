"""
FastAPI Router for the Baseline Scoring Agent.

JAVA PARALLEL:
  This is like a @RestController in Spring Boot.
  In Spring, you'd write:

    @RestController
    @RequestMapping("/score")
    public class ScoringController {
        @PostMapping("/baseline")
        public BaselineScoreResponse score(@RequestBody BaselineScoreRequest request) {
            return scoringService.score(request);
        }
    }

  In FastAPI, the same thing is:

    router = APIRouter(prefix="/score")

    @router.post("/baseline")
    def score_baseline(request: BaselineScoreRequest):
        ...

  Same concepts (routing, request body, response body), far less boilerplate.

API CONTRACT: POST /score/baseline
  Request:  { "transactions": [ { "id", "amount", "device_id", "ip", "account_id" } ] }
  Response: { "scores": [ { "id", "risk_score" } ] }
"""

from fastapi import APIRouter, HTTPException
from scoring.models import BaselineScoreRequest, BaselineScoreResponse, TransactionScore
from scoring.scorer import score_batch
import logging

# Set up logging — Python's logging module is like SLF4J in Java.
# getLogger(__name__) is like LoggerFactory.getLogger(ScoringRouter.class)
logger = logging.getLogger(__name__)

# APIRouter is like @RequestMapping("/score") — groups related endpoints.
# In Spring, you'd put this on the class. In FastAPI, it's a standalone object.
router = APIRouter(prefix="/score", tags=["Baseline Scoring"])


@router.post("/baseline", response_model=BaselineScoreResponse)
def score_baseline(request: BaselineScoreRequest):
    """
    Score a batch of transactions for fraud risk.

    HOW THIS WORKS (step by step):
      1. FastAPI receives the HTTP POST request
      2. Pydantic automatically parses the JSON body into a BaselineScoreRequest
         (if the JSON doesn't match the model, FastAPI returns 422 automatically —
          you don't write any validation code!)
      3. We call score_batch() which runs the scoring logic on each transaction
      4. We wrap the results in a BaselineScoreResponse and return it
      5. FastAPI automatically serializes it back to JSON

    JAVA COMPARISON:
      In Spring Boot, step 2 would be: @RequestBody + @Valid + Jackson
      In FastAPI, it's just the type hint: `request: BaselineScoreRequest`
      That one type hint does parsing + validation + deserialization.
    """
    # Guard: at least one transaction required
    if not request.transactions:
        raise HTTPException(
            status_code=400,
            detail="Request must contain at least one transaction"
        )

    logger.info(f"Scoring {len(request.transactions)} transactions")

    # Score all transactions
    raw_scores = score_batch(request.transactions)

    # Convert raw dicts to Pydantic response models
    # (This also validates that risk_score is between 0 and 1)
    scores = [TransactionScore(**s) for s in raw_scores]
    # ↑ The ** operator "unpacks" a dict into keyword arguments.
    # TransactionScore(**{"id": "t1", "risk_score": 0.72})
    # is equivalent to:
    # TransactionScore(id="t1", risk_score=0.72)
    # In Java, you'd use a constructor or builder pattern instead.

    logger.info(f"Scoring complete — {len(scores)} scores produced")

    return BaselineScoreResponse(scores=scores)
