package com.example.loandocs.repository;

import com.example.loandocs.entity.StorageObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageObjectRepository extends JpaRepository<StorageObject, Long> {

    Optional<StorageObject> findByStorageUuid(String storageUuid);

    /**
     * Look up by SHA-256 fingerprint within tenant scope to prevent cross-tenant discovery attacks
     */
    Optional<StorageObject> findBySha256FingerprintAndTenantId(String sha256Fingerprint, String tenantId);

    @Query("SELECT COALESCE(SUM(s.physicalSizeBytes), 0) FROM StorageObject s WHERE s.tenantId = :tenantId")
    Long sumPhysicalStorageByTenant(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(s) FROM StorageObject s WHERE s.tenantId = :tenantId")
    Long countUniqueObjectsByTenant(@Param("tenantId") String tenantId);
}
