"""
Pydantic models for the Baseline Scoring Agent.

JAVA PARALLEL:
  These are the Python equivalent of your Java DTOs (CleanTransaction, etc.).
  But here's the key difference:

  In Java, you write:
    - A DTO class (fields + getters + @JsonProperty)
    - Jackson handles serialization/deserialization separately
    - Validation is a separate concern (@Valid, @NotNull, etc.)

  In Python/Pydantic, ONE class does ALL THREE:
    - Defines the fields (like a DTO)
    - Automatically validates incoming JSON (rejects bad data with 422)
    - Automatically serializes to JSON on the way out

  So when you see `class Transaction(BaseModel)`, think:
    "This is my DTO + my validation + my serializer, all in one."

API CONTRACT ALIGNMENT:
  Field names here MUST match API_CONTRACT.md exactly.
  Python already uses snake_case, so no equivalent of @JsonProperty is needed —
  the field names are already correct on the wire.
"""

from pydantic import BaseModel, Field
from typing import Optional


# ── Request Models ────────────────────────────────────────────────────────

class Transaction(BaseModel):
    """
    A single cleaned transaction — matches the API contract for /score/baseline.

    Python type hints work like Java generics but for individual variables:
      str        → String
      float      → double
      Optional[str] → @Nullable String  (can be None, which is Python's null)
    """
    id: str                          # Unique transaction identifier (required)
    amount: float                    # Transaction amount (required, positive)
    device_id: Optional[str] = None  # Device fingerprint — None if unknown
    ip: Optional[str] = None         # IPv4 address — None if unknown
    account_id: str                  # Account that initiated the transaction


class BaselineScoreRequest(BaseModel):
    """
    The full request body for POST /score/baseline.

    In Java terms, this is like a wrapper DTO:
      public class BaselineScoreRequest {
          @JsonProperty("transactions")
          private List<Transaction> transactions;
      }

    Pydantic does this in one line: `transactions: list[Transaction]`
    The list[Transaction] means "a JSON array of Transaction objects" —
    Pydantic will validate EACH item in the list automatically.
    """
    transactions: list[Transaction]


# ── Response Models ───────────────────────────────────────────────────────

class TransactionScore(BaseModel):
    """
    A single transaction's risk score — returned in the /score/baseline response.
    """
    id: str                                              # Matches input transaction ID
    risk_score: float = Field(ge=0.0, le=1.0)           # 0.0 = safe, 1.0 = very risky

    # Field(ge=0.0, le=1.0) is like @Min(0) @Max(1) in Java —
    # Pydantic will reject any value outside this range.


class BaselineScoreResponse(BaseModel):
    """
    The full response body for POST /score/baseline.
    """
    scores: list[TransactionScore]
