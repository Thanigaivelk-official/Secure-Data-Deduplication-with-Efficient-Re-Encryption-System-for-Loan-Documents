package com.example.loandocs;

import com.example.loandocs.dto.DocumentDto;
import com.example.loandocs.dto.StorageAnalyticsDto;
import com.example.loandocs.entity.Loan;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.repository.LoanRepository;
import com.example.loandocs.repository.RoleRepository;
import com.example.loandocs.repository.UserRepository;
import com.example.loandocs.service.DeduplicationService;
import com.example.loandocs.service.LoanDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class DeduplicationAndReEncryptionIntegrationTests {

    @Autowired
    private LoanDocumentService loanDocumentService;

    @Autowired
    private DeduplicationService deduplicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testApplicant1;
    private User testApplicant2;
    private Loan testLoan1;
    private Loan testLoan2;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(Role.RoleType.ROLE_CUSTOMER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(Role.RoleType.ROLE_CUSTOMER).build()));

        testApplicant1 = userRepository.save(User.builder()
                .username("test_user_1_" + System.currentTimeMillis())
                .email("u1_" + System.currentTimeMillis() + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@123"))
                .fullName("Test Applicant 1")
                .tenantId("DEFAULT_BANK_TENANT")
                .roles(new HashSet<>(Collections.singletonList(customerRole)))
                .build());

        testApplicant2 = userRepository.save(User.builder()
                .username("test_user_2_" + System.currentTimeMillis())
                .email("u2_" + System.currentTimeMillis() + "@test.com")
                .passwordHash(passwordEncoder.encode("Test@123"))
                .fullName("Test Applicant 2")
                .tenantId("DEFAULT_BANK_TENANT")
                .roles(new HashSet<>(Collections.singletonList(customerRole)))
                .build());

        testLoan1 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-TEST-001-" + System.currentTimeMillis())
                .applicant(testApplicant1)
                .loanType("HOME_LOAN")
                .requestedAmount(new BigDecimal("250000.00"))
                .tenureMonths(120)
                .status(Loan.LoanStatus.SUBMITTED)
                .build());

        testLoan2 = loanRepository.save(Loan.builder()
                .applicationNumber("LN-TEST-002-" + System.currentTimeMillis())
                .applicant(testApplicant2)
                .loanType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("50000.00"))
                .tenureMonths(36)
                .status(Loan.LoanStatus.SUBMITTED)
                .build());
    }

    @Test
    @DisplayName("Integration Test: Identical file uploads trigger deduplication and reuse single storage object")
    void testFileDeduplicationAcrossLoans() {
        byte[] identicalFileContent = ("EMPLOYER_TAX_DOCUMENT_TEMPLATE_2026_XYZ_CORP\n" +
                "Standard salary certificate and tax deduction statement.").getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "tax_statement_applicant1.pdf", "application/pdf", identicalFileContent);
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "tax_statement_applicant2.pdf", "application/pdf", identicalFileContent);

        // Upload 1
        DocumentDto doc1 = loanDocumentService.uploadDocument(
                testLoan1.getId(), file1, "SALARY_SLIP", testApplicant1, "127.0.0.1");

        assertNotNull(doc1.getDocumentUuid());
        assertFalse(doc1.isDeduplicated(), "First upload of unique content must NOT be marked as deduplicated");
        String firstStorageUuid = doc1.getStorageUuid();

        // Upload 2: Identical bytes from different applicant and loan
        DocumentDto doc2 = loanDocumentService.uploadDocument(
                testLoan2.getId(), file2, "SALARY_SLIP", testApplicant2, "127.0.0.1");

        assertNotNull(doc2.getDocumentUuid());
        assertNotEquals(doc1.getDocumentUuid(), doc2.getDocumentUuid(), "Logical documents must have unique UUIDs");
        assertTrue(doc2.isDeduplicated(), "Second upload of identical content MUST be recognized as duplicate");
        assertEquals(firstStorageUuid, doc2.getStorageUuid(), "Both logical documents must reference the same storage object");

        // Verify storage analytics
        StorageAnalyticsDto analytics = deduplicationService.getAnalytics("DEFAULT_BANK_TENANT");
        assertTrue(analytics.getStorageSavedBytes() > 0, "Storage saved must be greater than zero");
        assertTrue(analytics.getDeduplicationRatio() > 0.0, "Deduplication ratio must be positive");
    }

    @Test
    @DisplayName("Integration Test: Secure download with authenticated decryption roundtrip")
    void testDocumentDownload() {
        byte[] payload = "CONFIDENTIAL_PAYSLIP_MAY_2026".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "payslip.pdf", "application/pdf", payload);

        DocumentDto uploaded = loanDocumentService.uploadDocument(
                testLoan1.getId(), file, "SALARY_SLIP", testApplicant1, "127.0.0.1");

        // Download by owner
        byte[] downloaded = loanDocumentService.downloadDocument(uploaded.getDocumentUuid(), testApplicant1, "127.0.0.1");
        assertArrayEquals(payload, downloaded, "Decrypted downloaded file must match original plaintext");
    }
}
