package com.sentinel.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * A cleaned account record ready to be sent to the Python ring-detection
 * endpoint: POST /graph/detect-rings.
 *
 * Key differences from CleanTransaction:
 *   - This is per-ACCOUNT, not per-transaction (deduplicated by account_id).
 *   - Contains bank_ref (hashed bank reference) — needed for graph edges.
 *   - Does NOT contain amount or transaction id — those are transaction-level.
 *
 * bank_ref is a SHA-256 hash of the raw bank account number.
 * Per API_CONTRACT.md → Data Privacy Rules: raw banking data never leaves
 * the Java side. Only the hash crosses the boundary to Python.
 */
public class CleanAccount {

    @JsonProperty("account_id")
    private final String accountId;

    /**
     * Nullable — null means we don't know this account's device.
     * Per contract: two accounts both having null device_id do NOT
     * create a graph edge (null is never shared evidence).
     */
    @JsonProperty("device_id")
    private final String deviceId;

    /** Nullable — same null semantics as deviceId. */
    @JsonProperty("ip")
    private final String ip;

    /**
     * Nullable — SHA-256 hash of the raw bank account number.
     * Null if no bank info was available in the raw data.
     */
    @JsonProperty("bank_ref")
    private final String bankRef;

    public CleanAccount(String accountId, String deviceId, String ip,
                        String bankRef) {
        this.accountId = accountId;
        this.deviceId = deviceId;
        this.ip = ip;
        this.bankRef = bankRef;
    }

    // ── Getters (immutable) ──────────────────────────────────────────────

    public String getAccountId() { return accountId; }
    public String getDeviceId() { return deviceId; }
    public String getIp() { return ip; }
    public String getBankRef() { return bankRef; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CleanAccount that = (CleanAccount) o;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(ip, that.ip)
                && Objects.equals(bankRef, that.bankRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, deviceId, ip, bankRef);
    }

    @Override
    public String toString() {
        return "CleanAccount{account_id='" + accountId +
               "', device_id=" + deviceId +
               ", ip=" + ip +
               ", bank_ref=" + (bankRef != null ? bankRef.substring(0, 8) + "..." : "null") + "}";
    }
}
