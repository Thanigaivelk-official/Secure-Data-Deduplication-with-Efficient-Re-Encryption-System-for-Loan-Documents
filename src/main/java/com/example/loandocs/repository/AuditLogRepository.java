package com.example.loandocs.repository;

import com.example.loandocs.entity.AuditLog;
import com.example.loandocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorOrderByTimestampDesc(User actor, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:targetEntity IS NULL OR a.targetEntity = :targetEntity) AND " +
           "(:status IS NULL OR a.status = :status) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchAuditLogs(
            @Param("action") String action,
            @Param("targetEntity") String targetEntity,
            @Param("status") String status,
            Pageable pageable
    );

    List<AuditLog> findTop20ByOrderByTimestampDesc();
}
