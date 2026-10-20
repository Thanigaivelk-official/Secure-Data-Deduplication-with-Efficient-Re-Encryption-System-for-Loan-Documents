# Technical Interview Preparation Guide
## Secure Data Deduplication with Efficient Re-Encryption for Loan Documents

---

### 1. Java & Spring Boot Core

**Q1: Why was Spring Boot chosen for this architecture?**  
*Answer*: Spring Boot provides production-grade dependency injection, transaction management (`@Transactional`), and first-class integration with Spring Security and Spring Data JPA. Its auto-configuration simplifies configuring connection pools (HikariCP), multipart file uploads, and modular service layering.

**Q2: How are database transactions handled during file upload and storage?**  
*Answer*: File persistence on disk cannot be natively rolled back by a relational database transaction manager. Therefore, the architecture validates the file and executes cryptographic storage first, followed by atomic database persistence of metadata and access records. If database persistence fails, an orphan cleanup hook can purge the unreferenced storage payload.

---

### 2. Cryptography & Security

**Q3: Why AES-GCM rather than AES-CBC or AES-ECB?**  
*Answer*:
- **ECB mode** is insecure because identical plaintext blocks produce identical ciphertext blocks, revealing patterns.
- **CBC mode** provides confidentiality but requires a separate MAC (e.g. HMAC-SHA256) to ensure integrity (Encrypt-then-MAC). Without authentication, CBC is vulnerable to padding oracle attacks.
- **AES-GCM** provides **Authenticated Encryption with Associated Data (AEAD)** in a single primitive, verifying both confidentiality and ciphertext integrity via a 128-bit authentication tag.

**Q4: Why must a Nonce/IV never be reused in AES-GCM?**  
*Answer*: Reusing an IV with the same AES key completely breaks GCM mode. It allows an attacker to recover the authentication key (GHASH key $H$) and forge messages, as well as XORing two ciphertexts together to eliminate keystream and compromise confidentiality.

**Q5: What is Envelope Encryption and why is it used?**  
*Answer*: Envelope encryption uses two layers of keys:
1. **Data Encryption Key (DEK)**: A fast symmetric key (AES-256) that encrypts the bulky document payload.
2. **Key Encryption Key (KEK / Master Key)**: Used only to encrypt (wrap) the smaller 256-bit DEK.
This enables efficient key rotation: we can rotate the Master Key or re-wrap DEKs for multiple users without decrypting and re-encrypting the underlying gigabytes of document ciphertext.

---

### 3. Deduplication & Re-Encryption

**Q6: What is Content-Based Deduplication and how does the system implement it?**  
*Answer*: The system computes a canonical SHA-256 fingerprint on plaintext bytes prior to encryption. It then queries `StorageObjectRepository` within the user's `tenant_id`. If a match is found, the system links a new `LoanDocument` record to the existing `StorageObject` and increments its `referenceCount`, saving physical storage space.

**Q7: What is "Equality Leakage" in deduplication, and how is it addressed?**  
*Answer*: Deterministic hashing reveals if two users have uploaded the exact same file (e.g., an attacker can upload a known tax return to see if the victim has already filed it). The system mitigates this by enforcing **tenant-scoped deduplication**, ensuring that deduplication discovery is restricted exclusively within the authorized organization domain.

**Q8: What is "Efficient Re-Encryption" in this system?**  
*Answer*: Naive re-encryption requires re-encrypting the entire multi-megabyte file separately for every authorized recipient ($O(N \times \text{FileSize})$). In our data-key architecture, one encrypted file on disk is referenced by multiple authorized access records. When key rotation is mandated, the system rotates the DEK and re-encrypts the storage payload once, or simply re-wraps the DEK, avoiding redundant per-user processing.
