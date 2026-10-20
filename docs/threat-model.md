# Security Threat Model & Cryptographic Assumptions
## Secure Data Deduplication with Efficient Re-Encryption for Loan Documents

---

### 1. Security Scope & Assumptions

1. **System Invariants**:
   - Every file written to disk or storage is encrypted with **AES-256-GCM** using a fresh, cryptographically strong Data Encryption Key (DEK) and a unique 96-bit IV.
   - Nonce reuse is strictly forbidden (`SecureRandom` generates 96 bits for each write or re-encryption).
   - Encryption keys (DEKs) are never stored in plaintext on disk or database. They are wrapped under the Master Key (KEK) using authenticated envelope encryption.

2. **Deduplication Privacy Caveats & Mitigation**:
   - **Equality Leakage**: Deterministic canonical fingerprinting reveals when two uploads contain identical plaintext bytes.
   - **Mitigation via Scoped Deduplication**: Deduplication is partitioned by `tenant_id` (organization/bank branch). Users outside the tenant cannot probe or deduce document presence.
   - Plaintext fingerprints are never exposed in user-facing REST responses or UI templates.

3. **Re-Encryption & Revocation Reality**:
   - When a user's sharing permission is revoked, the system flags the access record as `REVOKED` and triggers key rotation/re-encryption.
   - **Limitation**: Cryptographic revocation prevents future retrieval of the ciphertexts or keys. It cannot physically delete a copy that the user had previously downloaded to their local filesystem before revocation occurred.

---

### 2. Threat Analysis Matrix

| Threat | Description | Status & Mitigation |
| :--- | :--- | :--- |
| **Tampering in Storage** | Malicious actor modifies bytes of encrypted file on disk | **Mitigated**: AES-GCM AEAD produces a 128-bit authentication tag. Any single bit modification causes `GeneralSecurityException` during download decryption. |
| **SQL Injection** | Attacker injects SQL through loan or search parameters | **Mitigated**: Spring Data JPA with prepared statements and parameter binding (`@Param`). |
| **Path Traversal** | Uploaded file name contains `../` to overwrite system files | **Mitigated**: The original filename is metadata only; physical files are saved with randomized `UUID.enc` inside an isolated storage root guarded with `targetFile.startsWith(storageDirectory)`. |
| **Cross-Tenant Leakage** | Tenant B learns that Tenant A uploaded a specific document | **Mitigated**: Fingerprint lookup queries enforce `AND tenantId = :tenantId`. |
| **Session Fixation & CSRF** | Attacker intercepts user session or forces forged requests | **Mitigated**: Spring Security session fixation management and CSRF controls. |
