package com.example.loandocs.controller;

import com.example.loandocs.dto.ApiResponse;
import com.example.loandocs.dto.DocumentDto;
import com.example.loandocs.dto.StorageAnalyticsDto;
import com.example.loandocs.entity.DocumentAccess;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.User;
import com.example.loandocs.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentApiController {

    private final LoanDocumentService loanDocumentService;
    private final DocumentSharingService documentSharingService;
    private final ReEncryptionService reEncryptionService;
    private final DeduplicationService deduplicationService;
    private final UserService userService;

    @PostMapping(value = "/loans/{loanId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDto>> uploadDocument(
            @PathVariable Long loanId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        User actor = userService.findByUsername(userDetails.getUsername());
        DocumentDto response = loanDocumentService.uploadDocument(loanId, file, documentType, actor, request.getRemoteAddr());

        return ResponseEntity.ok(ApiResponse.success(
                response.isDeduplicated() ? "Document uploaded & deduplicated (zero duplicate storage used)!"
                                          : "Document uploaded, encrypted (AES-GCM), and stored securely.",
                response
        ));
    }

    @GetMapping("/documents/{documentUuid}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String documentUuid,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        User actor = userService.findByUsername(userDetails.getUsername());
        byte[] plaintext = loanDocumentService.downloadDocument(documentUuid, actor, request.getRemoteAddr());
        LoanDocument doc = loanDocumentService.getDocumentByUuid(documentUuid);

        ByteArrayResource resource = new ByteArrayResource(plaintext);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                .body(resource);
    }

    @PostMapping("/documents/{documentUuid}/share")
    public ResponseEntity<ApiResponse<Void>> shareDocument(
            @PathVariable String documentUuid,
            @RequestParam Long targetUserId,
            @RequestParam(defaultValue = "DOWNLOAD") DocumentAccess.PermissionType permissionType,
            @RequestParam(required = false) Long expiresInSeconds,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        User actor = userService.findByUsername(userDetails.getUsername());
        Instant expiresAt = expiresInSeconds != null ? Instant.now().plusSeconds(expiresInSeconds) : null;

        documentSharingService.grantAccess(documentUuid, targetUserId, permissionType, expiresAt, actor, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Document access successfully granted."));
    }

    @DeleteMapping("/documents/{documentUuid}/permissions/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> revokePermission(
            @PathVariable String documentUuid,
            @PathVariable Long targetUserId,
            @RequestParam(defaultValue = "true") boolean rotateKey,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        User actor = userService.findByUsername(userDetails.getUsername());
        documentSharingService.revokeAccess(documentUuid, targetUserId, rotateKey, actor, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Document access revoked. Re-encryption/key rotation triggered: " + rotateKey));
    }

    @PostMapping("/documents/{documentUuid}/reencrypt")
    public ResponseEntity<ApiResponse<Void>> triggerReEncryption(
            @PathVariable String documentUuid,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        User actor = userService.findByUsername(userDetails.getUsername());
        reEncryptionService.executeFullReEncryption(documentUuid, actor, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Re-encryption complete. Storage payload re-encrypted under a newly rotated DEK."));
    }

    @GetMapping("/reports/analytics")
    public ResponseEntity<ApiResponse<StorageAnalyticsDto>> getStorageAnalytics(@AuthenticationPrincipal UserDetails userDetails) {
        User actor = userService.findByUsername(userDetails.getUsername());
        StorageAnalyticsDto analytics = deduplicationService.getAnalytics(actor.getTenantId());
        return ResponseEntity.ok(ApiResponse.success("Storage analytics retrieved", analytics));
    }
}
