package com.example.loandocs.config;

import com.example.loandocs.dto.DocumentDto;
import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.repository.LoanRepository;
import com.example.loandocs.repository.RoleRepository;
import com.example.loandocs.repository.UserRepository;
import com.example.loandocs.service.LoanDocumentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import com.example.loandocs.util.SimpleMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LoanRepository loanRepository;
    private final LoanDocumentService loanDocumentService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Initializing security roles, demo seed accounts, and loan documents...");

        Role adminRole = getOrCreateRole(Role.RoleType.ROLE_ADMIN);
        Role officerRole = getOrCreateRole(Role.RoleType.ROLE_OFFICER);
        Role customerRole = getOrCreateRole(Role.RoleType.ROLE_CUSTOMER);

        // 1. Admin
        User admin = createUserIfMissing("admin", "admin@bank.com", "Admin@123", "System Administrator", Set.of(adminRole));

        // 2. Loan Officers
        User officerSarah = createUserIfMissing("officer_sarah", "sarah@bank.com", "Officer@123", "Sarah Jenkins (Senior Officer)", Set.of(officerRole));
        User officerRaj = createUserIfMissing("officer_raj", "raj@bank.com", "Officer@123", "Rajesh Kumar (Loan Officer)", Set.of(officerRole));

        // 3. Customers
        User customerJohn = createUserIfMissing("customer_john", "john@example.com", "Customer@123", "John Doe", Set.of(customerRole));
        User customerAlice = createUserIfMissing("customer_alice", "alice@example.com", "Customer@123", "Alice Smith", Set.of(customerRole));
        User customerRobert = createUserIfMissing("customer_robert", "robert@example.com", "Customer@123", "Robert Chen", Set.of(customerRole));

        // 4. Seed Loan Applications & Encrypted Documents if database is fresh
        if (loanRepository.count() == 0) {
            seedLoanDataAndDocuments(customerJohn, customerAlice, customerRobert, officerSarah, officerRaj);
        }

        log.info("Application initialized with rich loan documents and deduplicated storage objects!");
    }

    private Role getOrCreateRole(Role.RoleType type) {
        return roleRepository.findByName(type)
                .orElseGet(() -> roleRepository.save(Role.builder().name(type).build()));
    }

    private User createUserIfMissing(String username, String email, String rawPassword, String fullName, Set<Role> roles) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .fullName(fullName)
                    .enabled(true)
                    .tenantId("DEFAULT_BANK_TENANT")
                    .roles(new HashSet<>(roles))
                    .createdAt(Instant.now())
                    .build();
            return userRepository.save(user);
        });
    }

    private void seedLoanDataAndDocuments(User john, User alice, User robert, User officerSarah, User officerRaj) {
        log.info("Generating realistic fictional loan applications and encrypted documents...");

        // Loan 1 for John (Home Loan)
        Loan loan1 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-2026-HOME-7841")
                .applicant(john)
                .assignedOfficer(officerSarah)
                .loanType("HOME_LOAN")
                .requestedAmount(new BigDecimal("350000.00"))
                .approvedAmount(new BigDecimal("320000.00"))
                .interestRate(new BigDecimal("6.75"))
                .tenureMonths(240)
                .status(Loan.LoanStatus.UNDER_REVIEW)
                .build());

        // Loan 2 for Alice (Personal Loan - Same Employer)
        Loan loan2 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-2026-PERS-9023")
                .applicant(alice)
                .assignedOfficer(officerSarah)
                .loanType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("45000.00"))
                .approvedAmount(new BigDecimal("45000.00"))
                .interestRate(new BigDecimal("9.20"))
                .tenureMonths(48)
                .status(Loan.LoanStatus.DOCUMENTS_PENDING)
                .build());

        // Loan 3 for Robert (Business Loan)
        Loan loan3 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-2026-BIZ-1104")
                .applicant(robert)
                .assignedOfficer(officerRaj)
                .loanType("BUSINESS_LOAN")
                .requestedAmount(new BigDecimal("180000.00"))
                .interestRate(new BigDecimal("8.50"))
                .tenureMonths(60)
                .status(Loan.LoanStatus.SUBMITTED)
                .build());

        // Loan 4 for John (Vehicle Loan)
        Loan loan4 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-2026-AUTO-5542")
                .applicant(john)
                .assignedOfficer(officerRaj)
                .loanType("VEHICLE_LOAN")
                .requestedAmount(new BigDecimal("32000.00"))
                .interestRate(new BigDecimal("7.90"))
                .tenureMonths(36)
                .status(Loan.LoanStatus.SUBMITTED)
                .build());

        // --- Standard Corporate Tax Form (Shared duplicate file across John & Alice) ---
        byte[] standardCorporateTaxForm = (
                "%PDF-1.4 [SECURE LOAN DOCUMENT - ACME TECH CORP]\n" +
                "STANDARD CORPORATE EMPLOYER TAX DECLARATION FORM 16 / W-2 (YEAR 2026)\n" +
                "Corporate ID: ACMETEC-99218-US\n" +
                "Standard HR Template Certification & Master Employer Deduction Statement\n" +
                "Issued by: Acme Technologies Global Payroll Division\n" +
                "Authorized Signature: Electronic Audit Code 88921-TAX-VERIFIED\n"
        ).getBytes(StandardCharsets.UTF_8);

        // --- Builder NOC / Society Certificate (Shared duplicate file across Loan 1 & Loan 4) ---
        byte[] standardSocietyNoc = (
                "%PDF-1.4 [APARTMENT COOPERATIVE HOUSING SOCIETY]\n" +
                "NO OBJECTION CERTIFICATE (NOC) FOR MORTGAGE & LOAN PROCESSING\n" +
                "Society Reg No: SCH-MAH-2021-998\n" +
                "Property: Orchid Heights Residency, Tower B\n" +
                "Verified clear title and zero encumbrance declaration.\n"
        ).getBytes(StandardCharsets.UTF_8);

        // --- Unique Documents ---
        byte[] johnPassport = (
                "%PDF-1.4 PASSPORT IDENTITY VERIFICATION - JOHN DOE\n" +
                "Document: Nationality & Passport Proof\n" +
                "Passport Number: Z91823901\n" +
                "Date of Issue: 2022-01-15 | Expiry: 2032-01-14\n"
        ).getBytes(StandardCharsets.UTF_8);

        byte[] aliceBankStatement = (
                "%PDF-1.4 CHASE / CITIBANK 6-MONTH OFFICIAL STATEMENT\n" +
                "Account Holder: Alice Smith\n" +
                "Account: **** **** 4819\n" +
                "Average Monthly Balance: $14,250.00\n" +
                "Regular Salary Credit: Active Verified\n"
        ).getBytes(StandardCharsets.UTF_8);

        byte[] robertBizAudit = (
                "%PDF-1.4 ANNUAL CHARTERED ACCOUNTANT AUDIT REPORT\n" +
                "Company: Chen Global Logistics LLC\n" +
                "GST / EIN: 88-1928374\n" +
                "Gross Annual Turnover: $1,240,000.00\n" +
                "Audited Net Profit Margin: 18.4%\n"
        ).getBytes(StandardCharsets.UTF_8);

        // 1. Upload John's Tax Form (Initial Upload -> Unique StorageObject)
        loanDocumentService.uploadDocument(
                loan1.getId(),
                new SimpleMultipartFile("file", "John_Employer_Tax_Statement.pdf", "application/pdf", standardCorporateTaxForm),
                "SALARY_SLIP",
                john,
                "192.168.1.10"
        );

        // 2. Upload John's Passport (Unique)
        loanDocumentService.uploadDocument(
                loan1.getId(),
                new SimpleMultipartFile("file", "John_Doe_Passport_Copy.pdf", "application/pdf", johnPassport),
                "IDENTITY_PROOF",
                john,
                "192.168.1.10"
        );

        // 3. Upload Society NOC for Loan 1 (Unique)
        loanDocumentService.uploadDocument(
                loan1.getId(),
                new SimpleMultipartFile("file", "Orchid_Heights_Society_NOC.pdf", "application/pdf", standardSocietyNoc),
                "PROPERTY_DOCUMENT",
                john,
                "192.168.1.10"
        );

        // 4. Upload Alice's Tax Form (IDENTICAL to John's -> Triggers Deduplication!)
        loanDocumentService.uploadDocument(
                loan2.getId(),
                new SimpleMultipartFile("file", "Alice_Corporate_Tax_Statement.pdf", "application/pdf", standardCorporateTaxForm),
                "SALARY_SLIP",
                alice,
                "192.168.1.15"
        );

        // 5. Upload Alice's Bank Statement (Unique)
        loanDocumentService.uploadDocument(
                loan2.getId(),
                new SimpleMultipartFile("file", "Alice_6Month_BankStatement.pdf", "application/pdf", aliceBankStatement),
                "BANK_STATEMENT",
                alice,
                "192.168.1.15"
        );

        // 6. Upload Robert's Business Audit (Unique)
        loanDocumentService.uploadDocument(
                loan3.getId(),
                new SimpleMultipartFile("file", "Chen_Logistics_Audit_Report.pdf", "application/pdf", robertBizAudit),
                "INCOME_PROOF",
                robert,
                "192.168.1.22"
        );

        // 7. Upload Society NOC for Loan 4 (IDENTICAL -> Triggers Deduplication!)
        loanDocumentService.uploadDocument(
                loan4.getId(),
                new SimpleMultipartFile("file", "Society_Clearance_NOC_Doc.pdf", "application/pdf", standardSocietyNoc),
                "PROPERTY_DOCUMENT",
                john,
                "192.168.1.10"
        );

        log.info("Loan applications & sample documents successfully seeded with verified deduplication!");
    }
}
