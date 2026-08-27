from fastapi import APIRouter, HTTPException
from explain.models import ExplainRequest, ExplainResponse
from explain.explainer import generate_explanation

router = APIRouter(prefix="", tags=["Explainability"])

@router.post("/explain", response_model=ExplainResponse)
def explain_endpoint(request: ExplainRequest):
    if not request.account_ids:
        raise HTTPException(status_code=400, detail="account_ids required")
    if not request.shared_attrs:
        raise HTTPException(status_code=400, detail="shared_attrs required")

    text = generate_explanation(request)
    return ExplainResponse(explanation=text)
