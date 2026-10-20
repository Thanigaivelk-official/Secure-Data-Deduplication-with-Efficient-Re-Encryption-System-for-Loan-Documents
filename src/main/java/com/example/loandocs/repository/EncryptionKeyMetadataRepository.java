package com.example.loandocs.repository;

import com.example.loandocs.entity.EncryptionKeyMetadata;
import com.example.loandocs.entity.StorageObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EncryptionKeyMetadataRepository extends JpaRepository<EncryptionKeyMetadata, Long> {

    Optional<EncryptionKeyMetadata> findByStorageObjectAndActiveTrue(StorageObject storageObject);

    List<EncryptionKeyMetadata> findByStorageObjectOrderByKeyVersionDesc(StorageObject storageObject);
}
