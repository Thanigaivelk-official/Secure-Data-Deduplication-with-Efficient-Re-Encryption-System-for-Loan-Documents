package com.example.loandocs.repository;

import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByApplicationNumber(String applicationNumber);

    Page<Loan> findByApplicant(User applicant, Pageable pageable);

    Page<Loan> findByAssignedOfficer(User assignedOfficer, Pageable pageable);

    @Query("SELECT l FROM Loan l WHERE " +
           "(:applicant IS NULL OR l.applicant = :applicant) AND " +
           "(:officer IS NULL OR l.assignedOfficer = :officer) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "(:loanType IS NULL OR l.loanType = :loanType) AND " +
           "(:query IS NULL OR LOWER(l.applicationNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(l.applicant.fullName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Loan> searchLoans(
            @Param("applicant") User applicant,
            @Param("officer") User officer,
            @Param("status") Loan.LoanStatus status,
            @Param("loanType") String loanType,
            @Param("query") String query,
            Pageable pageable
    );

    long countByStatus(Loan.LoanStatus status);
}
