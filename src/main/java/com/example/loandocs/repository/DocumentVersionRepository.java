package com.example.loandocs.repository;

import com.example.loandocs.entity.DocumentVersion;
import com.example.loandocs.entity.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    Optional<DocumentVersion> findByDocumentAndCurrentTrue(LoanDocument document);

    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(LoanDocument document);
}
