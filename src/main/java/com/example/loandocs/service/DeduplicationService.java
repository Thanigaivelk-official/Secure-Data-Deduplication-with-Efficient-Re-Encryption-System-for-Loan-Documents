package com.example.loandocs.service;

import com.example.loandocs.crypto.EncryptionService;
import com.example.loandocs.crypto.FingerprintService;
import com.example.loandocs.dto.StorageAnalyticsDto;
import com.example.loandocs.entity.*;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.*;
import com.example.loandocs.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final FingerprintService fingerprintService;
    private final EncryptionService encryptionService;
    private final StorageService storageService;
    private final StorageObjectRepository storageObjectRepository;
    private final EncryptionKeyMetadataRepository encryptionKeyMetadataRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final AuditLogService auditLogService;

    public static class StorageProcessResult {
        public final StorageObject storageObject;
        public final boolean isDuplicate;

        public StorageProcessResult(StorageObject storageObject, boolean isDuplicate) {
            this.storageObject = storageObject;
            this.isDuplicate = isDuplicate;
        }
    }

    /**
     * Process file upload with scoped deduplication check.
     * If an identical file exists in the tenant: reuse the existing StorageObject and increment reference count.
     * If new: encrypt with AES-256-GCM, persist ciphertext to disk, and save envelope key metadata.
     */
    @Transactional
    public StorageProcessResult processAndStoreDocument(byte[] fileBytes, String originalFilename, String tenantId, User actor, String clientIp) {
        String fingerprint = fingerprintService.computeFingerprint(fileBytes);

        // Check for deduplication hit in tenant scope
        Optional<StorageObject> existingOpt = storageObjectRepository.findBySha256FingerprintAndTenantId(fingerprint, tenantId);

        if (existingOpt.isPresent()) {
            StorageObject existing = existingOpt.get();
            existing.setReferenceCount(existing.getReferenceCount() + 1);
            storageObjectRepository.save(existing);

            auditLogService.recordEvent(actor, "DEDUPLICATION_REUSED", "StorageObject", existing.getStorageUuid(),
                    clientIp, "SUCCESS", "{\"deduplicated\":true,\"originalFilename\":\"" + originalFilename + "\"}");

            return new StorageProcessResult(existing, true);
        }

        // New unique file: encrypt via AES-256-GCM
        EncryptionService.EncryptionResult encResult = encryptionService.encryptDocument(fileBytes);
        String storageUuid = UUID.randomUUID().toString();
        String relativeStoragePath = storageUuid + ".enc";

        storageService.store(relativeStoragePath, encResult.getCiphertext());

        StorageObject storageObject = StorageObject.builder()
                .storageUuid(storageUuid)
                .sha256Fingerprint(fingerprint)
                .tenantId(tenantId)
                .physicalSizeBytes((long) encResult.getCiphertext().length)
                .storagePath(relativeStoragePath)
                .referenceCount(1)
                .build();
        storageObject = storageObjectRepository.save(storageObject);

        EncryptionKeyMetadata keyMetadata = EncryptionKeyMetadata.builder()
                .storageObject(storageObject)
                .keyVersion(1)
                .encryptedDekBase64(encResult.getWrappedDekBase64())
                .ivNonceBase64(encResult.getIvNonceBase64())
                .algorithm("AES/GCM/NoPadding")
                .active(true)
                .build();
        encryptionKeyMetadataRepository.save(keyMetadata);

        auditLogService.recordEvent(actor, "DOCUMENT_ENCRYPTED_STORED", "StorageObject", storageObject.getStorageUuid(),
                clientIp, "SUCCESS", "{\"deduplicated\":false,\"originalFilename\":\"" + originalFilename + "\"}");

        return new StorageProcessResult(storageObject, false);
    }

    /**
     * Calculate aggregate storage analytics comparing logical vs physical storage to compute savings.
     */
    @Transactional(readOnly = true)
    public StorageAnalyticsDto getAnalytics(String tenantId) {
        long logicalBytes = loanDocumentRepository.sumLogicalStorageByTenant(tenantId);
        long physicalBytes = storageObjectRepository.sumPhysicalStorageByTenant(tenantId);
        long totalDocs = loanDocumentRepository.countTotalDocumentsByTenant(tenantId);
        long uniqueObjects = storageObjectRepository.countUniqueObjectsByTenant(tenantId);

        long bytesSaved = Math.max(0, logicalBytes - physicalBytes);
        double deduplicationRatio = logicalBytes > 0 ? (double) bytesSaved / logicalBytes : 0.0;

        return StorageAnalyticsDto.builder()
                .logicalStorageBytes(logicalBytes)
                .physicalStorageBytes(physicalBytes)
                .storageSavedBytes(bytesSaved)
                .deduplicationRatio(Math.round(deduplicationRatio * 10000.0) / 100.0) // percentage with 2 decimals
                .totalLogicalDocuments(totalDocs)
                .uniquePhysicalObjects(uniqueObjects)
                .build();
    }
}
