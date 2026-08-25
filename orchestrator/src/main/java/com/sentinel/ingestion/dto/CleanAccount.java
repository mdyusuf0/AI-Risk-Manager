package com.sentinel.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

// dto for /graph/detect-rings
public class CleanAccount {

    @JsonProperty("account_id")
    private final String accountId;

    @JsonProperty("device_ids")
    private final Set<String> deviceIds;

    @JsonProperty("ips")
    private final Set<String> ips;

    @JsonProperty("bank_refs")
    private final Set<String> bankRefs;

    public CleanAccount(String accountId, Set<String> deviceIds, Set<String> ips,
                        Set<String> bankRefs) {
        this.accountId = accountId;
        this.deviceIds = deviceIds != null ? Collections.unmodifiableSet(deviceIds) : Collections.emptySet();
        this.ips = ips != null ? Collections.unmodifiableSet(ips) : Collections.emptySet();
        this.bankRefs = bankRefs != null ? Collections.unmodifiableSet(bankRefs) : Collections.emptySet();
    }

    public String getAccountId() { return accountId; }
    public Set<String> getDeviceIds() { return deviceIds; }
    public Set<String> getIps() { return ips; }
    public Set<String> getBankRefs() { return bankRefs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CleanAccount that = (CleanAccount) o;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(deviceIds, that.deviceIds)
                && Objects.equals(ips, that.ips)
                && Objects.equals(bankRefs, that.bankRefs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, deviceIds, ips, bankRefs);
    }

    @Override
    public String toString() {
        return "CleanAccount{account_id='" + accountId +
               "', device_ids=" + deviceIds +
               ", ips=" + ips +
               ", bank_refs=" + bankRefs.size() + " hashes}";
    }
}
