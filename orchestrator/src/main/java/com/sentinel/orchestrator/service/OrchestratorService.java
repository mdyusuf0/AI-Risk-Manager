package com.sentinel.orchestrator.service;

import com.sentinel.ingestion.dto.*;
import com.sentinel.ingestion.service.IngestionService;
import com.sentinel.orchestrator.client.PythonAgentClient;
import com.sentinel.orchestrator.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final IngestionService ingestionService;
    private final PythonAgentClient pythonClient;

    @Value("${sentinel.threshold.risk-score}")
    private double riskScoreThreshold;

    @Value("${sentinel.threshold.ring-score}")
    private double ringScoreThreshold;

    @Value("${sentinel.cost.currency}")
    private String currency;

    @Value("${sentinel.cost.per-false-positive}")
    private double costPerFalsePositive;

    public OrchestratorService(IngestionService ingestionService, PythonAgentClient pythonClient) {
        this.ingestionService = ingestionService;
        this.pythonClient = pythonClient;
    }

    // visible for testing
    void setThresholds(double riskScore, double ringScore, String currency, double costPerFp) {
        this.riskScoreThreshold = riskScore;
        this.ringScoreThreshold = ringScore;
        this.currency = currency;
        this.costPerFalsePositive = costPerFp;
    }

    @SuppressWarnings("unchecked")
    public OrchestratorResponse analyze(AnalyzeRequest request) {
        // step 1: ingest
        IngestionResult ingested = ingestionService.ingest(request.getTransactions());
        log.info("ingestion done: {} txs, {} accounts, {} skipped",
                ingested.getTransactions().size(),
                ingested.getAccounts().size(),
                ingested.getSkippedRows());

        if (ingested.getTransactions().isEmpty()) {
            log.warn("no valid transactions ingested; returning empty verdict");
            return new OrchestratorResponse(Collections.emptyList(), null);
        }

        // step 2: call scoring + graph in parallel
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // FIX 1: use HashMap so nulls are preserved (Map.of rejects null)
        Map<String, Object> scorePayload = Map.of("transactions",
                ingested.getTransactions().stream().map(tx -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", tx.getId());
                    m.put("amount", tx.getAmount());
                    m.put("device_id", tx.getDeviceId());   // null stays null
                    m.put("ip", tx.getIp());                 // null stays null
                    m.put("account_id", tx.getAccountId());
                    return m;
                }).toList());

        Map<String, Object> graphPayload = Map.of("accounts",
                ingested.getAccounts().stream().map(acc -> Map.of(
                        "account_id", acc.getAccountId(),
                        "device_ids", new ArrayList<>(acc.getDeviceIds()),
                        "ips", new ArrayList<>(acc.getIps()),
                        "bank_refs", new ArrayList<>(acc.getBankRefs())
                )).toList());

        Future<Map<String, Object>> scoreFuture = executor.submit(() -> pythonClient.post("/score/baseline", scorePayload));
        Future<Map<String, Object>> graphFuture = executor.submit(() -> pythonClient.post("/graph/detect-rings", graphPayload));

        Map<String, Object> scoreResult;
        Map<String, Object> graphResult;
        try {
            scoreResult = scoreFuture.get(30, TimeUnit.SECONDS);
            graphResult = graphFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("python agent call failed: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
        }

        // step 3: aggregate risk scores per account (MAX)
        List<Map<String, Object>> scores = (List<Map<String, Object>>) scoreResult.get("scores");
        Map<String, Double> accountRiskScores = new HashMap<>();
        Map<String, String> txToAccount = new HashMap<>();
        for (CleanTransaction tx : ingested.getTransactions()) {
            txToAccount.put(tx.getId(), tx.getAccountId());
        }
        for (Map<String, Object> s : scores) {
            String txId = (String) s.get("id");
            double score = ((Number) s.get("risk_score")).doubleValue();
            String accountId = txToAccount.get(txId);
            if (accountId != null) {
                accountRiskScores.merge(accountId, score, Math::max);
            }
        }

        // step 4: parse rings + build account->best ring mapping
        List<Map<String, Object>> rings = (List<Map<String, Object>>) graphResult.get("rings");
        Map<String, Map<String, Object>> accountToBestRing = new HashMap<>();
        Set<String> ringFlaggedAccounts = new HashSet<>();

        for (Map<String, Object> ring : rings) {
            double ringScore = ((Number) ring.get("ring_score")).doubleValue();
            if (ringScore < ringScoreThreshold) continue;

            String ringId = (String) ring.get("ring_id");
            List<String> accountIds = (List<String>) ring.get("account_ids");

            for (String accId : accountIds) {
                ringFlaggedAccounts.add(accId);
                Map<String, Object> existing = accountToBestRing.get(accId);
                if (existing == null) {
                    accountToBestRing.put(accId, ring);
                } else {
                    double existingScore = ((Number) existing.get("ring_score")).doubleValue();
                    if (ringScore > existingScore ||
                            (ringScore == existingScore && ringId.compareTo((String) existing.get("ring_id")) < 0)) {
                        accountToBestRing.put(accId, ring);
                    }
                }
            }
        }

        // FIX 2: build account lookup for per-ring time window computation
        Map<String, CleanAccount> accountLookup = new HashMap<>();
        for (CleanAccount acc : ingested.getAccounts()) {
            accountLookup.put(acc.getAccountId(), acc);
        }

        // step 5: call /explain for each flagged ring with per-ring time window
        Set<String> explainedRingIds = new HashSet<>();
        Map<String, String> ringExplanations = new HashMap<>();

        for (Map<String, Object> ring : rings) {
            String ringId = (String) ring.get("ring_id");
            double ringScore = ((Number) ring.get("ring_score")).doubleValue();
            if (ringScore < ringScoreThreshold) continue;
            if (explainedRingIds.contains(ringId)) continue;
            explainedRingIds.add(ringId);

            // compute time window from this ring's accounts only
            Integer ringTimeWindowDays = computeRingTimeWindow(
                    (List<String>) ring.get("account_ids"), accountLookup);

            Map<String, Object> explainPayload = new HashMap<>();
            explainPayload.put("ring_id", ringId);
            explainPayload.put("account_ids", ring.get("account_ids"));
            explainPayload.put("shared_attrs", ring.get("shared_attrs"));
            if (ringTimeWindowDays != null) {
                explainPayload.put("time_window_days", ringTimeWindowDays);
            }

            try {
                Map<String, Object> explainResult = pythonClient.post("/explain", explainPayload);
                ringExplanations.put(ringId, (String) explainResult.get("explanation"));
            } catch (Exception e) {
                log.warn("explain call failed for ring {}: {}", ringId, e.getMessage());
                ringExplanations.put(ringId, "Flagged ring " + ringId);
            }
        }

        // step 6: build verdict
        List<VerdictItem> verdict = new ArrayList<>();
        Set<String> allAccountIds = accountLookup.keySet();

        for (String accountId : allAccountIds) {
            double riskScore = accountRiskScores.getOrDefault(accountId, 0.0);
            boolean inRing = ringFlaggedAccounts.contains(accountId);
            boolean scoreFlag = riskScore >= riskScoreThreshold;

            if (!inRing && !scoreFlag) continue;

            String ringId = null;
            String explanation;

            if (inRing) {
                Map<String, Object> bestRing = accountToBestRing.get(accountId);
                ringId = (String) bestRing.get("ring_id");
                explanation = ringExplanations.getOrDefault(ringId, "Flagged ring " + ringId);
            } else {
                explanation = String.format("Flagged: account risk score %.2f exceeds threshold (%.2f).",
                        riskScore, riskScoreThreshold);
            }

            verdict.add(new VerdictItem(accountId, true, riskScore, ringId, explanation));
        }

        verdict.sort(Comparator.comparing(VerdictItem::getAccountId));

        // step 7: call /evaluate if ground truth provided
        // FIX 4: propagate error if eval fails with ground truth supplied
        MetricsResult metrics = null;
        if (request.getGroundTruth() != null && !request.getGroundTruth().isEmpty()) {
            List<Map<String, Object>> predictions = new ArrayList<>();
            Set<String> flaggedIds = verdict.stream()
                    .map(VerdictItem::getAccountId)
                    .collect(Collectors.toSet());

            for (String accId : allAccountIds) {
                predictions.add(Map.of("id", accId, "flagged", flaggedIds.contains(accId)));
            }

            Map<String, Object> evalPayload = Map.of(
                    "predictions", predictions,
                    "ground_truth", request.getGroundTruth(),
                    "cost_config", Map.of(
                            "currency", currency,
                            "cost_per_false_positive", costPerFalsePositive
                    )
            );

            // don't swallow — caller supplied labels, they need to know if eval failed
            Map<String, Object> evalResult = pythonClient.post("/evaluate", evalPayload);
            metrics = new MetricsResult(
                    ((Number) evalResult.get("precision")).doubleValue(),
                    ((Number) evalResult.get("recall")).doubleValue(),
                    ((Number) evalResult.get("false_positive_cost_estimate")).doubleValue(),
                    currency
            );
        }

        return new OrchestratorResponse(verdict, metrics);
    }

    // FIX 2: compute time window from only a ring's member accounts
    // returns null if no timestamps available, or if span is 0 days
    Integer computeRingTimeWindow(List<String> ringAccountIds, Map<String, CleanAccount> accountLookup) {
        Instant earliest = null;
        Instant latest = null;

        for (String accId : ringAccountIds) {
            CleanAccount acc = accountLookup.get(accId);
            if (acc == null) continue;
            if (acc.getEarliestTimestamp() != null) {
                if (earliest == null || acc.getEarliestTimestamp().isBefore(earliest))
                    earliest = acc.getEarliestTimestamp();
            }
            if (acc.getLatestTimestamp() != null) {
                if (latest == null || acc.getLatestTimestamp().isAfter(latest))
                    latest = acc.getLatestTimestamp();
            }
        }

        if (earliest == null || latest == null) return null;

        long days = ChronoUnit.DAYS.between(earliest, latest);
        if (days <= 0) return null;  // same day or no span → don't claim time evidence

        return (int) days;
    }
}
