package com.sentinel.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

// dto for /score/baseline
public class CleanTransaction {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("amount")
    private final double amount;

    @JsonProperty("device_id")
    private final String deviceId;

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
