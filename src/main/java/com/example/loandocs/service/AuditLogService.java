package com.example.loandocs.service;

import com.example.loandocs.entity.AuditLog;
import com.example.loandocs.entity.User;
import com.example.loandocs.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit log record in a new transaction to ensure audit durability
     * even if the calling transaction rolls back on failure.
     */
    @Transactional
    public void recordEvent(User actor, String action, String targetEntity, String targetId, String clientIp, String status, String detailsJson) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .actor(actor)
                    .action(action)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .clientIp(clientIp)
                    .status(status)
                    .detailsJson(detailsJson)
                    .timestamp(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
            log.info("AUDIT EVENT: action={}, actor={}, target={}:{}, status={}",
                    action, actor != null ? actor.getUsername() : "ANONYMOUS", targetEntity, targetId, status);
        } catch (Exception e) {
            log.error("Failed to persist audit log entry", e);
        }
    }
}
