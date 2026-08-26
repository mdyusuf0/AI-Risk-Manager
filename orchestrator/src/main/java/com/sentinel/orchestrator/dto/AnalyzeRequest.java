package com.sentinel.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sentinel.ingestion.dto.RawTransaction;
import java.util.List;
import java.util.Map;

// request body for POST /api/analyze
public class AnalyzeRequest {

    @JsonProperty("transactions")
    private List<RawTransaction> transactions;

    // optional ground truth for offline eval
    @JsonProperty("ground_truth")
    private List<Map<String, Object>> groundTruth;

    public AnalyzeRequest() {}

    public List<RawTransaction> getTransactions() { return transactions; }
    public void setTransactions(List<RawTransaction> transactions) { this.transactions = transactions; }

    public List<Map<String, Object>> getGroundTruth() { return groundTruth; }
    public void setGroundTruth(List<Map<String, Object>> groundTruth) { this.groundTruth = groundTruth; }
}
