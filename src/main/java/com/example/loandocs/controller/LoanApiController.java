package com.example.loandocs.controller;

import com.example.loandocs.dto.ApiResponse;
import com.example.loandocs.dto.LoanDto;
import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.User;
import com.example.loandocs.service.LoanService;
import com.example.loandocs.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanApiController {

    private final LoanService loanService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoanDto>> createLoan(
            @Valid @RequestBody LoanDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User applicant = userService.findByUsername(userDetails.getUsername());
        Loan loan = loanService.createLoanApplication(request, applicant, httpRequest.getRemoteAddr());

        LoanDto responseDto = LoanDto.builder()
                .id(loan.getId())
                .applicationNumber(loan.getApplicationNumber())
                .loanType(loan.getLoanType())
                .requestedAmount(loan.getRequestedAmount())
                .tenureMonths(loan.getTenureMonths())
                .status(loan.getStatus().name())
                .applicantName(applicant.getFullName())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Loan application created successfully", responseDto));
    }

    @PatchMapping("/{loanId}/status")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long loanId,
            @RequestParam Loan.LoanStatus status,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User actor = userService.findByUsername(userDetails.getUsername());
        loanService.updateLoanStatus(loanId, status, actor, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Loan status updated to " + status));
    }

    @PatchMapping("/{loanId}/assign/{officerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignOfficer(
            @PathVariable Long loanId,
            @PathVariable Long officerId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User actor = userService.findByUsername(userDetails.getUsername());
        loanService.assignOfficer(loanId, officerId, actor, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Loan officer assigned successfully"));
    }
}
