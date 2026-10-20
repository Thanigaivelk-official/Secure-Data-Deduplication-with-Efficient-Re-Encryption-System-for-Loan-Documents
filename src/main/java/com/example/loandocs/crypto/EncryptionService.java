package com.example.loandocs.crypto;

import com.example.loandocs.exception.SecurityCryptoException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final KeyManagementService kms;

    public EncryptionService(KeyManagementService kms) {
        this.kms = kms;
    }

    @Getter
    @Builder
    public static class EncryptionResult {
        private final byte[] ciphertext;
        private final String wrappedDekBase64;
        private final String ivNonceBase64;
    }

    /**
     * Authenticated Encryption of document plaintext bytes using AES-256-GCM.
     * Generates a unique DEK and unique IV. Returns the encrypted bytes and envelope metadata.
     */
    public EncryptionResult encryptDocument(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }
        try {
            SecretKey dek = kms.generateDataEncryptionKey();
            byte[] iv = kms.generateUniqueNonce();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            String wrappedDekBase64 = kms.wrapDataKey(dek);
            String ivNonceBase64 = Base64.getEncoder().encodeToString(iv);

            return EncryptionResult.builder()
                    .ciphertext(ciphertext)
                    .wrappedDekBase64(wrappedDekBase64)
                    .ivNonceBase64(ivNonceBase64)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new SecurityCryptoException("AES-GCM Document encryption failed", e);
        }
    }

    /**
     * Authenticated Decryption of document ciphertext bytes using AES-256-GCM.
     * Automatically verifies GCM AEAD tag to ensure data integrity and authenticity.
     */
    public byte[] decryptDocument(byte[] ciphertext, String wrappedDekBase64, String ivNonceBase64) {
        try {
            SecretKey dek = kms.unwrapDataKey(wrappedDekBase64);
            byte[] iv = Base64.getDecoder().decode(ivNonceBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new SecurityCryptoException("AES-GCM Document decryption failed: Tag mismatch or corrupted ciphertext.", e);
        }
    }
}
