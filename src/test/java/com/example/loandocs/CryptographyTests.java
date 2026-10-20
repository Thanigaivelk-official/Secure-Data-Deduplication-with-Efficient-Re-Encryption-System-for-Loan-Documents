package com.example.loandocs;

import com.example.loandocs.crypto.EncryptionService;
import com.example.loandocs.crypto.FingerprintService;
import com.example.loandocs.crypto.KeyManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CryptographyTests {

    private final String masterKeyBase64 = "u6B1Z7p4W8k3N9x2L5v8R1t4Y7q0W3e6B9m2P5z8K1v=";
    private final KeyManagementService kms = new KeyManagementService(masterKeyBase64);
    private final EncryptionService encryptionService = new EncryptionService(kms);
    private final FingerprintService fingerprintService = new FingerprintService();

    @Test
    @DisplayName("Test 1: SHA-256 canonical fingerprint determinism")
    void testFingerprinting() {
        byte[] payload1 = "Sensitive Loan Applicant Tax Slip".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "Sensitive Loan Applicant Tax Slip".getBytes(StandardCharsets.UTF_8);
        byte[] payload3 = "Different Document Content".getBytes(StandardCharsets.UTF_8);

        String hash1 = fingerprintService.computeFingerprint(payload1);
        String hash2 = fingerprintService.computeFingerprint(payload2);
        String hash3 = fingerprintService.computeFingerprint(payload3);

        assertEquals(hash1, hash2, "Identical content must yield identical SHA-256 fingerprint");
        assertNotEquals(hash1, hash3, "Different content must yield distinct fingerprints");
        assertEquals(64, hash1.length(), "SHA-256 hex string must be 64 characters");
    }

    @Test
    @DisplayName("Test 2: AES-256-GCM authenticated encryption/decryption roundtrip")
    void testEncryptionDecryptionRoundtrip() {
        byte[] originalPlaintext = "CONFIDENTIAL_LOAN_APPLICATION_DATA_2026".getBytes(StandardCharsets.UTF_8);

        EncryptionService.EncryptionResult result = encryptionService.encryptDocument(originalPlaintext);

        assertNotNull(result.getCiphertext());
        assertNotNull(result.getWrappedDekBase64());
        assertNotNull(result.getIvNonceBase64());
        assertFalse(new String(result.getCiphertext()).contains("CONFIDENTIAL_LOAN"), "Ciphertext must not expose plaintext");

        byte[] decrypted = encryptionService.decryptDocument(
                result.getCiphertext(),
                result.getWrappedDekBase64(),
                result.getIvNonceBase64()
        );

        assertArrayEquals(originalPlaintext, decrypted, "Decrypted bytes must match original plaintext");
    }

    @Test
    @DisplayName("Test 3: AES-GCM AEAD detects tampering and rejects compromised ciphertext")
    void testTamperResistance() {
        byte[] originalPlaintext = "Aadhaar Identity Proof Document".getBytes(StandardCharsets.UTF_8);
        EncryptionService.EncryptionResult result = encryptionService.encryptDocument(originalPlaintext);

        byte[] tamperedCiphertext = result.getCiphertext().clone();
        tamperedCiphertext[0] ^= 0x55; // Flip bits

        assertThrows(Exception.class, () -> {
            encryptionService.decryptDocument(
                    tamperedCiphertext,
                    result.getWrappedDekBase64(),
                    result.getIvNonceBase64()
            );
        }, "Tampered ciphertext must cause GCM authentication tag verification failure");
    }
}
