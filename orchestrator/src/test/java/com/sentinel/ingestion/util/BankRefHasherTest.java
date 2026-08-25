package com.sentinel.ingestion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BankRefHasher — the SHA-256 hashing utility.
 *
 * These tests verify the contract requirement:
 *   "bank_ref must be a non-reversible token or hash"
 *
 * Key properties we're checking:
 *   1. Deterministic: same input → same output (always)
 *   2. Distinct: different inputs → different outputs
 *   3. Null-safe: null/blank → null (not an empty hash)
 *   4. Trimming: leading/trailing whitespace is ignored
 */
@DisplayName("BankRefHasher")
class BankRefHasherTest {

    @Test
    @DisplayName("produces a 64-character hex string (SHA-256 = 256 bits = 64 hex chars)")
    void hashProduces64CharHexString() {
        String result = BankRefHasher.hash("12345");
        assertThat(result)
                .isNotNull()
                .hasSize(64)
                .matches("[0-9a-f]+"); // only lowercase hex characters
    }

    @Test
    @DisplayName("is deterministic — same input always produces same hash")
    void hashIsDeterministic() {
        assertThat(BankRefHasher.hash("12345"))
                .isEqualTo(BankRefHasher.hash("12345"));
    }

    @Test
    @DisplayName("different inputs produce different hashes")
    void differentInputsProduceDifferentHashes() {
        assertThat(BankRefHasher.hash("12345"))
                .isNotEqualTo(BankRefHasher.hash("12346"));
    }

    @Test
    @DisplayName("null input returns null (not an empty hash)")
    void nullReturnsNull() {
        assertThat(BankRefHasher.hash(null)).isNull();
    }

    @Test
    @DisplayName("empty string returns null")
    void emptyStringReturnsNull() {
        assertThat(BankRefHasher.hash("")).isNull();
    }

    @Test
    @DisplayName("blank string (whitespace only) returns null")
    void blankStringReturnsNull() {
        assertThat(BankRefHasher.hash("   ")).isNull();
    }

    @Test
    @DisplayName("leading/trailing whitespace is trimmed before hashing")
    void trimmingWorks() {
        // " 12345 " should hash the same as "12345"
        assertThat(BankRefHasher.hash(" 12345 "))
                .isEqualTo(BankRefHasher.hash("12345"));
    }
}
