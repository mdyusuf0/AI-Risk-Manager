package com.sentinel.orchestrator.service;

import com.sentinel.ingestion.dto.*;
import com.sentinel.ingestion.service.IngestionService;
import com.sentinel.orchestrator.client.PythonAgentClient;
import com.sentinel.orchestrator.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @SuppressWarnings("unchecked")
    public OrchestratorResponse analyze(AnalyzeRequest request) {
        // step 1: ingest raw data
        IngestionResult ingested = ingestionService.ingest(request.getTransactions());
        log.info("ingestion done: {} txs, {} accounts, {} skipped",
                ingested.getTransactions().size(),
                ingested.getAccounts().size(),
                ingested.getSkippedRows());

        // step 2: call scoring + graph in parallel
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // build request payloads
        Map<String, Object> scorePayload = Map.of("transactions",
                ingested.getTransactions().stream().map(tx -> Map.of(
                        "id", tx.getId(),
                        "amount", tx.getAmount(),
                        "device_id", (Object) Optional.ofNullable(tx.getDeviceId()).orElse(""),
                        "ip", (Object) Optional.ofNullable(tx.getIp()).orElse(""),
                        "account_id", tx.getAccountId()
                )).toList());

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
        // build tx -> account map
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

        // step 4: parse rings
        List<Map<String, Object>> rings = (List<Map<String, Object>>) graphResult.get("rings");

        // build account -> best ring mapping (highest ring_score, tiebreak by ring_id)
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

        // step 5: compute time_window_days from ingestion timestamps
        Integer timeWindowDays = null;
        if (ingested.getEarliestTimestamp() != null && ingested.getLatestTimestamp() != null) {
            long days = ChronoUnit.DAYS.between(ingested.getEarliestTimestamp(), ingested.getLatestTimestamp());
            timeWindowDays = (int) Math.max(1, days);
        }

        // step 6: call /explain for each flagged ring
        Set<String> explainedRingIds = new HashSet<>();
        Map<String, String> ringExplanations = new HashMap<>();

        for (Map<String, Object> ring : rings) {
            String ringId = (String) ring.get("ring_id");
            double ringScore = ((Number) ring.get("ring_score")).doubleValue();
            if (ringScore < ringScoreThreshold) continue;
            if (explainedRingIds.contains(ringId)) continue;
            explainedRingIds.add(ringId);

            Map<String, Object> explainPayload = new HashMap<>();
            explainPayload.put("ring_id", ringId);
            explainPayload.put("account_ids", ring.get("account_ids"));
            explainPayload.put("shared_attrs", ring.get("shared_attrs"));
            if (timeWindowDays != null) {
                explainPayload.put("time_window_days", timeWindowDays);
            }

            try {
                Map<String, Object> explainResult = pythonClient.post("/explain", explainPayload);
                ringExplanations.put(ringId, (String) explainResult.get("explanation"));
            } catch (Exception e) {
                log.warn("explain call failed for ring {}: {}", ringId, e.getMessage());
                ringExplanations.put(ringId, "Flagged ring " + ringId);
            }
        }

        // step 7: build verdict
        List<VerdictItem> verdict = new ArrayList<>();
        Set<String> allAccountIds = new HashSet<>();
        for (CleanAccount acc : ingested.getAccounts()) {
            allAccountIds.add(acc.getAccountId());
        }

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

        // sort verdict by account_id
        verdict.sort(Comparator.comparing(VerdictItem::getAccountId));

        // step 8: call /evaluate if ground truth provided
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

            try {
                Map<String, Object> evalResult = pythonClient.post("/evaluate", evalPayload);
                metrics = new MetricsResult(
                        ((Number) evalResult.get("precision")).doubleValue(),
                        ((Number) evalResult.get("recall")).doubleValue(),
                        ((Number) evalResult.get("false_positive_cost_estimate")).doubleValue(),
                        currency
                );
            } catch (Exception e) {
                log.warn("evaluation failed: {}", e.getMessage());
            }
        }

        return new OrchestratorResponse(verdict, metrics);
    }
}
