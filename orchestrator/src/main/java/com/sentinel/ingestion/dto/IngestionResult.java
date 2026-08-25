package com.sentinel.ingestion.dto;

import java.time.Instant;
import java.util.List;

// wrapper for ingestion service output
public class IngestionResult {

    private final List<CleanTransaction> transactions;
    private final List<CleanAccount> accounts;
    private final Instant earliestTimestamp;
    private final Instant latestTimestamp;
    private final int skippedRows;

    public IngestionResult(List<CleanTransaction> transactions,
                           List<CleanAccount> accounts,
                           Instant earliestTimestamp,
                           Instant latestTimestamp,
                           int skippedRows) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.earliestTimestamp = earliestTimestamp;
        this.latestTimestamp = latestTimestamp;
        this.skippedRows = skippedRows;
    }

    public List<CleanTransaction> getTransactions() { return transactions; }
    public List<CleanAccount> getAccounts() { return accounts; }
    public Instant getEarliestTimestamp() { return earliestTimestamp; }
    public Instant getLatestTimestamp() { return latestTimestamp; }
    public int getSkippedRows() { return skippedRows; }

    @Override
    public String toString() {
        return "IngestionResult{transactions=" + transactions.size() +
               ", accounts=" + accounts.size() +
               ", timeRange=" + earliestTimestamp + " -> " + latestTimestamp +
               ", skippedRows=" + skippedRows + "}";
    }
}
