package com.example.loandocs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "encryption_keys", indexes = {
    @Index(name = "idx_enc_storage_active", columnList = "storage_object_id, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EncryptionKeyMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_object_id", nullable = false)
    private StorageObject storageObject;

    @Column(name = "key_version", nullable = false)
    @Builder.Default
    private Integer keyVersion = 1;

    /**
     * Envelope Encryption: The symmetric Data Encryption Key (DEK) encrypted (wrapped)
     * using the Master Key (KEK) using AES-256-KW or AES-GCM and stored as Base64.
     */
    @Column(name = "encrypted_dek_base64", nullable = false, length = 255)
    private String encryptedDekBase64;

    /**
     * Unique 96-bit (12 bytes) IV/Nonce used for AES-GCM encryption of the file payload.
     */
    @Column(name = "iv_nonce_base64", nullable = false, length = 64)
    private String ivNonceBase64;

    @Column(name = "algorithm", nullable = false, length = 30)
    @Builder.Default
    private String algorithm = "AES/GCM/NoPadding";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
