from typing import Tuple
from eval.models import EvaluateRequest, EvaluateResponse

def compute_metrics(request: EvaluateRequest) -> EvaluateResponse:
    preds = request.predictions
    gt = request.ground_truth

    pred_ids = [p.id for p in preds]
    gt_ids = [g.id for g in gt]

    if len(pred_ids) != len(set(pred_ids)):
        raise ValueError("duplicate IDs in predictions")
    if len(gt_ids) != len(set(gt_ids)):
        raise ValueError("duplicate IDs in ground truth")

    pred_set = set(pred_ids)
    gt_set = set(gt_ids)

    if pred_set != gt_set:
        diff = sorted(list(pred_set ^ gt_set))
        raise ValueError(f"mismatched IDs: {diff}")

    pred_map = {p.id: p.flagged for p in preds}
    gt_map = {g.id: g.is_fraud for g in gt}

    tp = fp = tn = fn = 0
    for node_id, flagged in pred_map.items():
        is_fraud = gt_map[node_id]
        if flagged and is_fraud:
            tp += 1
        elif flagged and not is_fraud:
            fp += 1
        elif not flagged and not is_fraud:
            tn += 1
        else:
            fn += 1

    # precision edge cases
    if tp + fp == 0:
        precision = 1.0
    else:
        precision = tp / (tp + fp)

    # recall edge cases
    if tp + fn == 0:
        recall = 1.0
        if tp + fp > 0:
            precision = 0.0
    else:
        recall = tp / (tp + fn)

    cost_per_fp = 50.0
    if request.cost_config and request.cost_config.cost_per_false_positive is not None:
        cost_per_fp = request.cost_config.cost_per_false_positive

    fp_cost = float(fp * cost_per_fp)

    return EvaluateResponse(
        precision=round(precision, 4),
        recall=round(recall, 4),
        false_positive_cost_estimate=round(fp_cost, 2)
    )
