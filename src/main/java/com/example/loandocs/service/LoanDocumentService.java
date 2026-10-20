package com.example.loandocs.service;

import com.example.loandocs.crypto.EncryptionService;
import com.example.loandocs.dto.DocumentDto;
import com.example.loandocs.entity.*;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.*;
import com.example.loandocs.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanDocumentService {

    private final LoanDocumentRepository loanDocumentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final EncryptionKeyMetadataRepository encryptionKeyMetadataRepository;
    private final LoanRepository loanRepository;
    private final DeduplicationService deduplicationService;
    private final EncryptionService encryptionService;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx");

    @Transactional
    public DocumentDto uploadDocument(Long loanId, MultipartFile file, String documentType, User actor, String clientIp) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + loanId));

        // Validate ownership or officer authorization
        boolean isOwner = loan.getApplicant().getId().equals(actor.getId());
        boolean isOfficer = loan.getAssignedOfficer() != null && loan.getAssignedOfficer().getId().equals(actor.getId());
        boolean isAdmin = actor.getRoles().stream().anyMatch(r -> r.getName() == Role.RoleType.ROLE_ADMIN);

        if (!isOwner && !isOfficer && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to upload documents for this loan.");
        }

        if (file.isEmpty()) {
            throw new AppException("Cannot upload empty file.");
        }

        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException("Unsupported file extension: " + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException("Failed to read uploaded file payload", e);
        }

        // Process through Deduplication & Authenticated Encryption engine
        DeduplicationService.StorageProcessResult processResult = deduplicationService.processAndStoreDocument(
                fileBytes,
                originalFilename,
                actor.getTenantId(),
                actor,
                clientIp
        );

        String documentUuid = UUID.randomUUID().toString();
        LoanDocument loanDocument = LoanDocument.builder()
                .documentUuid(documentUuid)
                .loan(loan)
                .owner(loan.getApplicant())
                .documentType(documentType)
                .originalFilename(originalFilename)
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .logicalSizeBytes(file.getSize())
                .status(LoanDocument.DocumentStatus.VERIFIED_PENDING)
                .build();
        loanDocument = loanDocumentRepository.save(loanDocument);

        DocumentVersion version = DocumentVersion.builder()
                .document(loanDocument)
                .storageObject(processResult.storageObject)
                .versionNumber(1)
                .current(true)
                .uploadedBy(actor)
                .build();
        documentVersionRepository.save(version);

        auditLogService.recordEvent(
                actor,
                "DOCUMENT_UPLOAD",
                "LoanDocument",
                documentUuid,
                clientIp,
                "SUCCESS",
                "{\"originalFilename\":\"" + originalFilename + "\",\"isDeduplicated\":" + processResult.isDuplicate + "}"
        );

        return mapToDto(loanDocument, version, processResult.isDuplicate);
    }

    @Transactional(readOnly = true)
    public byte[] downloadDocument(String documentUuid, User actor, String clientIp) {
        LoanDocument document = loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));

        // Authorization check: Owner, Assigned Officer, Admin, or Active Granted DocumentAccess
        boolean isOwner = document.getOwner().getId().equals(actor.getId());
        boolean isOfficer = document.getLoan().getAssignedOfficer() != null &&
                document.getLoan().getAssignedOfficer().getId().equals(actor.getId());
        boolean isAdmin = actor.getRoles().stream().anyMatch(r -> r.getName() == Role.RoleType.ROLE_ADMIN);

        boolean hasAccessGrant = documentAccessRepository
                .findByDocumentAndUserAndStatus(document, actor, DocumentAccess.AccessStatus.ACTIVE)
                .filter(a -> a.getExpiresAt() == null || a.getExpiresAt().isAfter(Instant.now()))
                .isPresent();

        if (!isOwner && !isOfficer && !isAdmin && !hasAccessGrant) {
            auditLogService.recordEvent(actor, "DOWNLOAD_ACCESS_DENIED", "LoanDocument", documentUuid, clientIp, "DENIED", null);
            throw new AccessDeniedException("Access denied: You are not authorized to download this document.");
        }

        DocumentVersion currentVersion = documentVersionRepository.findByDocumentAndCurrentTrue(document)
                .orElseThrow(() -> new ResourceNotFoundException("No active version found"));

        StorageObject storageObject = currentVersion.getStorageObject();
        EncryptionKeyMetadata keyMetadata = encryptionKeyMetadataRepository.findByStorageObjectAndActiveTrue(storageObject)
                .orElseThrow(() -> new ResourceNotFoundException("No active encryption key for storage object"));

        byte[] ciphertext = storageService.retrieve(storageObject.getStoragePath());
        byte[] plaintext = encryptionService.decryptDocument(
                ciphertext,
                keyMetadata.getEncryptedDekBase64(),
                keyMetadata.getIvNonceBase64()
        );

        auditLogService.recordEvent(actor, "DOCUMENT_DOWNLOAD", "LoanDocument", documentUuid, clientIp, "SUCCESS", null);
        return plaintext;
    }

    @Transactional(readOnly = true)
    public LoanDocument getDocumentByUuid(String documentUuid) {
        return loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }

    public DocumentDto mapToDto(LoanDocument doc, DocumentVersion version, boolean isDeduplicated) {
        return DocumentDto.builder()
                .documentUuid(doc.getDocumentUuid())
                .loanId(doc.getLoan().getId())
                .applicationNumber(doc.getLoan().getApplicationNumber())
                .originalFilename(doc.getOriginalFilename())
                .documentType(doc.getDocumentType())
                .mimeType(doc.getMimeType())
                .logicalSizeBytes(doc.getLogicalSizeBytes())
                .status(doc.getStatus().name())
                .versionNumber(version != null ? version.getVersionNumber() : 1)
                .storageUuid(version != null ? version.getStorageObject().getStorageUuid() : null)
                .deduplicated(isDeduplicated)
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
