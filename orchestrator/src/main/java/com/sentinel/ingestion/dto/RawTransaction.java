package com.sentinel.ingestion.dto;

/**
 * Represents a single row of RAW transaction data — before any cleaning.
 *
 * This is what we read from the Kaggle CSV or any external data source.
 * Fields here can be messy: nulls, blanks, inconsistent date formats, raw
 * bank account numbers that need hashing.
 *
 * The IngestionService transforms these into CleanTransaction + CleanAccount.
 *
 * NOTE: No @JsonProperty here — this class is internal-only. It never
 * crosses the Java–Python boundary. The snake_case annotations only go on
 * the "Clean" DTOs that get serialized to JSON for the Python endpoints.
 */
public class RawTransaction {

    /** Unique transaction identifier (required — rows without this are skipped). */
    private String id;

    /** Transaction amount (required — rows with null amount are skipped). */
    private Double amount;

    /** Device fingerprint — may be null or empty in raw data. */
    private String deviceId;

    /** IP address of the request origin — may be null or empty. */
    private String ip;

    /** Account that initiated this transaction (required). */
    private String accountId;

    /**
     * Raw bank account number / IBAN / sort code.
     * ⚠️  This is SENSITIVE — the Ingestion Agent will hash it into a
     * non-reversible "bank_ref" token before it leaves the Java side.
     * See API_CONTRACT.md → Data Privacy Rules.
     */
    private String bankAccount;

    /**
     * Raw timestamp string — could be ISO-8601, simple date, epoch seconds,
     * or garbage. The Ingestion Agent will try multiple parse strategies.
     * If unparseable, the row is kept (timestamp is just omitted from the
     * time-range calculation).
     */
    private String transactionTime;

    // ── Constructors ─────────────────────────────────────────────────────

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

    // ── Getters & Setters ────────────────────────────────────────────────

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
