from pydantic import BaseModel
from typing import List, Optional

class ExplainRequest(BaseModel):
    ring_id: str
    account_ids: List[str]
    shared_attrs: List[str]
    time_window_days: Optional[int] = None

class ExplainResponse(BaseModel):
    explanation: str
