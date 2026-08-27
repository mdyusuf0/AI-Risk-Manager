package com.sentinel.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

// raw uncleaned tx model from csv/input — accepts both camelCase and snake_case
public class RawTransaction {

    @JsonProperty("id")
    private String id;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("deviceId")
    @JsonAlias({"device_id", "deviceId"})
    private String deviceId;

    @JsonProperty("ip")
    private String ip;

    @JsonProperty("accountId")
    @JsonAlias({"account_id", "accountId"})
    private String accountId;

    @JsonProperty("bankAccount")
    @JsonAlias({"bank_account", "bankAccount", "bank_ref", "bankRef"})
    private String bankAccount;

    @JsonProperty("transactionTime")
    @JsonAlias({"transaction_time", "transactionTime", "timestamp"})
    private String transactionTime;

    public RawTransaction() {
    }

    public RawTransaction(String id, Double amount, String deviceId,
                          String ip, String accountId, String bankAccount,
                          String transactionTime) {
        this.id = id;
        this.amount = amount;
        this.deviceId = deviceId;
        this.ip = ip;
        this.accountId = accountId;
        this.bankAccount = bankAccount;
        this.transactionTime = transactionTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getTransactionTime() { return transactionTime; }
    public void setTransactionTime(String transactionTime) { this.transactionTime = transactionTime; }

    @Override
    public String toString() {
        return "RawTransaction{id='" + id + "', amount=" + amount +
               ", accountId='" + accountId + "'}";
    }
}
