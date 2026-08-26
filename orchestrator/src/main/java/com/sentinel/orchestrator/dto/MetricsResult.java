package com.sentinel.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MetricsResult {

    @JsonProperty("precision")
    private double precision;

    @JsonProperty("recall")
    private double recall;

    @JsonProperty("false_positive_cost_estimate")
    private double falsePositiveCostEstimate;

    @JsonProperty("currency")
    private String currency;

    public MetricsResult() {}

    public MetricsResult(double precision, double recall, double falsePositiveCostEstimate, String currency) {
        this.precision = precision;
        this.recall = recall;
        this.falsePositiveCostEstimate = falsePositiveCostEstimate;
        this.currency = currency;
    }

    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getFalsePositiveCostEstimate() { return falsePositiveCostEstimate; }
    public String getCurrency() { return currency; }
}
