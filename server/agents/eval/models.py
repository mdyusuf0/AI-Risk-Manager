from pydantic import BaseModel, Field
from typing import List, Optional

class PredictionItem(BaseModel):
    id: str
    flagged: bool

class GroundTruthItem(BaseModel):
    id: str
    is_fraud: bool

class CostConfig(BaseModel):
    currency: Optional[str] = "USD"
    cost_per_false_positive: Optional[float] = 50.0

class EvaluateRequest(BaseModel):
    predictions: List[PredictionItem]
    ground_truth: List[GroundTruthItem]
    cost_config: Optional[CostConfig] = None

class EvaluateResponse(BaseModel):
    precision: float = Field(ge=0.0, le=1.0)
    recall: float = Field(ge=0.0, le=1.0)
    false_positive_cost_estimate: float = Field(ge=0.0)
