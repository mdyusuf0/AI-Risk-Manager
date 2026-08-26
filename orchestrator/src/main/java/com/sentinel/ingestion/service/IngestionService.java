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

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public IngestionResult ingest(List<RawTransaction> rawTransactions) {
        List<CleanTransaction> cleanTransactions = new ArrayList<>();
        Map<String, AccountBuilder> accountBuilders = new LinkedHashMap<>();

        Instant earliest = null;
        Instant latest = null;
        int skippedRows = 0;

        for (RawTransaction raw : rawTransactions) {
            if (!isValid(raw)) {
                skippedRows++;
                log.warn("Skipping bad row: missing id/amount/accountId");
                continue;
            }

            String cleanDeviceId = blankToNull(raw.getDeviceId());
            String cleanIp = blankToNull(raw.getIp());
            String bankRef = BankRefHasher.hash(raw.getBankAccount());

            CleanTransaction cleanTx = new CleanTransaction(
                    raw.getId(),
                    raw.getAmount(),
                    cleanDeviceId,
                    cleanIp,
                    raw.getAccountId()
            );
            cleanTransactions.add(cleanTx);

            accountBuilders
                    .computeIfAbsent(raw.getAccountId(), AccountBuilder::new)
                    .mergeAttributes(cleanDeviceId, cleanIp, bankRef);

            Instant ts = parseTimestamp(raw.getTransactionTime());
            if (ts != null) {
                accountBuilders.get(raw.getAccountId()).mergeTimestamp(ts);
                if (earliest == null || ts.isBefore(earliest)) {
                    earliest = ts;
                }
                if (latest == null || ts.isAfter(latest)) {
                    latest = ts;
                }
            }
        }

        List<CleanAccount> cleanAccounts = accountBuilders.values().stream()
                .map(AccountBuilder::build)
                .toList();

        IngestionResult result = new IngestionResult(
                cleanTransactions, cleanAccounts, earliest, latest, skippedRows
        );

        log.info("Ingestion done: {} txs, {} accs", cleanTransactions.size(), cleanAccounts.size());
        return result;
    }

    private boolean isValid(RawTransaction raw) {
        return raw.getId() != null && !raw.getId().isBlank()
                && raw.getAmount() != null
                && raw.getAccountId() != null && !raw.getAccountId().isBlank();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();

        // try iso datetime first
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {}

        // simple date
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
        } catch (DateTimeParseException ignored) {}

        // epoch sec
        try {
            long epoch = Long.parseLong(trimmed);
            if (epoch > 946684800L && epoch < 4102444800L) {
                return Instant.ofEpochSecond(epoch);
            }
        } catch (NumberFormatException ignored) {}

        return null;
    }

    private static class AccountBuilder {
        private final String accountId;
        private final Set<String> deviceIds = new LinkedHashSet<>();
        private final Set<String> ips = new LinkedHashSet<>();
        private final Set<String> bankRefs = new LinkedHashSet<>();
        private Instant earliest = null;
        private Instant latest = null;

        AccountBuilder(String accountId) {
            this.accountId = accountId;
        }

        void mergeAttributes(String newDeviceId, String newIp, String newBankRef) {
            if (newDeviceId != null) this.deviceIds.add(newDeviceId);
            if (newIp != null) this.ips.add(newIp);
            if (newBankRef != null) this.bankRefs.add(newBankRef);
        }

        void mergeTimestamp(Instant ts) {
            if (ts == null) return;
            if (earliest == null || ts.isBefore(earliest)) earliest = ts;
            if (latest == null || ts.isAfter(latest)) latest = ts;
        }

        CleanAccount build() {
            return new CleanAccount(accountId, deviceIds, ips, bankRefs, earliest, latest);
        }
    }
}
