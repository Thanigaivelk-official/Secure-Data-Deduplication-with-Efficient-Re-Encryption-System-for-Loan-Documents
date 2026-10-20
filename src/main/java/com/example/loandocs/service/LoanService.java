package com.example.loandocs.service;

import com.example.loandocs.dto.LoanDto;
import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.LoanRepository;
import com.example.loandocs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public Loan createLoanApplication(LoanDto request, User applicant, String clientIp) {
        String applicationNumber = "LN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Loan loan = Loan.builder()
                .applicationNumber(applicationNumber)
                .applicant(applicant)
                .loanType(request.getLoanType())
                .requestedAmount(request.getRequestedAmount())
                .tenureMonths(request.getTenureMonths())
                .status(Loan.LoanStatus.SUBMITTED)
                .build();

        loan = loanRepository.save(loan);

        auditLogService.recordEvent(
                applicant,
                "LOAN_APPLICATION_CREATED",
                "Loan",
                applicationNumber,
                clientIp,
                "SUCCESS",
                "{\"requestedAmount\":" + request.getRequestedAmount() + ",\"type\":\"" + request.getLoanType() + "\"}"
        );

        return loan;
    }

    @Transactional
    public Loan updateLoanStatus(Long loanId, Loan.LoanStatus newStatus, User actor, String clientIp) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));

        Loan.LoanStatus oldStatus = loan.getStatus();
        loan.setStatus(newStatus);
        loan = loanRepository.save(loan);

        auditLogService.recordEvent(
                actor,
                "LOAN_STATUS_UPDATED",
                "Loan",
                loan.getApplicationNumber(),
                clientIp,
                "SUCCESS",
                "{\"oldStatus\":\"" + oldStatus + "\",\"newStatus\":\"" + newStatus + "\"}"
        );

        return loan;
    }

    @Transactional
    public Loan assignOfficer(Long loanId, Long officerId, User actor, String clientIp) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found: " + officerId));

        loan.setAssignedOfficer(officer);
        loan.setStatus(Loan.LoanStatus.UNDER_REVIEW);
        loan = loanRepository.save(loan);

        auditLogService.recordEvent(
                actor,
                "LOAN_OFFICER_ASSIGNED",
                "Loan",
                loan.getApplicationNumber(),
                clientIp,
                "SUCCESS",
                "{\"officer\":\"" + officer.getUsername() + "\"}"
        );

        return loan;
    }

    @Transactional(readOnly = true)
    public Loan getLoanById(Long loanId, User actor) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));

        boolean isOwner = loan.getApplicant().getId().equals(actor.getId());
        boolean isOfficer = loan.getAssignedOfficer() != null && loan.getAssignedOfficer().getId().equals(actor.getId());
        boolean isAdmin = actor.getRoles().stream().anyMatch(r -> r.getName() == Role.RoleType.ROLE_ADMIN);

        if (!isOwner && !isOfficer && !isAdmin) {
            throw new AccessDeniedException("Access denied to loan application: " + loanId);
        }
        return loan;
    }
}
