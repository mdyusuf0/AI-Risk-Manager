package com.sentinel.ingestion.service;

import com.sentinel.ingestion.dto.*;
import com.sentinel.ingestion.util.BankRefHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * The Ingestion Agent — Stage 1 of the Sentinel Ring pipeline.
 *
 * WHAT IT DOES:
 *   Takes raw, messy transaction data and produces two clean outputs:
 *   1. List<CleanTransaction> — one per valid transaction, for /score/baseline
 *   2. List<CleanAccount>     — one per unique account, for /graph/detect-rings
 *
 * WHAT IT DOES NOT DO:
 *   ⚠️  DEFENSE-ONLY: This agent ONLY cleans and normalizes data.
 *   It does NOT score, flag, block, or take any action on any account.
 *   All decision-making happens downstream (Orchestrator + Python agents).
 *
 * KEY BEHAVIORS (per API_CONTRACT.md):
 *   - Missing/blank device_id, ip, bank_ref → normalized to null
 *   - Raw bank account numbers → SHA-256 hashed into bank_ref
 *   - Malformed rows (missing id/amount/accountId) → dropped with warning log
 *   - Timestamps → parsed from multiple formats, tracked for time_window_days
 *   - Accounts → deduplicated by accountId, keeping first non-null attributes
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /**
     * Main entry point: takes raw transactions, returns clean ingestion output.
     *
     * @param rawTransactions list of raw, uncleaned transaction records
     * @return IngestionResult containing clean transactions, accounts, time range, skip count
     */
    public IngestionResult ingest(List<RawTransaction> rawTransactions) {
        List<CleanTransaction> cleanTransactions = new ArrayList<>();

        // LinkedHashMap preserves insertion order — nice for debugging
        // Key: accountId → Value: mutable holder for first-non-null attributes
        Map<String, AccountBuilder> accountBuilders = new LinkedHashMap<>();

        Instant earliest = null;
        Instant latest = null;
        int skippedRows = 0;

        for (RawTransaction raw : rawTransactions) {
            // ── Step 1: Validate required fields ─────────────────────────
            if (!isValid(raw)) {
                skippedRows++;
                log.warn("Skipping malformed row: {} — missing id, amount, or accountId", raw);
                continue;
            }

            // ── Step 2: Normalize nullable attributes ────────────────────
            // blank/empty → null (contract: null is never shared evidence)
            String cleanDeviceId = blankToNull(raw.getDeviceId());
            String cleanIp = blankToNull(raw.getIp());
            String bankRef = BankRefHasher.hash(raw.getBankAccount());

            // ── Step 3: Build CleanTransaction ───────────────────────────
            CleanTransaction cleanTx = new CleanTransaction(
                    raw.getId(),
                    raw.getAmount(),
                    cleanDeviceId,
                    cleanIp,
                    raw.getAccountId()
            );
            cleanTransactions.add(cleanTx);

            // ── Step 4: Accumulate account-level data (deduplicate) ──────
            // If we've already seen this accountId, update only the null fields
            // with the first non-null value we encounter.
            accountBuilders
                    .computeIfAbsent(raw.getAccountId(), AccountBuilder::new)
                    .mergeAttributes(cleanDeviceId, cleanIp, bankRef);

            // ── Step 5: Parse timestamp, track time range ────────────────
            Instant ts = parseTimestamp(raw.getTransactionTime());
            if (ts != null) {
                if (earliest == null || ts.isBefore(earliest)) {
                    earliest = ts;
                }
                if (latest == null || ts.isAfter(latest)) {
                    latest = ts;
                }
            }
        }

        // ── Step 6: Convert account builders to CleanAccount list ────────
        List<CleanAccount> cleanAccounts = accountBuilders.values().stream()
                .map(AccountBuilder::build)
                .toList();

        IngestionResult result = new IngestionResult(
                cleanTransactions, cleanAccounts, earliest, latest, skippedRows
        );

        log.info("Ingestion complete: {}", result);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════

    /**
     * A row is valid if it has a non-blank id, a non-null amount, and a
     * non-blank accountId. Everything else (deviceId, ip, etc.) is optional.
     */
    private boolean isValid(RawTransaction raw) {
        return raw.getId() != null && !raw.getId().isBlank()
                && raw.getAmount() != null
                && raw.getAccountId() != null && !raw.getAccountId().isBlank();
    }

    /**
     * Converts blank/empty strings to null.
     *
     * WHY: The API contract says nullable attributes should be explicit null,
     * not empty strings. This matters because the Python graph-builder must
     * never treat two empty-string device_ids as "shared" — they're just
     * "unknown." Normalizing to null makes this unambiguous.
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Tries to parse a raw timestamp string using multiple strategies:
     *   1. ISO-8601 datetime: "2019-06-15T10:30:00"
     *   2. Simple date: "2019-06-15" (becomes start-of-day UTC)
     *   3. Epoch seconds: "1560600000" (numeric string)
     *
     * Returns null if all strategies fail — the row is NOT skipped, we just
     * don't include it in the time-range calculation.
     *
     * WHY MULTIPLE FORMATS: Real Kaggle data is messy. The IEEE-CIS dataset
     * uses epoch-style seconds (TransactionDT), other datasets use ISO dates.
     * Rather than hardcoding one format, we try all reasonable options.
     */
    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();

        // Strategy 1: ISO-8601 datetime (e.g. "2019-06-15T10:30:00")
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // Not ISO datetime — try next strategy
        }

        // Strategy 2: Simple date (e.g. "2019-06-15")
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // Not a simple date — try next strategy
        }

        // Strategy 3: Epoch seconds (e.g. "1560600000")
        try {
            long epoch = Long.parseLong(trimmed);
            // Sanity check: epoch seconds should be between year 2000 and 2100
            // to avoid misinterpreting other numbers as timestamps
            if (epoch > 946684800L && epoch < 4102444800L) {
                return Instant.ofEpochSecond(epoch);
            }
        } catch (NumberFormatException ignored) {
            // Not a number — give up
        }

        log.debug("Could not parse timestamp: '{}' — skipping time range for this row", trimmed);
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Inner class: mutable builder for account-level deduplication
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Collects attributes for a single account across multiple transactions.
     *
     * WHY DEDUPLICATE: The Python /graph/detect-rings endpoint expects one
     * record per account. But raw data has one row per transaction, and an
     * account can have many transactions. We merge them here.
     *
     * MERGE RULE: Keep the FIRST non-null value for each attribute.
     * Example: transaction 1 has deviceId=null, transaction 2 has deviceId="d1"
     * → the account gets deviceId="d1" because that's the first non-null.
     */
    private static class AccountBuilder {
        private final String accountId;
        private String deviceId;
        private String ip;
        private String bankRef;

        AccountBuilder(String accountId) {
            this.accountId = accountId;
        }

        /** Merge in new attribute values, keeping first non-null for each. */
        void mergeAttributes(String newDeviceId, String newIp, String newBankRef) {
            if (this.deviceId == null && newDeviceId != null) {
                this.deviceId = newDeviceId;
            }
            if (this.ip == null && newIp != null) {
                this.ip = newIp;
            }
            if (this.bankRef == null && newBankRef != null) {
                this.bankRef = newBankRef;
            }
        }

        CleanAccount build() {
            return new CleanAccount(accountId, deviceId, ip, bankRef);
        }
    }
}
