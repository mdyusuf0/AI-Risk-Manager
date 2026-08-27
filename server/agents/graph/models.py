from pydantic import BaseModel, Field
from typing import List

class AccountGraphInput(BaseModel):
    account_id: str
    device_ids: List[str] = Field(default_factory=list)
    ips: List[str] = Field(default_factory=list)
    bank_refs: List[str] = Field(default_factory=list)

class DetectRingsRequest(BaseModel):
    accounts: List[AccountGraphInput]

class Ring(BaseModel):
    ring_id: str
    account_ids: List[str]
    shared_attrs: List[str]
    ring_score: float = Field(ge=0.0, le=1.0)

class DetectRingsResponse(BaseModel):
    rings: List[Ring]
