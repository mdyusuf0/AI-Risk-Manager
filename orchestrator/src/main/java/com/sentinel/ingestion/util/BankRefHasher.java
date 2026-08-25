package com.sentinel.ingestion.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BankRefHasher {

    private BankRefHasher() {}

    // sha256 hash raw bank account num
    public static String hash(String rawBankAccount) {
        if (rawBankAccount == null || rawBankAccount.isBlank()) {
            return null;
        }

        try {
            String trimmed = rawBankAccount.trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 missing", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
