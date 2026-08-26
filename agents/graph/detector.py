from typing import List, Dict, Set, Tuple
import networkx as nx
from networkx.algorithms.community import louvain_communities
from graph.models import AccountGraphInput, Ring

def detect_rings(accounts: List[AccountGraphInput]) -> List[Ring]:
    if not accounts:
        return []

    attr_to_accounts: Dict[Tuple[str, str], Set[str]] = {}

    for acc in accounts:
        acc_id = acc.account_id
        for dev in acc.device_ids:
            if dev and dev.strip():
                attr_to_accounts.setdefault(("device_id", dev.strip()), set()).add(acc_id)
        for ip in acc.ips:
            if ip and ip.strip():
                attr_to_accounts.setdefault(("ip", ip.strip()), set()).add(acc_id)
        for bank in acc.bank_refs:
            if bank and bank.strip():
                attr_to_accounts.setdefault(("bank_ref", bank.strip()), set()).add(acc_id)

    G = nx.Graph()
    for acc in accounts:
        G.add_node(acc.account_id)

    attr_weights = {"device_id": 3, "bank_ref": 2, "ip": 1}

    for (attr_type, _), sharing_accounts in attr_to_accounts.items():
        if len(sharing_accounts) > 1:
            acc_list = sorted(list(sharing_accounts))
            for i in range(len(acc_list)):
                for j in range(i + 1, len(acc_list)):
                    u, v = acc_list[i], acc_list[j]
                    if G.has_edge(u, v):
                        G[u][v]["shared_attrs"].add(attr_type)
                        G[u][v]["weight"] = sum(attr_weights[attr] for attr in G[u][v]["shared_attrs"])
                    else:
                        G.add_edge(u, v, shared_attrs={attr_type}, weight=attr_weights[attr_type])

    components = [comp for comp in louvain_communities(G, weight="weight", seed=42) if len(comp) >= 3]

    raw_rings = []
    for comp in components:
        sorted_account_ids = sorted(list(comp))
        comp_shared_attrs: Set[str] = set()
        subgraph = G.subgraph(comp)
        for u, v, data in subgraph.edges(data=True):
            comp_shared_attrs.update(data.get("shared_attrs", set()))

        sorted_shared_attrs = sorted(list(comp_shared_attrs))
        score = _calculate_ring_score(len(comp), comp_shared_attrs)

        raw_rings.append({
            "account_ids": sorted_account_ids,
            "shared_attrs": sorted_shared_attrs,
            "ring_score": score
        })

    # sort by score desc, then account ids string
    raw_rings.sort(key=lambda r: (-r["ring_score"], ",".join(r["account_ids"])))

    rings = []
    for idx, r in enumerate(raw_rings, start=1):
        rings.append(
            Ring(
                ring_id=f"ring-{idx}",
                account_ids=r["account_ids"],
                shared_attrs=r["shared_attrs"],
                ring_score=r["ring_score"]
            )
        )

    return rings

def _calculate_ring_score(cluster_size: int, shared_attrs: Set[str]) -> float:
    # RING SCORE FORMULA:
    # base = 0.40
    # + 0.25 if bank_ref shared
    # + 0.20 if device_id shared
    # + 0.05 if ip shared
    # + 0.05 * (cluster_size - 3) for size > 3
    # IP-only cap: if only "ip" in shared_attrs -> cap score at 0.55
    # Clamped to [0.0, 1.0]
    score = 0.40
    if "bank_ref" in shared_attrs:
        score += 0.25
    if "device_id" in shared_attrs:
        score += 0.20
    if "ip" in shared_attrs:
        score += 0.05

    if cluster_size > 3:
        score += 0.05 * (cluster_size - 3)

    if len(shared_attrs) == 1 and "ip" in shared_attrs:
        score = min(score, 0.55)

    return max(0.0, min(1.0, round(score, 4)))
