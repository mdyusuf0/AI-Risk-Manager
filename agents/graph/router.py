"""
FastAPI Router for the Graph-Builder & Ring-Detection Agent.

API CONTRACT: POST /graph/detect-rings
  Request:  { "accounts": [ { "account_id", "device_ids", "ips", "bank_refs" } ] }
  Response: { "rings": [ { "ring_id", "account_ids", "shared_attrs", "ring_score" } ] }
"""

from fastapi import APIRouter, HTTPException
from graph.models import DetectRingsRequest, DetectRingsResponse
from graph.detector import detect_rings
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/graph", tags=["Graph & Ring Detection"])


@router.post("/detect-rings", response_model=DetectRingsResponse)
def detect_rings_endpoint(request: DetectRingsRequest):
    """
    Detect suspicious account rings based on shared device, IP, or bank attributes.
    """
    if not request.accounts:
        raise HTTPException(
            status_code=400,
            detail="Request must contain at least one account record"
        )

    logger.info(f"Processing graph ring detection for {len(request.accounts)} accounts")
    rings = detect_rings(request.accounts)
    logger.info(f"Detected {len(rings)} suspicious rings")

    return DetectRingsResponse(rings=rings)
