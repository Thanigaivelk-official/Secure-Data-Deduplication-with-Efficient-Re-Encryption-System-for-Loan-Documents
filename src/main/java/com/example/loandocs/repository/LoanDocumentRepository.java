package com.example.loandocs.repository;

import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Long> {

    Optional<LoanDocument> findByDocumentUuid(String documentUuid);

    List<LoanDocument> findByLoan(Loan loan);

    Page<LoanDocument> findByOwner(User owner, Pageable pageable);

    @Query("SELECT d FROM LoanDocument d WHERE " +
           "(:loan IS NULL OR d.loan = :loan) AND " +
           "(:owner IS NULL OR d.owner = :owner) AND " +
           "(:documentType IS NULL OR d.documentType = :documentType) AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:query IS NULL OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<LoanDocument> searchDocuments(
            @Param("loan") Loan loan,
            @Param("owner") User owner,
            @Param("documentType") String documentType,
            @Param("status") LoanDocument.DocumentStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(d.logicalSizeBytes), 0) FROM LoanDocument d WHERE d.owner.tenantId = :tenantId")
    Long sumLogicalStorageByTenant(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(d) FROM LoanDocument d WHERE d.owner.tenantId = :tenantId")
    Long countTotalDocumentsByTenant(@Param("tenantId") String tenantId);
}
