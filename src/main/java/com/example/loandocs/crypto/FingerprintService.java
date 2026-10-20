package com.example.loandocs.crypto;

import com.example.loandocs.exception.SecurityCryptoException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class FingerprintService {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Computes the canonical SHA-256 hex fingerprint from input raw bytes.
     */
    public String computeFingerprint(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null for fingerprinting");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityCryptoException("Configured hash algorithm not found: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * Computes the SHA-256 hex fingerprint by streaming through an InputStream
     * to avoid loading extremely large files into memory all at once.
     */
    public String computeFingerprint(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new SecurityCryptoException("Failed to calculate stream fingerprint", e);
        }
    }
}
