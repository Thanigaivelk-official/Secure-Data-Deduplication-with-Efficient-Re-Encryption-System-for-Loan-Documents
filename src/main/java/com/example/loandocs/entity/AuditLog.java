package com.example.loandocs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_actor", columnList = "actor_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_target", columnList = "target_entity, target_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 80)
    private String action; // e.g. USER_LOGIN, DOCUMENT_UPLOAD, DEDUPLICATION_REUSED, DOCUMENT_DOWNLOAD, RE_ENCRYPTION_EXECUTED, PERMISSION_REVOKED

    @Column(name = "target_entity", length = 60)
    private String targetEntity; // e.g. LoanDocument, Loan, User

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS"; // SUCCESS, FAILED, DENIED

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
}
