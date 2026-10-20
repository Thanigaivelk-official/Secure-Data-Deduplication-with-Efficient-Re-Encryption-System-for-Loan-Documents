package com.example.loandocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageAnalyticsDto {
    private long logicalStorageBytes;
    private long physicalStorageBytes;
    private long storageSavedBytes;
    private double deduplicationRatio;
    private long totalLogicalDocuments;
    private long uniquePhysicalObjects;
}
