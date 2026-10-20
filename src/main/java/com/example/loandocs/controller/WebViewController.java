package com.example.loandocs.controller;

import com.example.loandocs.dto.StorageAnalyticsDto;
import com.example.loandocs.entity.AuditLog;
import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.LoanDocument;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.repository.AuditLogRepository;
import com.example.loandocs.repository.LoanDocumentRepository;
import com.example.loandocs.repository.LoanRepository;
import com.example.loandocs.service.DeduplicationService;
import com.example.loandocs.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebViewController {

    private final UserService userService;
    private final DeduplicationService deduplicationService;
    private final LoanRepository loanRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(userDetails.getUsername());
        model.addAttribute("user", user);

        StorageAnalyticsDto analytics = deduplicationService.getAnalytics(user.getTenantId());
        model.addAttribute("analytics", analytics);

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName() == Role.RoleType.ROLE_ADMIN);
        boolean isOfficer = user.getRoles().stream().anyMatch(r -> r.getName() == Role.RoleType.ROLE_OFFICER);

        if (isAdmin) {
            model.addAttribute("role", "ADMIN");
            model.addAttribute("loans", loanRepository.findAll());
            model.addAttribute("documents", loanDocumentRepository.findAll());
            model.addAttribute("auditLogs", auditLogRepository.findTop20ByOrderByTimestampDesc());
        } else if (isOfficer) {
            model.addAttribute("role", "OFFICER");
            model.addAttribute("loans", loanRepository.findByAssignedOfficer(user, PageRequest.of(0, 50)).getContent());
            model.addAttribute("documents", loanDocumentRepository.findAll());
            model.addAttribute("auditLogs", auditLogRepository.findByActorOrderByTimestampDesc(user, PageRequest.of(0, 15)).getContent());
        } else {
            model.addAttribute("role", "CUSTOMER");
            model.addAttribute("loans", loanRepository.findByApplicant(user, PageRequest.of(0, 50)).getContent());
            model.addAttribute("documents", loanDocumentRepository.findByOwner(user, PageRequest.of(0, 50)).getContent());
            model.addAttribute("auditLogs", auditLogRepository.findByActorOrderByTimestampDesc(user, PageRequest.of(0, 10)).getContent());
        }

        return "dashboard";
    }
}
