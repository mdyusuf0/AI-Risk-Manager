from explain.models import ExplainRequest

ATTR_LABELS = {
    "device_id": "device fingerprint",
    "ip": "IP address",
    "bank_ref": "bank account",
}

def generate_explanation(req: ExplainRequest) -> str:
    n = len(req.account_ids)
    attrs = [ATTR_LABELS.get(a, a) for a in req.shared_attrs]

    if len(attrs) == 1:
        attr_str = attrs[0]
    elif len(attrs) == 2:
        attr_str = f"{attrs[0]} and {attrs[1]}"
    else:
        attr_str = ", ".join(attrs[:-1]) + f", and {attrs[-1]}"

    time_part = ""
    if req.time_window_days is not None and req.time_window_days > 0:
        time_part = f" within a {req.time_window_days}-day window"

    return (
        f"Flagged ring {req.ring_id}: {n} accounts "
        f"({', '.join(req.account_ids)}) share the same {attr_str}{time_part}."
    )
