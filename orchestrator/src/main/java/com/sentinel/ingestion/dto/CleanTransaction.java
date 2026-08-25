package com.sentinel.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A cleaned transaction ready to be sent to the Python baseline scoring
 * endpoint: POST /score/baseline.
 *
 * Every @JsonProperty annotation here maps to the exact field name in
 * API_CONTRACT.md. This is how we enforce snake_case on the wire even
 * though Java fields use camelCase internally.
 *
 * Think of it this way:
 *   Java side:   cleanTx.getDeviceId()          → camelCase (Java convention)
 *   JSON wire:   { "device_id": "d1" }           → snake_case (API contract)
 *   Python side: tx["device_id"]                 → snake_case (Python convention)
 *
 * The @JsonProperty bridge makes both sides happy without either side
 * breaking its own naming conventions.
 */
public class CleanTransaction {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("amount")
    private final double amount;

    /**
     * Nullable — if the raw data had no device fingerprint, this is null.
     * Per API contract: null device_id is never treated as shared evidence.
     */
    @JsonProperty("device_id")
    private final String deviceId;

    /**
     * Nullable — same null semantics as deviceId.
     */
    @JsonProperty("ip")
    private final String ip;

    @JsonProperty("account_id")
    private final String accountId;

    public CleanTransaction(String id, double amount, String deviceId,
                            String ip, String accountId) {
        this.id = id;
        this.amount = amount;
        this.deviceId = deviceId;
        this.ip = ip;
        this.accountId = accountId;
    }

    // ── Getters (no setters — this DTO is immutable after construction) ──

    public String getId() { return id; }
    public double getAmount() { return amount; }
    public String getDeviceId() { return deviceId; }
    public String getIp() { return ip; }
    public String getAccountId() { return accountId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CleanTransaction that = (CleanTransaction) o;
        return Double.compare(that.amount, amount) == 0
                && Objects.equals(id, that.id)
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(ip, that.ip)
                && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount, deviceId, ip, accountId);
    }

    @Override
    public String toString() {
        return "CleanTransaction{id='" + id + "', amount=" + amount +
               ", device_id=" + deviceId + ", ip=" + ip +
               ", account_id='" + accountId + "'}";
    }
}
