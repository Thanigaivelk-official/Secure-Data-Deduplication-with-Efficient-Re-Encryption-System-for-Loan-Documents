package com.example.loandocs.crypto;

import com.example.loandocs.exception.SecurityCryptoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class KeyManagementService {

    private static final String KEY_ALGORITHM = "AES";
    private static final int AES_KEY_BIT_LENGTH = 256;
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128; // 128-bit authentication tag

    private final SecretKey masterKey;
    private final SecureRandom secureRandom;

    public KeyManagementService(@Value("${app.security.master-key-secret}") String masterKeySecretBase64) {
        this.secureRandom = new SecureRandom();
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKeySecretBase64);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("Master Key must be 256 bits (32 bytes)");
            }
            this.masterKey = new SecretKeySpec(decoded, KEY_ALGORITHM);
        } catch (Exception e) {
            throw new SecurityCryptoException("Failed to initialize KeyManagementService with Master Key", e);
        }
    }

    /**
     * Generates a fresh, cryptographically strong AES-256 Data Encryption Key (DEK).
     */
    public SecretKey generateDataEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(AES_KEY_BIT_LENGTH, secureRandom);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityCryptoException("Failed to generate AES data key", e);
        }
    }

    /**
     * Generates a unique 96-bit (12-byte) initialization vector / nonce for AES-GCM.
     * Enforces the rule: NEVER reuse an IV under the same key.
     */
    public byte[] generateUniqueNonce() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Envelope Encryption: Encrypts (wraps) the Data Encryption Key (DEK) using the Master Key (KEK).
     * Output format: Base64( IV [12 bytes] + CiphertextWithTag )
     */
    public String wrapDataKey(SecretKey dek) {
        try {
            byte[] iv = generateUniqueNonce();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encryptedKeyBytes = cipher.doFinal(dek.getEncoded());

            // Prepend IV
            byte[] payload = new byte[iv.length + encryptedKeyBytes.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encryptedKeyBytes, 0, payload, iv.length, encryptedKeyBytes.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new SecurityCryptoException("Failed to wrap Data Encryption Key", e);
        }
    }

    /**
     * Envelope Decryption: Decrypts (unwraps) the Data Encryption Key (DEK) using the Master Key (KEK).
     */
    public SecretKey unwrapDataKey(String wrappedKeyBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(wrappedKeyBase64);
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                throw new SecurityCryptoException("Corrupted wrapped key payload");
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH_BYTES);

            int ciphertextLen = payload.length - GCM_IV_LENGTH_BYTES;
            byte[] encryptedKeyBytes = new byte[ciphertextLen];
            System.arraycopy(payload, GCM_IV_LENGTH_BYTES, encryptedKeyBytes, 0, ciphertextLen);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] rawKeyBytes = cipher.doFinal(encryptedKeyBytes);

            return new SecretKeySpec(rawKeyBytes, KEY_ALGORITHM);
        } catch (Exception e) {
            throw new SecurityCryptoException("Failed to unwrap Data Encryption Key. Authentication verification failed.", e);
        }
    }
}
