package com.sentinel.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OrchestratorResponse {

    @JsonProperty("verdict")
    private List<VerdictItem> verdict;

    @JsonProperty("metrics")
    private MetricsResult metrics; // null if no ground truth

    public OrchestratorResponse() {}

    public OrchestratorResponse(List<VerdictItem> verdict, MetricsResult metrics) {
        this.verdict = verdict;
        this.metrics = metrics;
    }

    public List<VerdictItem> getVerdict() { return verdict; }
    public MetricsResult getMetrics() { return metrics; }
}
