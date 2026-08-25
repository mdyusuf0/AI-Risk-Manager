"""
Graph-Builder & Ring-Detection Logic using NetworkX.

WHAT THIS DOES:
  1. Takes account attribute records (AccountGraphInput) containing sets of
     device_ids, ips, and bank_refs.
  2. Ignores empty/null attribute sets.
  3. Builds an undirected attribute-sharing graph where nodes are accounts and
     edges represent shared non-null attributes.
  4. Identifies connected components (clusters of size >= 2).
  5. Computes a ring_score (0.0 to 1.0) and aggregated shared_attrs for each ring.
  6. Applies deterministic ordering (by descending ring_score, tie-broken by account_ids)
     and assigns run-scoped ring IDs ("ring-1", "ring-2", etc.).

JAVA PARALLEL:
  In Java, you'd use a graph library like JGraphT to build a Graph<String, DefaultEdge>.
  In Python, NetworkX (import networkx as nx) is the standard graph library.

DEFENSE-ONLY:
  ⚠️  This module only identifies and scores connected clusters. It does NOT
  auto-block or take autonomous action on any account.
"""

from typing import List, Dict, Set, Tuple
import networkx as nx
from graph.models import AccountGraphInput, Ring


def detect_rings(accounts: List[AccountGraphInput]) -> List[Ring]:
    """
    Build graph from account attributes and return detected rings.
    """
    if not accounts:
        return []

    # ── Step 1: Map shared attributes to account IDs ──────────────────────
    # Key: (attr_type, attr_value), Value: set of account_ids sharing this value
    attr_to_accounts: Dict[Tuple[str, str], Set[str]] = {}

    for acc in accounts:
        acc_id = acc.account_id

        # Add all non-empty device_ids
        for dev in acc.device_ids:
            if dev and dev.strip():
                attr_to_accounts.setdefault(("device_id", dev.strip()), set()).add(acc_id)

        # Add all non-empty ips
        for ip in acc.ips:
            if ip and ip.strip():
                attr_to_accounts.setdefault(("ip", ip.strip()), set()).add(acc_id)

        # Add all non-empty bank_refs
        for bank in acc.bank_refs:
            if bank and bank.strip():
                attr_to_accounts.setdefault(("bank_ref", bank.strip()), set()).add(acc_id)

    # ── Step 2: Build NetworkX Graph ──────────────────────────────────────
    G = nx.Graph()

    # Add all account nodes
    for acc in accounts:
        G.add_node(acc.account_id)

    # Add edges between accounts that share at least one attribute
    for (attr_type, _), sharing_accounts in attr_to_accounts.items():
        if len(sharing_accounts) > 1:
            acc_list = sorted(list(sharing_accounts))
            for i in range(len(acc_list)):
                for j in range(i + 1, len(acc_list)):
                    u, v = acc_list[i], acc_list[j]
                    if G.has_edge(u, v):
                        G[u][v]["shared_attrs"].add(attr_type)
                    else:
                        G.add_edge(u, v, shared_attrs={attr_type})

    # ── Step 3: Find Connected Components (Rings) ──────────────────────────
    components = [comp for comp in nx.connected_components(G) if len(comp) >= 2]

    raw_rings = []
    for comp in components:
        sorted_account_ids = sorted(list(comp))

        # Collect all shared attribute types across edges in this component
        comp_shared_attrs: Set[str] = set()
        subgraph = G.subgraph(comp)
        for u, v, data in subgraph.edges(data=True):
            comp_shared_attrs.update(data.get("shared_attrs", set()))

        sorted_shared_attrs = sorted(list(comp_shared_attrs))

        # Compute heuristic ring score
        score = _calculate_ring_score(len(comp), comp_shared_attrs)

        raw_rings.append({
            "account_ids": sorted_account_ids,
            "shared_attrs": sorted_shared_attrs,
            "ring_score": score
        })

    # ── Step 4: Deterministic Sorting & ID Assignment ──────────────────────
    # Sort rule per contract:
    #   1. Descending ring_score
    #   2. Ascending account_ids string representation (tie-breaker)
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
    """
    Calculate confidence score (0.0 to 1.0) that a cluster is an abusive ring.

    Heuristic weighting:
      - Base cluster risk: 0.50
      - Bank account sharing: +0.25 (strongest signal)
      - Device sharing: +0.20 (strong signal)
      - IP sharing: +0.10 (moderate signal)
      - Cluster size bonus: +0.05 per additional account above 2
    """
    score = 0.50

    if "bank_ref" in shared_attrs:
        score += 0.25
    if "device_id" in shared_attrs:
        score += 0.20
    if "ip" in shared_attrs:
        score += 0.10

    # Cluster size bonus
    if cluster_size > 2:
        score += 0.05 * (cluster_size - 2)

    return max(0.0, min(1.0, round(score, 4)))
