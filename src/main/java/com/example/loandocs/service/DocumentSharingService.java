package com.example.loandocs.service;

import com.example.loandocs.entity.DocumentAccess;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.User;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.DocumentAccessRepository;
import com.example.loandocs.repository.LoanDocumentRepository;
import com.example.loandocs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentSharingService {

    private final DocumentAccessRepository documentAccessRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final UserRepository userRepository;
    private final ReEncryptionService reEncryptionService;
    private final AuditLogService auditLogService;

    @Transactional
    public DocumentAccess grantAccess(String documentUuid, Long targetUserId, DocumentAccess.PermissionType permissionType,
                                      Instant expiresAt, User granter, String clientIp) {
        LoanDocument document = loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));

        // Validate that granter is owner or assigned officer or admin
        boolean isOwner = document.getOwner().getId().equals(granter.getId());
        boolean isOfficer = document.getLoan().getAssignedOfficer() != null &&
                document.getLoan().getAssignedOfficer().getId().equals(granter.getId());

        if (!isOwner && !isOfficer) {
            throw new AccessDeniedException("You do not have permission to grant access to this document.");
        }

        User recipient = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        // Update or create access
        DocumentAccess access = documentAccessRepository
                .findByDocumentAndUserAndStatus(document, recipient, DocumentAccess.AccessStatus.ACTIVE)
                .orElse(DocumentAccess.builder()
                        .document(document)
                        .user(recipient)
                        .build());

        access.setPermissionType(permissionType);
        access.setStatus(DocumentAccess.AccessStatus.ACTIVE);
        access.setExpiresAt(expiresAt);
        access.setGrantedBy(granter);
        access = documentAccessRepository.save(access);

        auditLogService.recordEvent(
                granter,
                "DOCUMENT_ACCESS_GRANTED",
                "LoanDocument",
                documentUuid,
                clientIp,
                "SUCCESS",
                "{\"recipient\":\"" + recipient.getUsername() + "\",\"permission\":\"" + permissionType.name() + "\"}"
        );

        return access;
    }

    @Transactional
    public void revokeAccess(String documentUuid, Long targetUserId, boolean rotateKey, User revoker, String clientIp) {
        LoanDocument document = loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found: " + targetUserId));

        documentAccessRepository.findByDocumentAndUserAndStatus(document, targetUser, DocumentAccess.AccessStatus.ACTIVE)
                .ifPresent(access -> {
                    access.setStatus(DocumentAccess.AccessStatus.REVOKED);
                    documentAccessRepository.save(access);
                });

        auditLogService.recordEvent(
                revoker,
                "DOCUMENT_ACCESS_REVOKED",
                "LoanDocument",
                documentUuid,
                clientIp,
                "SUCCESS",
                "{\"revokedUser\":\"" + targetUser.getUsername() + "\",\"rotateKey\":" + rotateKey + "}"
        );

        // If key rotation is mandated upon revocation to prevent cached key attacks
        if (rotateKey) {
            reEncryptionService.executeFullReEncryption(documentUuid, revoker, clientIp);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentAccess> getAccessList(String documentUuid) {
        LoanDocument document = loanDocumentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentUuid));
        return documentAccessRepository.findByDocument(document);
    }
}
