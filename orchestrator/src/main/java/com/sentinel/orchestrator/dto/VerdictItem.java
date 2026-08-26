package com.sentinel.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerdictItem {

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("flagged")
    private boolean flagged;

    @JsonProperty("risk_score")
    private double riskScore;

    @JsonProperty("ring_id")
    private String ringId;

    @JsonProperty("explanation")
    private String explanation;

    public VerdictItem() {}

    public VerdictItem(String accountId, boolean flagged, double riskScore,
                       String ringId, String explanation) {
        this.accountId = accountId;
        this.flagged = flagged;
        this.riskScore = riskScore;
        this.ringId = ringId;
        this.explanation = explanation;
    }

    public String getAccountId() { return accountId; }
    public boolean isFlagged() { return flagged; }
    public double getRiskScore() { return riskScore; }
    public String getRingId() { return ringId; }
    public String getExplanation() { return explanation; }
}
