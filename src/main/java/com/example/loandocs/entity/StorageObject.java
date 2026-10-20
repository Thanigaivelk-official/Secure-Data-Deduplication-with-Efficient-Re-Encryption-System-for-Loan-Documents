package com.example.loandocs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "storage_objects", indexes = {
    @Index(name = "idx_storage_fingerprint_tenant", columnList = "sha256_fingerprint, tenant_id"),
    @Index(name = "idx_storage_uuid", columnList = "storage_uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class StorageObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_uuid", nullable = false, unique = true, length = 64)
    private String storageUuid;

    /**
     * Canonical SHA-256 fingerprint of the plaintext content computed prior to encryption.
     * Scoped by tenant_id to isolate equality discovery across distinct bank tenants.
     */
    @Column(name = "sha256_fingerprint", nullable = false, length = 64)
    private String sha256Fingerprint;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "physical_size_bytes", nullable = false)
    private Long physicalSizeBytes;

    /**
     * Safe random file location on disk outside webroot, e.g. "storage/encrypted/xxxx-xxxx.enc"
     */
    @Column(name = "storage_path", nullable = false, length = 255)
    private String storagePath;

    /**
     * Deduplication reference count: incremented when duplicate file references this storage object,
     * decremented on logical document deletion.
     */
    @Column(name = "reference_count", nullable = false)
    @Builder.Default
    private Integer referenceCount = 1;

    @OneToMany(mappedBy = "storageObject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EncryptionKeyMetadata> encryptionKeys = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
