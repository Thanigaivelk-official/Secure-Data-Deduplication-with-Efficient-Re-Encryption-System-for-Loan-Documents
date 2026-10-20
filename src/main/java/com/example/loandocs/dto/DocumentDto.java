package com.example.loandocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private String documentUuid;
    private Long loanId;
    private String applicationNumber;
    private String originalFilename;
    private String documentType;
    private String mimeType;
    private Long logicalSizeBytes;
    private String status;
    private Integer versionNumber;
    private String storageUuid;
    private boolean deduplicated;
    private Instant createdAt;
}
