import math
from scoring.models import Transaction

# weights for heuristic
AMOUNT_WEIGHT = 0.50
MISSING_DEVICE_WEIGHT = 0.25
MISSING_IP_WEIGHT = 0.15
BASE_RISK = 0.05

AMOUNT_LOW = 100.0
AMOUNT_HIGH = 10000.0

def score_transaction(tx: Transaction) -> float:
    risk = BASE_RISK
    amount_risk = _amount_to_risk(tx.amount)
    risk += amount_risk * AMOUNT_WEIGHT

    if tx.device_id is None:
        risk += MISSING_DEVICE_WEIGHT
    if tx.ip is None:
        risk += MISSING_IP_WEIGHT

    return max(0.0, min(1.0, risk))

def score_batch(transactions: list[Transaction]) -> list[dict]:
    return [
        {"id": tx.id, "risk_score": round(score_transaction(tx), 4)}
        for tx in transactions
    ]

def _amount_to_risk(amount: float) -> float:
    if amount <= 0:
        return 0.0
    midpoint = (AMOUNT_LOW + AMOUNT_HIGH) / 2
    scale = (AMOUNT_HIGH - AMOUNT_LOW) / 6
    if scale <= 0:
        return 0.5
    x = (amount - midpoint) / scale
    try:
        return 1.0 / (1.0 + math.exp(-x))
    except OverflowError:
        return 0.0 if x < 0 else 1.0
