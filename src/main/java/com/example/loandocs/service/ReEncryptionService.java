package com.example.loandocs.service;

import com.example.loandocs.crypto.EncryptionService;
import com.example.loandocs.crypto.KeyManagementService;
import com.example.loandocs.entity.DocumentVersion;
import com.example.loandocs.entity.EncryptionKeyMetadata;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.StorageObject;
import com.example.loandocs.entity.User;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.DocumentVersionRepository;
import com.example.loandocs.repository.EncryptionKeyMetadataRepository;
import com.example.loandocs.repository.LoanDocumentRepository;
import com.example.loandocs.repository.StorageObjectRepository;
import com.example.loandocs.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(ReEncryptionService.class);

    private final EncryptionService encryptionService;
    private final KeyManagementService kms;
    private final StorageService storageService;
    private final LoanDocumentRepository loanDocumentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final EncryptionKeyMetadataRepository encryptionKeyMetadataRepository;
    private final AuditLogService auditLogService;

    /**
     * Re-encrypts an existing storage object under a fresh Data Encryption Key (DEK)
     * and new IV, incrementing the key version.
     * 
     * Workflow:
     * 1. Retrieve current active key metadata and ciphertext.
     * 2. Decrypt using the old DEK and verify GCM integrity.
     * 3. Generate a brand new AES-256 DEK and new 96-bit nonce.
     * 4. Re-encrypt document payload with AES-256-GCM.
     * 5. Persist the updated ciphertext.
     * 6. Deactivate old key metadata and save new key version metadata.
     * 7. Audit log the re-encryption event.
     */
    @Transactional
    public void executeFullReEncryption(String documentUuid, User actor, String clientIp) {
        LoanDocument document = loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));

        DocumentVersion currentVersion = documentVersionRepository.findByDocumentAndCurrentTrue(document)
                .orElseThrow(() -> new ResourceNotFoundException("No current version found for document"));

        StorageObject storageObject = currentVersion.getStorageObject();

        EncryptionKeyMetadata currentKey = encryptionKeyMetadataRepository.findByStorageObjectAndActiveTrue(storageObject)
                .orElseThrow(() -> new ResourceNotFoundException("No active encryption key found for storage object"));

        try {
            // Step 1: Read existing ciphertext and decrypt
            byte[] existingCiphertext = storageService.retrieve(storageObject.getStoragePath());
            byte[] plaintext = encryptionService.decryptDocument(
                    existingCiphertext,
                    currentKey.getEncryptedDekBase64(),
                    currentKey.getIvNonceBase64()
            );

            // Step 2: Encrypt with fresh DEK and unique IV
            EncryptionService.EncryptionResult newEncResult = encryptionService.encryptDocument(plaintext);

            // Step 3: Overwrite physical storage file with new ciphertext
            storageService.store(storageObject.getStoragePath(), newEncResult.getCiphertext());
            storageObject.setPhysicalSizeBytes((long) newEncResult.getCiphertext().length);
            storageObjectRepository.save(storageObject);

            // Step 4: Rotate key versions
            currentKey.setActive(false);
            encryptionKeyMetadataRepository.save(currentKey);

            int newKeyVersion = currentKey.getKeyVersion() + 1;
            EncryptionKeyMetadata newKey = EncryptionKeyMetadata.builder()
                    .storageObject(storageObject)
                    .keyVersion(newKeyVersion)
                    .encryptedDekBase64(newEncResult.getWrappedDekBase64())
                    .ivNonceBase64(newEncResult.getIvNonceBase64())
                    .algorithm("AES/GCM/NoPadding")
                    .active(true)
                    .build();
            encryptionKeyMetadataRepository.save(newKey);

            auditLogService.recordEvent(
                    actor,
                    "RE_ENCRYPTION_EXECUTED",
                    "LoanDocument",
                    document.getDocumentUuid(),
                    clientIp,
                    "SUCCESS",
                    "{\"oldKeyVersion\":" + currentKey.getKeyVersion() + ",\"newKeyVersion\":" + newKeyVersion + "}"
            );

            log.info("Successfully re-encrypted storage object {} to key version {}", storageObject.getStorageUuid(), newKeyVersion);
        } catch (Exception e) {
            auditLogService.recordEvent(
                    actor,
                    "RE_ENCRYPTION_FAILED",
                    "LoanDocument",
                    document.getDocumentUuid(),
                    clientIp,
                    "FAILED",
                    "{\"error\":\"" + e.getMessage() + "\"}"
            );
            throw new AppException("Re-encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Efficient Re-wrapping: If access is revoked or recipient key needs rotation,
     * re-wrap the DEK without decrypting/encrypting the underlying gigabytes of file content.
     */
    @Transactional
    public void rewrapDataKey(StorageObject storageObject, User actor, String clientIp) {
        EncryptionKeyMetadata currentKey = encryptionKeyMetadataRepository.findByStorageObjectAndActiveTrue(storageObject)
                .orElseThrow(() -> new ResourceNotFoundException("Active key not found for object"));

        SecretKey dek = kms.unwrapDataKey(currentKey.getEncryptedDekBase64());
        String rewrappedBase64 = kms.wrapDataKey(dek);

        currentKey.setEncryptedDekBase64(rewrappedBase64);
        encryptionKeyMetadataRepository.save(currentKey);

        auditLogService.recordEvent(
                actor,
                "KEY_REWRAPPED_EFFICIENT",
                "StorageObject",
                storageObject.getStorageUuid(),
                clientIp,
                "SUCCESS",
                "{\"keyVersion\":" + currentKey.getKeyVersion() + "}"
        );
    }
}
