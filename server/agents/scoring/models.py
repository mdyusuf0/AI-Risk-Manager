from pydantic import BaseModel, Field
from typing import Optional

class Transaction(BaseModel):
    id: str
    amount: float
    device_id: Optional[str] = None
    ip: Optional[str] = None
    account_id: str

class BaselineScoreRequest(BaseModel):
    transactions: list[Transaction]

class TransactionScore(BaseModel):
    id: str
    risk_score: float = Field(ge=0.0, le=1.0)

class BaselineScoreResponse(BaseModel):
    scores: list[TransactionScore]
