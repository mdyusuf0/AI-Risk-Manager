package com.sentinel.ingestion.service;

import com.sentinel.ingestion.dto.*;
import com.sentinel.ingestion.util.BankRefHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for IngestionService — the core Ingestion Agent logic.
 *
 * Organized by concern:
 *   1. Null attribute normalization
 *   2. Bank-reference hashing
 *   3. Malformed row handling
 *   4. Timestamp parsing
 *   5. Account deduplication
 *   6. Defense-only verification
 */
@DisplayName("IngestionService")
class IngestionServiceTest {

    private IngestionService service;

    @BeforeEach
    void setUp() {
        // IngestionService has no dependencies — just instantiate it directly.
        // No need for @SpringBootTest here, which would slow down test startup.
        service = new IngestionService();
    }

    // ── Helper: build a valid RawTransaction with sensible defaults ──────
    private RawTransaction validRaw(String id, String accountId) {
        return new RawTransaction(
                id, 100.0, "device1", "1.2.3.4",
                accountId, "BANK123", "2019-06-15T10:30:00"
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Null attribute normalization
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null attribute normalization")
    class NullNormalization {

        @Test
        @DisplayName("empty deviceId becomes null in CleanTransaction")
        void emptyDeviceIdBecomesNull() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "", "1.2.3.4", "a1", "BANK123", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions()).hasSize(1);
            assertThat(result.getTransactions().get(0).getDeviceId()).isNull();
        }

        @Test
        @DisplayName("blank ip (whitespace only) becomes null")
        void blankIpBecomesNull() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "   ", "a1", "BANK123", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions().get(0).getIp()).isNull();
        }

        @Test
        @DisplayName("empty bankAccount becomes null bank_ref in CleanAccount")
        void emptyBankAccountBecomesNullBankRef() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", "", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getAccounts()).hasSize(1);
            assertThat(result.getAccounts().get(0).getBankRef()).isNull();
        }

        @Test
        @DisplayName("valid (non-blank) attributes are preserved, not nullified")
        void validAttributesPreserved() {
            RawTransaction raw = new RawTransaction(
                    "t1", 500.0, "device-X", "10.0.0.1", "a1", "BANK999", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            CleanTransaction tx = result.getTransactions().get(0);
            assertThat(tx.getDeviceId()).isEqualTo("device-X");
            assertThat(tx.getIp()).isEqualTo("10.0.0.1");
            assertThat(tx.getAmount()).isEqualTo(500.0);

            CleanAccount acct = result.getAccounts().get(0);
            assertThat(acct.getBankRef()).isNotNull().hasSize(64); // SHA-256 hex
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Bank-reference hashing
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bank-reference hashing")
    class BankRefHashing {

        @Test
        @DisplayName("bankAccount is hashed to SHA-256 in CleanAccount.bankRef")
        void bankAccountIsHashed() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", "ACC123", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            // Should match the same hash we'd get from BankRefHasher directly
            String expectedHash = BankRefHasher.hash("ACC123");
            assertThat(result.getAccounts().get(0).getBankRef())
                    .isEqualTo(expectedHash);
        }

        @Test
        @DisplayName("same bankAccount across transactions produces same bankRef")
        void sameBankAccountSameHash() {
            RawTransaction raw1 = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", "SHARED_BANK", null
            );
            RawTransaction raw2 = new RawTransaction(
                    "t2", 200.0, "d2", "5.6.7.8", "a2", "SHARED_BANK", null
            );

            IngestionResult result = service.ingest(List.of(raw1, raw2));

            // Two different accounts with the same raw bank number →
            // they should get the same bankRef hash (this creates a graph edge later)
            assertThat(result.getAccounts().get(0).getBankRef())
                    .isEqualTo(result.getAccounts().get(1).getBankRef());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Malformed row handling
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Malformed row handling")
    class MalformedRows {

        @Test
        @DisplayName("row with null id is skipped")
        void missingIdIsSkipped() {
            RawTransaction raw = new RawTransaction(
                    null, 100.0, "d1", "1.2.3.4", "a1", "BANK", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions()).isEmpty();
            assertThat(result.getSkippedRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("row with null amount is skipped")
        void missingAmountIsSkipped() {
            RawTransaction raw = new RawTransaction(
                    "t1", null, "d1", "1.2.3.4", "a1", "BANK", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions()).isEmpty();
            assertThat(result.getSkippedRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("row with null accountId is skipped")
        void missingAccountIdIsSkipped() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", null, "BANK", null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions()).isEmpty();
            assertThat(result.getSkippedRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("mix of valid and invalid rows — only valid ones in output")
        void mixOfValidAndInvalidRows() {
            List<RawTransaction> rows = List.of(
                    validRaw("t1", "a1"),           // valid
                    new RawTransaction(null, 100.0, "d1", "1.2.3.4", "a2", "B", null), // invalid: no id
                    validRaw("t2", "a2"),           // valid
                    new RawTransaction("t3", null, "d1", "1.2.3.4", "a3", "B", null),  // invalid: no amount
                    validRaw("t4", "a3")            // valid
            );

            IngestionResult result = service.ingest(rows);

            assertThat(result.getTransactions()).hasSize(3);
            assertThat(result.getSkippedRows()).isEqualTo(2);
        }

        @Test
        @DisplayName("empty input list produces empty result with zero skipped")
        void emptyInputProducesEmptyResult() {
            IngestionResult result = service.ingest(Collections.emptyList());

            assertThat(result.getTransactions()).isEmpty();
            assertThat(result.getAccounts()).isEmpty();
            assertThat(result.getSkippedRows()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Timestamp parsing
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timestamp parsing")
    class TimestampParsing {

        @Test
        @DisplayName("ISO-8601 datetime is parsed correctly")
        void isoTimestampParsed() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", null, "2019-06-15T10:30:00"
            );

            IngestionResult result = service.ingest(List.of(raw));

            Instant expected = LocalDateTime.of(2019, 6, 15, 10, 30, 0)
                    .toInstant(ZoneOffset.UTC);
            assertThat(result.getEarliestTimestamp()).isEqualTo(expected);
            assertThat(result.getLatestTimestamp()).isEqualTo(expected);
        }

        @Test
        @DisplayName("simple date (no time) is parsed as start-of-day UTC")
        void simpleDateParsed() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", null, "2019-06-15"
            );

            IngestionResult result = service.ingest(List.of(raw));

            Instant expected = LocalDate.of(2019, 6, 15)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            assertThat(result.getEarliestTimestamp()).isEqualTo(expected);
        }

        @Test
        @DisplayName("epoch seconds (numeric string) is parsed correctly")
        void epochSecondsParsed() {
            // 1560600000 = 2019-06-15T12:00:00Z
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", null, "1560600000"
            );

            IngestionResult result = service.ingest(List.of(raw));

            Instant expected = Instant.ofEpochSecond(1560600000L);
            assertThat(result.getEarliestTimestamp()).isEqualTo(expected);
        }

        @Test
        @DisplayName("invalid timestamp is ignored — row is NOT skipped, timestamps stay null")
        void invalidTimestampIgnored() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", null, "not-a-date"
            );

            IngestionResult result = service.ingest(List.of(raw));

            // Row should still be in the output (it has valid id/amount/accountId)
            assertThat(result.getTransactions()).hasSize(1);
            // But timestamps should be null because the only row had unparseable time
            assertThat(result.getEarliestTimestamp()).isNull();
            assertThat(result.getLatestTimestamp()).isNull();
        }

        @Test
        @DisplayName("time range tracks earliest and latest across multiple rows")
        void timestampRangeTracked() {
            List<RawTransaction> rows = List.of(
                    new RawTransaction("t1", 100.0, "d1", "1.2.3.4", "a1", null, "2019-06-10"),
                    new RawTransaction("t2", 200.0, "d2", "5.6.7.8", "a2", null, "2019-06-15T18:00:00"),
                    new RawTransaction("t3", 300.0, "d3", "9.0.0.1", "a3", null, "2019-06-12")
            );

            IngestionResult result = service.ingest(rows);

            Instant expectedEarliest = LocalDate.of(2019, 6, 10)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant expectedLatest = LocalDateTime.of(2019, 6, 15, 18, 0, 0)
                    .toInstant(ZoneOffset.UTC);

            assertThat(result.getEarliestTimestamp()).isEqualTo(expectedEarliest);
            assertThat(result.getLatestTimestamp()).isEqualTo(expectedLatest);
        }

        @Test
        @DisplayName("null timestamp is gracefully handled — row kept, no timestamp tracked")
        void nullTimestampHandled() {
            RawTransaction raw = new RawTransaction(
                    "t1", 100.0, "d1", "1.2.3.4", "a1", null, null
            );

            IngestionResult result = service.ingest(List.of(raw));

            assertThat(result.getTransactions()).hasSize(1);
            assertThat(result.getEarliestTimestamp()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Account deduplication
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Account deduplication")
    class AccountDeduplication {

        @Test
        @DisplayName("multiple transactions from same account produce one CleanAccount")
        void accountsDeduplicatedById() {
            List<RawTransaction> rows = List.of(
                    new RawTransaction("t1", 100.0, "d1", "1.2.3.4", "a1", "B1", null),
                    new RawTransaction("t2", 200.0, "d1", "1.2.3.4", "a1", "B1", null),
                    new RawTransaction("t3", 300.0, "d1", "1.2.3.4", "a1", "B1", null)
            );

            IngestionResult result = service.ingest(rows);

            // 3 transactions but only 1 unique account
            assertThat(result.getTransactions()).hasSize(3);
            assertThat(result.getAccounts()).hasSize(1);
            assertThat(result.getAccounts().get(0).getAccountId()).isEqualTo("a1");
        }

        @Test
        @DisplayName("first non-null attribute wins when merging across transactions")
        void firstNonNullAttributeKept() {
            List<RawTransaction> rows = List.of(
                    // First tx for a1: deviceId is null
                    new RawTransaction("t1", 100.0, null, "1.2.3.4", "a1", null, null),
                    // Second tx for a1: deviceId is "d1" — this should be kept
                    new RawTransaction("t2", 200.0, "d1", null, "a1", "BANK99", null),
                    // Third tx for a1: deviceId is "d2" — ignored, d1 was first non-null
                    new RawTransaction("t3", 300.0, "d2", "9.9.9.9", "a1", "BANK99", null)
            );

            IngestionResult result = service.ingest(rows);

            CleanAccount account = result.getAccounts().get(0);
            assertThat(account.getDeviceId()).isEqualTo("d1");       // first non-null
            assertThat(account.getIp()).isEqualTo("1.2.3.4");        // from tx1
            assertThat(account.getBankRef())
                    .isEqualTo(BankRefHasher.hash("BANK99"));        // from tx2
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Defense-only verification
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Defense-only verification")
    class DefenseOnly {

        @Test
        @DisplayName("IngestionResult contains no scoring, flagging, or action fields")
        void ingestionDoesNotFlagOrScore() {
            // This test is a design contract check — IngestionResult should
            // only have: transactions, accounts, timestamps, skippedRows.
            // No risk_score, no flagged, no verdict, no action.
            IngestionResult result = service.ingest(List.of(validRaw("t1", "a1")));

            // These are the ONLY things the result should contain
            assertThat(result.getTransactions()).isNotNull();
            assertThat(result.getAccounts()).isNotNull();
            // Verify the DTO does NOT have scoring/flagging methods
            // (If someone accidentally adds them, this test documents the intent)
            assertThat(result.getClass().getDeclaredFields())
                    .extracting("name")
                    .containsExactlyInAnyOrder(
                            "transactions", "accounts",
                            "earliestTimestamp", "latestTimestamp",
                            "skippedRows"
                    );
        }
    }
}
