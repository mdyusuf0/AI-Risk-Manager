# API Contract — Sentinel Ring

> **Status:** 🔒 LOCKED — Do not change field names, shapes, or endpoint
> paths without updating this file first and getting team agreement. Every
> agent on both the Java and Python side must be built against this exact spec.
>
> **Revision:** v4 (FINAL) — incorporates multi-value attribute sets (`device_ids`,
> `ips`, `bank_refs`) per account in `/graph/detect-rings` so all observed
> linkage attributes across transactions are preserved without loss.

---

## Overview

| Side | Framework | Port | Role |
|------|-----------|------|------|
| **Spring Boot (Java)** | Spring Boot 3.x | `localhost:8080` | Orchestrator + Ingestion Agent |
| **Python** | FastAPI | `localhost:8000` | Scoring, Graph/Ring Detection, Evaluation, Explainability |

Communication is **plain JSON over HTTP** — Spring Boot's `RestTemplate` or
`WebClient` calls FastAPI directly. No gRPC, no message queues.

---

## Pipeline Topology

Baseline scoring and graph/ring detection run **independently and in
parallel** from the same ingestion output. Neither consumes the other's
results. The Orchestrator merges their outputs afterward.

```
                        ┌──→ POST /score/baseline ──────┐
Ingestion (clean JSON) ─┤                               ├──→ Orchestrator merges
                        └──→ POST /graph/detect-rings ──┘         │
                                                                  │
                                          ┌───────────────────────┘
                                          ▼
                                POST /explain  (once per flagged ring)
                                          │
                                          ▼
                                POST /evaluate (predictions vs. ground truth)
                                          │
                                          ▼
                                   Final Response
```

---

## Flagging & Aggregation Rules

These rules are **contract-level** — both sides must implement them
identically to ensure `/evaluate` can reproduce results.

### Risk-Score Aggregation (transaction → account)

`/score/baseline` returns one score per **transaction**. The final verdict
is per **account**. The Orchestrator aggregates as:

```
account_risk_score = MAX(risk_score) across all transactions for that account
```

This is the `risk_score` value that appears in the final verdict.

### Flagging Decision

An account is flagged (`flagged: true`) if **either** condition is met:

| Condition | Threshold | Rationale |
|-----------|-----------|-----------|
| The account's aggregated `risk_score` ≥ | **0.70** | High individual risk |
| The account belongs to a ring whose `ring_score` ≥ | **0.60** | Part of a suspicious cluster |

Both thresholds are configurable at the Orchestrator level (passed as
Spring Boot application properties), but these are the defaults. If an
account meets both conditions, it is flagged once — not duplicated.

Accounts that meet neither condition have `flagged: false` and are still
included in the evaluation population (see `/evaluate` rules below).

### Multiple-Ring Membership

An account may belong to more than one detected ring. The verdict's
`ring_id` field is a **single value** (not an array). The Orchestrator
picks one ring per account using this deterministic rule:

1. Choose the ring with the **highest `ring_score`**.
2. If tied, choose the ring with the **lexicographically smallest `ring_id`**.

The `/explain` call uses the selected ring's data. Information about other
rings the account belongs to is not lost — it is available in the full
`/graph/detect-rings` response retained by the Orchestrator — but the
verdict surfaces only the most significant one for clarity.

---

## Data Privacy Rules

| Rule | Detail |
|------|--------|
| `bank_ref` is a **non-reversible token or hash** | Never raw account numbers, IBANs, or sort codes. The Ingestion Agent hashes raw banking identifiers before they leave the Java side. |
| Nullable attributes | `device_id`, `ip`, and `bank_ref` may be `null`. A `null` value is **never treated as shared evidence** — two accounts both having `null` for `device_id` does **not** create an edge in the graph. |

---

## Python Endpoints (FastAPI — `localhost:8000`)

### 1. `POST /score/baseline`

Receives cleaned transactions and returns a per-transaction risk score (0–1).

**Request Body**

```json
{
  "transactions": [
    {
      "id": "t1",
      "amount": 4500,
      "device_id": "d1",
      "ip": "1.2.3.4",
      "account_id": "a1"
    }
  ]
}
```

| Field | Type | Required | Nullable | Notes |
|-------|------|----------|----------|-------|
| `transactions` | array | yes | — | One or more transaction objects |
| `transactions[].id` | string | yes | no | Unique transaction identifier |
| `transactions[].amount` | number | yes | no | Transaction amount (positive) |
| `transactions[].device_id` | string | yes | **yes** | Device fingerprint; `null` if unknown |
| `transactions[].ip` | string | yes | **yes** | IPv4 address; `null` if unknown |
| `transactions[].account_id` | string | yes | no | Account that initiated the transaction |

**Response Body**

```json
{
  "scores": [
    {
      "id": "t1",
      "risk_score": 0.72
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `scores` | array | One entry per input transaction, same order |
| `scores[].id` | string | Matches the input `transactions[].id` |
| `scores[].risk_score` | number | Float in range [0.0, 1.0] — higher = riskier |

---

### 2. `POST /graph/detect-rings`

Receives account-level attribute sets (all observed non-null attributes per
account across transactions), builds an internal graph of shared attributes,
runs community detection, and returns discovered rings.

**Request Body**

```json
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
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `accounts` | array | yes | One or more account objects |
| `accounts[].account_id` | string | yes | Unique account identifier |
| `accounts[].device_ids` | array of strings | yes | All non-null device fingerprints observed for this account (can be empty `[]`) |
| `accounts[].ips` | array of strings | yes | All non-null IP addresses observed for this account (can be empty `[]`) |
| `accounts[].bank_refs` | array of strings | yes | All non-null bank account hashes observed for this account (can be empty `[]`) |

**Null / empty handling:** Empty arrays `[]` or `null` values represent unobserved attributes and do **not** create graph edges. Two accounts both having empty `device_ids` do **not** get connected. Only identical, non-empty attribute values create edges.

**Response Body**

```json
{
  "rings": [
    {
      "ring_id": "r1",
      "account_ids": ["a1", "a7", "a12"],
      "shared_attrs": ["device_id"],
      "ring_score": 0.88
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `rings` | array | Zero or more detected rings |
| `rings[].ring_id` | string | **Run-scoped**, deterministic within a single request: format `ring-<index>` where index is the 1-based rank by descending `ring_score`. Not stable across different input sets. |
| `rings[].account_ids` | array of strings | Accounts in this ring, sorted alphabetically for determinism |
| `rings[].shared_attrs` | array of strings | Which non-null attributes linked them (e.g. `"device_id"`, `"ip"`, `"bank_ref"`) |
| `rings[].ring_score` | number | Float in range [0.0, 1.0] — confidence that this cluster is abusive |

---

### 3. `POST /evaluate`

Compares the system's flagged predictions against ground-truth labels and
returns precision, recall, and an estimated false-positive cost.

> **Offline / test-only endpoint.** This endpoint is used during held-out
> evaluation runs where labeled ground truth is available. It is **not**
> called during live or demo inference where no labels exist yet.

**Request Body**

```json
{
  "predictions": [
    { "id": "a1", "flagged": true },
    { "id": "a2", "flagged": false }
  ],
  "ground_truth": [
    { "id": "a1", "is_fraud": true },
    { "id": "a2", "is_fraud": false }
  ],
  "cost_config": {
    "currency": "USD",
    "cost_per_false_positive": 50.0
  }
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `predictions` | array | yes | **Must include every account in the evaluation population** — both `flagged: true` and `flagged: false` records |
| `predictions[].id` | string | yes | Account identifier — **must be unique** within the array |
| `predictions[].flagged` | boolean | yes | `true` if the system flagged this account |
| `ground_truth` | array | yes | Held-out labels — **must cover the same set of IDs** as `predictions` |
| `ground_truth[].id` | string | yes | Account identifier — must be unique, must match a `predictions[].id` |
| `ground_truth[].is_fraud` | boolean | yes | `true` if the account is actually fraudulent |
| `cost_config` | object | no | Optional; defaults shown below if omitted |
| `cost_config.currency` | string | no | Currency code (default: `"USD"`) — used for labeling only |
| `cost_config.cost_per_false_positive` | number | no | Estimated cost per wrongly flagged account (default: `50.0`). Covers support overhead, customer friction, lost trust. |

**Alignment rules:**
- The set of IDs in `predictions` and `ground_truth` must be **identical**.
  If they differ, the endpoint returns `400` with a message listing the
  mismatched IDs.
- Each ID must appear **exactly once** in each array.

**Edge-case behavior:**

| Scenario | `precision` | `recall` | `false_positive_cost_estimate` |
|----------|-------------|----------|-------------------------------|
| No accounts flagged (`flagged: true` count = 0) | `1.0` (vacuous — nothing flagged, nothing wrong) | `0.0` (caught none of the real fraud) | `0.0` |
| All accounts flagged | normal calculation | `1.0` | `count(flagged ∧ ¬is_fraud) × cost_per_false_positive` |
| No actual fraud in ground truth | `0.0` if any flagged, `1.0` if none flagged | `1.0` (vacuous — nothing to miss) | `count(flagged) × cost_per_false_positive` |

**Response Body**

```json
{
  "precision": 0.81,
  "recall": 0.74,
  "false_positive_cost_estimate": 1250.0
}
```

| Field | Type | Notes |
|-------|------|-------|
| `precision` | number | Of accounts flagged, what fraction are actually fraud |
| `recall` | number | Of all actual fraud accounts, what fraction were flagged |
| `false_positive_cost_estimate` | number | `count(flagged ∧ ¬is_fraud) × cost_per_false_positive`, in the configured currency |

**Cost formula:**
```
false_positive_cost_estimate = (number of false positives) × cost_per_false_positive
```

---

### 4. `POST /explain`

Given a flagged ring's details, returns a plain-English explanation of why
the ring was flagged. Accepts an optional time-window parameter so
explanations can include temporal evidence when available.

**Request Body**

```json
{
  "ring_id": "r1",
  "account_ids": ["a1", "a7", "a12"],
  "shared_attrs": ["device_id"],
  "time_window_days": 3
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `ring_id` | string | yes | The ring being explained |
| `account_ids` | array of strings | yes | Accounts in the ring |
| `shared_attrs` | array of strings | yes | Attributes that linked them |
| `time_window_days` | integer | no | Observation window in days. If provided, explanations may reference it (e.g. "over a 3-day window"). If omitted or `null`, explanations must **not** claim any time-based evidence. |

**Where `time_window_days` comes from:** The Ingestion Agent extracts and
cleans a transaction timestamp field from raw data. The Orchestrator
computes `time_window_days` as the span (in whole days) between the
earliest and latest transaction timestamps across the ring's accounts. If
timestamp data is unavailable or unparseable in the raw input, the
Orchestrator **omits** `time_window_days` (sends `null`), and the
explanation must not reference any time-based evidence.

**Response Body**

```json
{
  "explanation": "Flagged: 3 accounts share the same device_id over a 3-day window."
}
```

| Field | Type | Notes |
|-------|------|-------|
| `explanation` | string | Human-readable reason for the flag. Only references time windows when `time_window_days` was provided. |

---

## Spring Boot Side (`localhost:8080`)

### Ingestion Agent

- **Not an HTTP endpoint** — this is the first step in the pipeline.
- Reads raw CSV / input data, cleans and normalizes it, and holds the result
  as internal clean JSON (Java objects / DTOs).
- **Hashes raw bank identifiers** into `bank_ref` tokens before passing data
  downstream — raw banking data never leaves this agent.
- Treats missing/empty `device_id`, `ip`, `bank_ref` values as `null` in the
  clean output.
- Its output becomes the input to the Python endpoints above.

### Orchestrator — Final Response

The Orchestrator calls the Python endpoints as shown in the pipeline
topology above. Key behaviors:

1. Sends ingestion output to `/score/baseline` and `/graph/detect-rings`
   **in parallel** (they are independent).
2. **Aggregates** transaction-level `risk_score` to account-level using
   `MAX(risk_score)` per account.
3. Applies the **flagging rules** (see above) to decide which accounts are
   flagged. For multi-ring accounts, selects one ring per the
   **multiple-ring membership** tie-breaking rule.
4. Calls `/explain` once per **flagged ring** (rings where `ring_score ≥ 0.60`).
5. **If ground-truth labels are supplied** in the request: assembles the
   full prediction set (flagged + unflagged accounts) and calls `/evaluate`.
   **If labels are not supplied** (live/demo mode): skips `/evaluate` and
   returns `metrics: null` in the final response.
6. Returns the final response.

**Final Response Shape**

```json
{
  "verdict": [
    {
      "account_id": "a1",
      "flagged": true,
      "risk_score": 0.72,
      "ring_id": "r1",
      "explanation": "Flagged: 3 accounts share the same device_id over a 3-day window."
    },
    {
      "account_id": "a5",
      "flagged": true,
      "risk_score": 0.85,
      "ring_id": null,
      "explanation": "Flagged: account risk score 0.85 exceeds threshold (0.70)."
    }
  ],
  "metrics": {
    "precision": 0.81,
    "recall": 0.74,
    "false_positive_cost_estimate": 1250.0,
    "currency": "USD"
  }
}
```

| Field | Type | Notes |
|-------|------|-------|
| `verdict` | array | One entry per **flagged** account (unflagged accounts omitted from verdict but included in `/evaluate` call) |
| `verdict[].account_id` | string | The flagged account |
| `verdict[].flagged` | boolean | Always `true` in this array |
| `verdict[].risk_score` | number | `MAX(risk_score)` across the account's transactions (from `/score/baseline`) |
| `verdict[].ring_id` | string or null | The most significant ring (by highest `ring_score`, then lexicographic `ring_id`), or `null` if flagged by risk score alone |
| `verdict[].explanation` | string | From `/explain` for ring-flagged accounts; auto-generated by Orchestrator for score-only flags |
| `metrics` | object or null | From `/evaluate` when ground-truth labels are supplied; `null` in live/demo mode (no labels) |
| `metrics.precision` | number | Precision on the evaluation set |
| `metrics.recall` | number | Recall on the evaluation set |
| `metrics.false_positive_cost_estimate` | number | Estimated cost of false positives |
| `metrics.currency` | string | Currency of the cost estimate |

**Note on score-only flags:** Accounts flagged purely by `risk_score ≥ 0.70`
(not part of any ring) get `ring_id: null` and an Orchestrator-generated
explanation like `"Flagged: account risk score 0.85 exceeds threshold (0.70)."`.
These accounts do **not** trigger a `/explain` call.

---

## Field-Name Consistency Rules

> **This section exists because Java ↔ Python naming mismatches are the #1
> integration bug in hybrid projects.**

| `account_id` | everywhere | `accountId`, `AccountId`, `acct_id` |
| `device_id` | `/score/baseline` (tx level) | `deviceId`, `DeviceId` |
| `device_ids` | `/graph/detect-rings` (account level) | `deviceIds`, `devices` |
| `ip` | `/score/baseline` (tx level) | `ipAddress`, `ip_address`, `IP` |
| `ips` | `/graph/detect-rings` (account level) | `ipAddresses`, `ipList` |
| `bank_ref` | data privacy | `bankRef`, `bank_reference` |
| `bank_refs` | `/graph/detect-rings` (account level) | `bankRefs`, `bankList` |
| `risk_score` | `/score/baseline` response, orchestrator verdict | `riskScore`, `score` |
| `ring_id` | `/graph/detect-rings` response, `/explain` request, orchestrator verdict | `ringId`, `clusterId` |
| `ring_score` | `/graph/detect-rings` response | `ringScore`, `confidence` |
| `account_ids` | `/graph/detect-rings` response, `/explain` request | `accountIds`, `accounts` |
| `shared_attrs` | `/graph/detect-rings` response, `/explain` request | `sharedAttrs`, `attributes` |
| `is_fraud` | `/evaluate` request (ground truth) | `isFraud`, `fraud`, `label` |
| `false_positive_cost_estimate` | `/evaluate` response, orchestrator metrics | `fpCost`, `falsePositiveCost` |
| `cost_per_false_positive` | `/evaluate` request (cost config) | `fpCostPerUnit`, `costPerFP` |
| `time_window_days` | `/explain` request | `timeWindow`, `windowDays`, `days` |

**Java-side note:** Use `@JsonProperty("snake_case_name")` on every DTO field
to ensure Jackson serializes/deserializes with the snake_case names above,
even if your Java fields use camelCase internally.

---

## Error Responses (all Python endpoints)

All endpoints return standard HTTP error codes with a consistent error body:

```json
{
  "detail": "Human-readable error message"
}
```

| Code | Meaning |
|------|---------|
| `400` | Bad request — missing/malformed fields, or ID mismatch between predictions and ground truth |
| `422` | Validation error — FastAPI's automatic Pydantic validation |
| `500` | Internal server error |

---

## Defense-Only Notice

> **This system only flags and explains.** It never auto-blocks, auto-bans,
> or takes autonomous action against any account. A human operator reviews
> every flag before any action is taken. This is by design and must remain
> true across all agents.
