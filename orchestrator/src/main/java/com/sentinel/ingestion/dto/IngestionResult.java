package com.sentinel.ingestion.dto;

import java.time.Instant;
import java.util.List;

/**
 * The complete output of the Ingestion Agent.
 *
 * This object is what the Orchestrator receives after ingestion is done.
 * It contains everything needed to call the Python endpoints:
 *   - transactions → POST /score/baseline
 *   - accounts    → POST /graph/detect-rings
 *   - timestamps  → used by Orchestrator to compute time_window_days for /explain
 *
 * This class is INTERNAL — it never crosses the Java–Python boundary directly.
 * The Orchestrator extracts the sub-lists and sends them to the right endpoints.
 */
public class IngestionResult {

    /** Cleaned transactions ready for baseline scoring. */
    private final List<CleanTransaction> transactions;

    /** Deduplicated, cleaned accounts ready for graph/ring detection. */
    private final List<CleanAccount> accounts;

    /**
     * Earliest successfully parsed transaction timestamp across all rows.
     * Null if no timestamps could be parsed from the raw data.
     * Used by the Orchestrator to compute time_window_days = span between
     * earliest and latest, per API_CONTRACT.md → /explain.
     */
    private final Instant earliestTimestamp;

    /**
     * Latest successfully parsed transaction timestamp.
     * Null if no timestamps could be parsed.
     */
    private final Instant latestTimestamp;

    /**
     * How many raw rows were dropped because they were missing required
     * fields (id, amount, or accountId). Logged for auditing — helps us
     * know if the raw data quality is degrading.
     */
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

    // ── Getters ──────────────────────────────────────────────────────────

    public List<CleanTransaction> getTransactions() { return transactions; }
    public List<CleanAccount> getAccounts() { return accounts; }
    public Instant getEarliestTimestamp() { return earliestTimestamp; }
    public Instant getLatestTimestamp() { return latestTimestamp; }
    public int getSkippedRows() { return skippedRows; }

    @Override
    public String toString() {
        return "IngestionResult{transactions=" + transactions.size() +
               ", accounts=" + accounts.size() +
               ", timeRange=" + earliestTimestamp + " → " + latestTimestamp +
               ", skippedRows=" + skippedRows + "}";
    }
}
