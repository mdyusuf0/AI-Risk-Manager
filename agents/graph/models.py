"""
Pydantic models for the Graph-Builder & Ring-Detection Agent.

API CONTRACT ALIGNMENT (v4):
  POST /graph/detect-rings
  Request:
    {
      "accounts": [
        {
          "account_id": "a1",
          "device_ids": ["d1", "d2"],
          "ips": ["1.2.3.4"],
          "bank_refs": ["b1"]
        }
      ]
    }
  Response:
    {
      "rings": [
        {
          "ring_id": "ring-1",
          "account_ids": ["a1", "a7", "a12"],
          "shared_attrs": ["device_id"],
          "ring_score": 0.88
        }
      ]
    }
"""

from pydantic import BaseModel, Field
from typing import List, Optional


class AccountGraphInput(BaseModel):
    """
    Input account record containing sets of observed attributes.
    Supports multi-value linkage attributes (device_ids, ips, bank_refs).
    """
    account_id: str
    device_ids: List[str] = Field(default_factory=list)
    ips: List[str] = Field(default_factory=list)
    bank_refs: List[str] = Field(default_factory=list)


class DetectRingsRequest(BaseModel):
    """Request payload for POST /graph/detect-rings."""
    accounts: List[AccountGraphInput]


class Ring(BaseModel):
    """
    A detected ring of linked accounts.

    ring_id: Deterministic run-scoped identifier (e.g. 'ring-1', 'ring-2')
    account_ids: List of account IDs in the ring, sorted alphabetically
    shared_attrs: Attributes that created links in this ring (e.g. ['device_id', 'ip'])
    ring_score: Confidence score between 0.0 and 1.0 that this cluster is abusive
    """
    ring_id: str
    account_ids: List[str]
    shared_attrs: List[str]
    ring_score: float = Field(ge=0.0, le=1.0)


class DetectRingsResponse(BaseModel):
    """Response payload for POST /graph/detect-rings."""
    rings: List[Ring]
