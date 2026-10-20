package com.example.loandocs.repository;

import com.example.loandocs.entity.DocumentAccess;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, Long> {

    Optional<DocumentAccess> findByDocumentAndUserAndStatus(LoanDocument document, User user, DocumentAccess.AccessStatus status);

    List<DocumentAccess> findByDocumentAndStatus(LoanDocument document, DocumentAccess.AccessStatus status);

    List<DocumentAccess> findByDocument(LoanDocument document);
}
