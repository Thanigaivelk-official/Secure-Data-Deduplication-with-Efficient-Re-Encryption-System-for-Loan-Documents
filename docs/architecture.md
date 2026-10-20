# Architecture & System Design
## Secure Data Deduplication with Efficient Re-Encryption for Loan Documents

---

### 1. High-Level System Architecture

The application is structured into four cohesive layers:
1. **Client & Presentation Layer**:
   - Thymeleaf Server-Rendered UI with responsive Bootstrap 5 and Bootstrap Icons.
   - REST API Controllers (`/api/auth`, `/api/loans`, `/api/loans/{id}/documents`, `/api/documents/{uuid}/share`, `/api/documents/{uuid}/reencrypt`, `/api/reports/analytics`).
2. **Access Control & Authorization Layer**:
   - Spring Security 6 with `BCryptPasswordEncoder` (strength 12).
   - Role-Based Access Control (`ROLE_ADMIN`, `ROLE_OFFICER`, `ROLE_CUSTOMER`).
   - Resource-level authorization enforcing loan ownership and explicit document-access grants.
3. **Cryptographic & Deduplication Engine**:
   - **Pre-Encryption Canonical Fingerprinting**: SHA-256 computed on canonical plaintext bytes.
   - **Scoped Deduplication Engine**: Scoped to the organization/tenant (`tenant_id`) to mitigate cross-tenant confirmation attacks.
   - **AES-256-GCM AEAD Authenticated Encryption**: Fresh 256-bit symmetric Data Encryption Key (DEK) and fresh 96-bit unique IV per file.
   - **Key Management & Envelope Encryption (KMS)**: DEKs are wrapped using the Master Key (KEK).
   - **Controlled Re-Encryption**: Efficient DEK re-wrapping and full ciphertext rotation to increment encryption key versions.
4. **Storage & Persistence Layer**:
   - `StorageService` abstraction isolating business logic from storage implementation (`LocalEncryptedStorageService`).
   - Relational Database (MySQL 8.x in production, H2 MySQL-compatibility mode for local evaluation).
   - Append-only `AuditLog` table for immutable security trails.

---

### 2. Physical vs. Logical Deduplication Architecture

```text
+-----------------------+              +-----------------------+
|  Loan Application A   |              |  Loan Application B   |
|   (Customer John)     |              |   (Customer Alice)    |
+-----------------------+              +-----------------------+
           |                                       |
           v                                       v
+-----------------------+              +-----------------------+
|  LoanDocument Record  |              |  LoanDocument Record  |
|   (UUID: doc-101)     |              |   (UUID: doc-102)     |
+-----------------------+              +-----------------------+
           \                                       /
            \                                     /
             v                                   v
          +-----------------------------------------+
          |         Shared StorageObject            |
          |  (SHA-256: 7f83b1657ff1fc53b92dc181...) |
          |        referenceCount = 2               |
          +-----------------------------------------+
                               |
                               v
          +-----------------------------------------+
          |   Physical AES-256-GCM Ciphertext File  |
          |  Path: storage/encrypted/{uuid}.enc     |
          |    (Only ONE copy stored on disk)       |
          +-----------------------------------------+
```
