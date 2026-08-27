from fastapi import APIRouter, HTTPException
from graph.models import DetectRingsRequest, DetectRingsResponse
from graph.detector import detect_rings
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/graph", tags=["Graph & Ring Detection"])

@router.post("/detect-rings", response_model=DetectRingsResponse)
def detect_rings_endpoint(request: DetectRingsRequest):
    if not request.accounts:
        raise HTTPException(status_code=400, detail="accounts array required")

    rings = detect_rings(request.accounts)
    return DetectRingsResponse(rings=rings)
