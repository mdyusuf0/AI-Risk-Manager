package com.sentinel.ingestion.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes raw bank account identifiers into non-reversible tokens.
 *
 * WHY WE HASH:
 *   The API contract (Data Privacy Rules) requires that bank_ref is a
 *   "non-reversible token or hash — never raw account numbers, IBANs,
 *   or sort codes." This protects customer financial data:
 *     - The Python side never sees the real bank account number.
 *     - If our data is ever leaked, the hashes are useless to attackers.
 *     - Two accounts with the SAME raw bank number produce the SAME hash,
 *       so graph edges still work for ring detection.
 *
 * HOW IT WORKS:
 *   SHA-256 is a one-way hash function: given the output, you can't
 *   reverse it to find the input. It always produces a fixed 64-character
 *   hex string regardless of input length.
 *
 *   "ACC12345" → "a3f2b7c9..." (64 hex chars, always the same for this input)
 *   "ACC12346" → "8e1d4f0a..." (totally different — even 1 char change = new hash)
 *
 * JAVA NOTE:
 *   MessageDigest is Java's built-in crypto API. It's in java.security,
 *   not a third-party library. SHA-256 is guaranteed to be available on
 *   every JVM, so the NoSuchAlgorithmException can never actually happen
 *   in practice — we wrap it in a RuntimeException just to satisfy the
 *   compiler.
 */
public final class BankRefHasher {

    // Private constructor — this is a utility class with only static methods.
    // Same pattern as java.util.Collections or java.lang.Math.
    private BankRefHasher() {
    }

    /**
     * Hashes a raw bank account identifier into a SHA-256 hex string.
     *
     * @param rawBankAccount the raw bank account number (will be trimmed)
     * @return 64-character lowercase hex string, or null if input is null/blank
     */
    public static String hash(String rawBankAccount) {
        // Null or blank → null (per contract: null bank_ref = no evidence)
        if (rawBankAccount == null || rawBankAccount.isBlank()) {
            return null;
        }

        try {
            // Trim whitespace — " ACC123 " and "ACC123" should produce the same hash
            String trimmed = rawBankAccount.trim();

            // Get a SHA-256 digest instance
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Feed the string as UTF-8 bytes into the hasher
            byte[] hashBytes = digest.digest(
                    trimmed.getBytes(StandardCharsets.UTF_8)
            );

            // Convert the raw bytes to a hex string
            // Each byte becomes 2 hex characters → 32 bytes × 2 = 64 chars
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — this should never happen
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Converts a byte array to a lowercase hex string.
     *
     * Example: [0xA3, 0xF2] → "a3f2"
     *
     * In Java, bytes are signed (-128 to 127), so we mask with 0xFF to
     * treat them as unsigned (0 to 255) before converting to hex.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // & 0xFF: treat byte as unsigned
            // %02x: format as 2-digit lowercase hex, zero-padded
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
