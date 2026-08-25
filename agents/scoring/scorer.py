"""
Baseline Scoring Logic — the "brain" of the Baseline Scoring Agent.

WHAT THIS DOES:
  Takes a transaction and returns a risk score between 0.0 and 1.0.
  Higher score = more suspicious.

CURRENT APPROACH (v1 — rule-based heuristic):
  We don't have trained ML models yet (that needs the Kaggle dataset loaded).
  So we use a simple weighted heuristic based on common fraud signals:

  1. Amount risk:  Very high amounts are riskier (but we normalize, not just "big = bad")
  2. Missing device: No device fingerprint means we can't track the device → riskier
  3. Missing IP:    No IP means we can't trace the origin → riskier
  4. Combined:      Multiple missing signals compound the risk

  This is a PLACEHOLDER scorer. It will be upgraded to a trained scikit-learn
  or XGBoost model once we have data flowing through the pipeline. The API
  shape stays identical — only the internal scoring logic changes.

JAVA PARALLEL:
  This is like a @Service class in Spring Boot — pure business logic,
  no HTTP handling. In Python, it's just a module with functions.
  No class needed (Python doesn't force everything into a class like Java does).

DEFENSE-ONLY:
  ⚠️  This scorer only COMPUTES a risk score. It does NOT flag, block,
  or take any action. The Orchestrator decides what to do with the score.
"""

import math
from scoring.models import Transaction


# ── Configuration ─────────────────────────────────────────────────────────
# These weights control how much each signal contributes to the final score.
# In production, these would come from a config file or trained model.

AMOUNT_WEIGHT = 0.50      # How much does transaction amount affect the score?
MISSING_DEVICE_WEIGHT = 0.25  # Penalty for not having a device fingerprint
MISSING_IP_WEIGHT = 0.15      # Penalty for not having an IP address
BASE_RISK = 0.05              # Everyone starts with a tiny base risk (never truly 0)

# Amount thresholds — these are heuristic, will be replaced by learned thresholds
AMOUNT_LOW = 100.0        # Below this, amount contributes almost no risk
AMOUNT_HIGH = 10000.0     # Above this, amount contributes maximum risk


def score_transaction(tx: Transaction) -> float:
    """
    Compute a risk score for a single transaction.

    Args:
        tx: A validated Transaction object (from Pydantic)

    Returns:
        Float between 0.0 and 1.0 (clamped)

    PYTHON NOTE FOR JAVA DEVS:
      In Java, you'd write: public double scoreTransaction(Transaction tx)
      In Python, `tx: Transaction` is a TYPE HINT — it tells you (and your IDE)
      what type to expect, but Python won't crash if you pass something else.
      Pydantic already validated the data before it gets here, so we're safe.
    """
    risk = BASE_RISK

    # ── Signal 1: Amount risk ─────────────────────────────────────────────
    # Use a sigmoid-like curve to map amount to risk.
    # Low amounts (< $100) → near-zero contribution
    # High amounts (> $10k) → near-maximum contribution
    # This avoids the naive "big number = bad" trap.
    amount_risk = _amount_to_risk(tx.amount)
    risk += amount_risk * AMOUNT_WEIGHT

    # ── Signal 2: Missing device fingerprint ──────────────────────────────
    # If we don't know what device was used, that's suspicious.
    # Legitimate users usually have a trackable device.
    if tx.device_id is None:
        risk += MISSING_DEVICE_WEIGHT

    # ── Signal 3: Missing IP address ──────────────────────────────────────
    # No IP could mean VPN/proxy or data wasn't captured — slightly risky.
    if tx.ip is None:
        risk += MISSING_IP_WEIGHT

    # ── Clamp to [0.0, 1.0] ──────────────────────────────────────────────
    # In Java: Math.max(0.0, Math.min(1.0, risk))
    # In Python: max() and min() are built-in functions, no Math import needed.
    return max(0.0, min(1.0, risk))


def score_batch(transactions: list[Transaction]) -> list[dict]:
    """
    Score a batch of transactions. Returns a list of {id, risk_score} dicts.

    PYTHON NOTE:
      `list[dict]` means "a list of dictionaries." A Python dict is like
      a Java HashMap<String, Object> — key-value pairs. We use dicts here
      because they map directly to JSON objects.
    """
    return [
        {"id": tx.id, "risk_score": round(score_transaction(tx), 4)}
        for tx in transactions
    ]
    # ↑ This is a LIST COMPREHENSION — Python's compact way to build a list.
    # The Java equivalent would be:
    #   transactions.stream()
    #       .map(tx -> Map.of("id", tx.getId(), "risk_score", scoreTransaction(tx)))
    #       .collect(Collectors.toList());
    # Same logic, different syntax.


# ── Private helpers ───────────────────────────────────────────────────────

def _amount_to_risk(amount: float) -> float:
    """
    Maps a transaction amount to a risk contribution between 0.0 and 1.0
    using a logistic (sigmoid) curve.

    WHY SIGMOID:
      A linear mapping would say "$5000 is exactly half as risky as $10000."
      That's too simplistic. In reality:
        - $50 vs $100: barely any difference in risk
        - $5000 vs $10000: both are high-risk, not that different
        - $500 vs $5000: THIS is where the jump matters most
      A sigmoid curve captures this S-shaped relationship naturally.

    MATH NOTE:
      sigmoid(x) = 1 / (1 + e^(-x))
      We shift and scale it so the midpoint is at AMOUNT_HIGH / 2.

    The _ prefix is a Python CONVENTION (not enforced) meaning "private."
    Like `private` in Java, but Python trusts you not to call it from outside.
    """
    if amount <= 0:
        return 0.0

    # Normalize amount to center the sigmoid around the midpoint
    midpoint = (AMOUNT_LOW + AMOUNT_HIGH) / 2
    # Scale factor controls how steep the S-curve is
    scale = (AMOUNT_HIGH - AMOUNT_LOW) / 6  # 6 gives a nice spread

    if scale <= 0:
        return 0.5

    x = (amount - midpoint) / scale
    # math.exp(-x) can overflow for very large x, so we clamp
    try:
        return 1.0 / (1.0 + math.exp(-x))
    except OverflowError:
        return 0.0 if x < 0 else 1.0
