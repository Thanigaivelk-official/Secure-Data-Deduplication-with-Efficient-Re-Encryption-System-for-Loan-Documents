# Secure Data Deduplication with Efficient Re-Encryption for Loan Documents

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Security](https://img.shields.io/badge/Security-AES--256--GCM%20%7C%20SHA--256-blue.svg)]()
[![Database](https://img.shields.io/badge/Database-MySQL%208.x%20%2F%20H2-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()

A cloud-style, secure document-sharing and deduplication web platform engineered for loan-processing institutions. It eliminates redundant physical storage by identifying duplicate files through pre-encryption canonical fingerprinting within trusted tenant scopes, while enforcing authenticated envelope encryption (**AES-256-GCM**), role-based access control, and efficient re-encryption workflows when document permissions change.

---

## Table of Contents
1. [Overview & Problem Statement](#1-overview--problem-statement)
2. [Full System Workflow & Process](#2-full-system-workflow--process)
3. [End-to-End Cryptographic & Deduplication Engine](#3-end-to-end-cryptographic--deduplication-engine)
4. [Efficient Re-Encryption Architecture](#4-efficient-re-encryption-architecture)
5. [System Architecture & Data Flow](#5-system-architecture--data-flow)
6. [Database & ER Schema](#6-database--er-schema)
7. [Threat Model & Security Assumptions](#7-threat-model--security-assumptions)
8. [Role-Based Access Control (RBAC)](#8-role-based-access-control-rbac)
9. [Interactive UI & Visual Animations](#9-interactive-ui--visual-animations)
10. [Step-by-Step Installation & Setup](#10-step-by-step-installation--setup)
11. [Demonstration Walkthrough (Demo Accounts)](#11-demonstration-walkthrough-demo-accounts)
12. [Automated Verification & Tests](#12-automated-verification--tests)
13. [Resume Summary & Interview Preparation](#13-resume-summary--interview-preparation)

---

## 1. Overview & Problem Statement

In lending and banking institutions, loan processing requires applicants to submit numerous identity, address, income, and legal collateral documents. Many of these documents are redundant across applications:
- **Corporate Standard Forms**: Standard employer Form 16 / W-2 tax forms and payroll templates shared by multiple employees of the same corporation.
- **Housing Society / Builder NOCs**: Standard No Objection Certificates and master property title documents for apartment developments shared by various buyers.
- **Joint / Co-applicant Submissions**: Identical documents uploaded across related loan applications.

### The Challenge
Storing duplicate copies of large confidential documents leads to ballooning cloud storage bills. However, traditional deduplication on plaintext violates customer confidentiality. Conversely, naive encryption with different random keys for each user makes identical files appear completely random, defeating conventional cloud storage deduplication.

### The Solution
This platform implements **Tenant-Scoped Content-Based Deduplication with Authenticated Envelope Encryption (AES-256-GCM)** and **Data Key Envelope Re-Encryption**. The system recognizes identical files within an organization domain prior to encryption, stores only **one encrypted physical copy** on disk, links multiple logical document records to that single ciphertext, and supports instantaneous re-wrapping and controlled re-encryption upon permission revocation.

---

## 2. Full System Workflow & Process

```
                                  [ User Uploads Loan Document ]
                                                 │
                                                 ▼
                                     [ Validate File Metadata ]
                           (MIME type, extension, size, magic signature)
                                                 │
                                                 ▼
                                [ Compute SHA-256 Fingerprint ]
                           (Canonical plaintext hash before encryption)
                                                 │
                                                 ▼
                                [ Scoped Deduplication Query ]
                        (Search StorageObject by Fingerprint + TenantId)
                                                 │
                      ┌──────────────────────────┴──────────────────────────┐
                      ▼                                                     ▼
             [ Duplicate Found! ]                                  [ New Unique File ]
                      │                                                     │
                      ▼                                                     ▼
      • Increment StorageObject refCount                   • Generate fresh AES-256 Data Key (DEK)
      • Link new LoanDocument record                       • Generate unique 96-bit IV (SecureRandom)
      • ZERO duplicate disk space used!                    • Encrypt with AES-256-GCM (AEAD)
      • Log DEDUPLICATION_REUSED event                     • Wrap DEK under Master Key (KEK)
                                                           • Write {uuid}.enc to secure storage
                                                           • Save EncryptionKeyMetadata record
                      │                                                     │
                      └──────────────────────────┬──────────────────────────┘
                                                 │
                                                 ▼
                                  [ User Downloads Document ]
                                                 │
                                                 ▼
                                  [ RBAC & Access Verification ]
                      (Is Owner? Assigned Officer? Admin? Active Grant?)
                                                 │
                                                 ▼
                                  [ Authenticated Decryption ]
                      • Retrieve encrypted DEK & unwrap using Master Key
                      • Read ciphertext from /storage/encrypted/{uuid}.enc
                      • Decrypt using AES-256-GCM & verify 128-bit AEAD tag
                      • Stream plaintext to authorized user
                                                 │
                                                 ▼
                                [ Access Revocation / Re-Encryption ]
                      • Access revoked: Mark grant as REVOKED
                      • Efficient Re-wrapping: Re-wrap DEK for remaining users
                      • Policy Key Rotation: Re-encrypt ciphertext under new DEK
                      • Increment key version & record audit trail
```

---

## 3. End-to-End Cryptographic & Deduplication Engine

### 1. Authenticated Encryption with Associated Data (AES-256-GCM)
- Every encrypted document is protected using **AES-256-GCM** with a 128-bit authentication tag.
- **Nonce/IV Security**: A cryptographically random 96-bit initialization vector is generated via `SecureRandom` for every single encryption and re-encryption operation. Nonce reuse is strictly prevented.
- **Tamper Resistance**: If any byte of the ciphertext file or IV is modified in storage, GCM tag verification fails, rejecting tampered data.

### 2. Envelope Encryption (Key Management Service)
- **Data Encryption Key (DEK)**: A fresh 256-bit symmetric AES key generated per unique storage object to encrypt bulky document payloads.
- **Key Encryption Key (KEK / Master Key Reference)**: A 256-bit master key used exclusively to wrap (encrypt) and unwrap the DEK using AES-GCM envelope encryption.
- Keys are never stored in plaintext in the database or source code.

### 3. Canonical Plaintext Fingerprinting
- Calculates a canonical **SHA-256** hash from the plaintext stream before encryption:
  $$\text{Fingerprint} = \text{SHA-256}(\text{Document Bytes})$$
- The fingerprint is indexed in MySQL/H2 to detect duplicates within milliseconds without reading disk files.

### 4. Tenant-Scoped Deduplication
- Rather than a dangerous global deduplication pool, lookups are partitioned by `tenant_id`:
  $$\text{Query: } \text{SELECT } * \text{ FROM storage\_objects WHERE sha256\_fingerprint} = ? \text{ AND tenant\_id} = ?$$
- This guarantees that external organizations or unauthorized tenants cannot probe the database to verify if an applicant has uploaded a specific file.

---

## 4. Efficient Re-Encryption Architecture

### The Problem with Naive Re-Encryption
In traditional sharing schemes, if 100 loan officers or auditors are granted access to a 50 MB document, naive systems encrypt 100 separate copies ($100 \times 50\text{ MB} = 5\text{ GB}$ of storage). When access is revoked, re-encrypting requires processing all 5 GB.

### The Efficient Data-Key Architecture
Our system decouples the **bulk ciphertext** from the **access credentials**:
$$\text{One Physical Ciphertext Object} + \text{One Wrapped DEK} \longleftrightarrow \text{Multiple Logical Document References}$$

1. **Access Granting**: When a document is shared with another officer or auditor, a new `DocumentAccess` grant is created with an optional expiration timestamp. No physical file copying occurs.
2. **Access Revocation**: When access is revoked:
   - The user's `DocumentAccess` status is set to `REVOKED`.
   - The download controller instantly rejects future retrieval attempts ($403\text{ Forbidden}$).
3. **Controlled Re-Encryption (Key Rotation)**:
   - When policy mandates physical key rotation, the `ReEncryptionService` decrypts the storage payload using the old DEK, generates a brand new AES-256 DEK and new 96-bit IV, re-encrypts the ciphertext, increments the `keyVersion` ($v1 \to v2$), and deactivates the previous key.
   - All operations are transactional and recorded in the append-only `AuditLog`.

---

## 5. System Architecture & Data Flow

```text
+-----------------------------------------------------------------------------------+
|                            PRESENTATION & API LAYER                               |
|   Bootstrap 5 + Animate.css UI  |  Thymeleaf Views  |  Spring REST API Endpoints  |
+-----------------------------------------------------------------------------------+
                                          │
                                          ▼
+-----------------------------------------------------------------------------------+
|                           SECURITY & AUTHORIZATION LAYER                          |
|   Spring Security 6  |  BCrypt (Strength 12)  |  RBAC (ADMIN, OFFICER, CUSTOMER)  |
+-----------------------------------------------------------------------------------+
                                          │
                                          ▼
+-----------------------------------------------------------------------------------+
|                        APPLICATION & CRYPTOGRAPHIC SERVICES                       |
|   LoanDocumentService       DeduplicationService       ReEncryptionService        |
|   KeyManagementService (KMS Envelope)     FingerprintService (Canonical SHA-256)   |
|   AuditLogService (Immutable Security Trail)          FileValidationService       |
+-----------------------------------------------------------------------------------+
                     │                                           │
                     ▼                                           ▼
+------------------------------------------+    +-----------------------------------+
|         PERSISTENCE LAYER (JPA)          |    |          STORAGE LAYER            |
|   MySQL 8.x / Embedded H2 Database       |    |   LocalEncryptedStorageService    |
|   • users             • loans            |    |   (Cloud-ready StorageService API)|
|   • loan_documents    • storage_objects  |    |   Writes AES-256-GCM ciphertexts  |
|   • encryption_keys   • audit_logs       |    |   Path: /storage/encrypted/*.enc  |
+------------------------------------------+    +-----------------------------------+
```

---

## 6. Database & ER Schema

```text
 [ USERS ] 1 ──── * [ LOANS ] 1 ──── * [ LOAN_DOCUMENTS ] 1 ──── * [ DOCUMENT_VERSIONS ]
    │                      │                                                  │
    *                      *                                                  *
 [ USER_ROLES ]     [ DOCUMENT_ACCESS ]                             [ STORAGE_OBJECTS ]
                                                                              │
                                                                              *
                                                                   [ ENCRYPTION_KEYS ]
```

### Table Definitions
- `users`: Stores credentials (BCrypt hashes), full name, tenant ID, and activation status.
- `roles`: `ROLE_ADMIN`, `ROLE_OFFICER`, `ROLE_CUSTOMER`.
- `loans`: Application number, applicant foreign key, assigned loan officer, loan type, amounts, tenure, and status.
- `loan_documents`: Logical document record with original filename, document type (e.g. `SALARY_SLIP`, `PROPERTY_DOCUMENT`), MIME type, and logical size.
- `document_versions`: Tracks version numbers and points to the physical `storage_objects`.
- `storage_objects`: Physical ciphertext record with unique UUID, canonical SHA-256 fingerprint, physical byte size, storage path, and active `reference_count`.
- `encryption_keys`: Active and historical key versions, Base64 wrapped DEK, Base64 96-bit IV, and algorithm name.
- `document_access`: Explicit sharing grants with permission type (`DOWNLOAD`, `VIEW`, `MANAGE`) and expiration timestamps.
- `audit_logs`: Append-only security event trail recording actor, action, target entity, client IP, status, and JSON metadata.

---

## 7. Threat Model & Security Assumptions

| Threat Category | Attack Vector | System Defense & Mitigation |
| :--- | :--- | :--- |
| **Storage Theft** | Physical hard drive or cloud bucket accessed by unauthorized party | **AES-256-GCM AEAD**: Stored files are ciphertext only (`.enc`). Without the master key and wrapped DEKs, data cannot be decrypted. |
| **Tampering** | Attacker modifies ciphertext bytes in storage | **AEAD Tag Verification**: AES-GCM calculates a 128-bit authentication tag. Any single-bit alteration causes decryption to fail. |
| **Equality Leakage** | Attacker probes deduplication to detect if a victim uploaded a specific file | **Tenant-Scoped Deduplication**: Deduplication searches are restricted to `tenant_id`. Cross-organization probing is blocked. |
| **Replay Attacks** | IV / Nonce reuse under identical encryption keys | **Unique SecureRandom Nonces**: A fresh 96-bit IV is generated for every write and re-encryption. Nonce reuse is strictly prohibited. |
| **Path Traversal** | Malicious filenames (e.g. `../../etc/passwd`) | **Randomized Storage UUIDs**: The original filename is metadata only. Files are stored as randomized `{uuid}.enc` guarded with root path checks. |
| **Unauthorized Access** | Customer tries to access another customer's loan files | **Method-Level RBAC**: `@PreAuthorize` checks loan ownership, assigned officer ID, or explicit active `DocumentAccess` records. |
| **SQL Injection** | SQL payloads in application numbers or names | **JPA Parameterization**: All queries use Hibernate/JPA prepared statements with typed parameters (`@Param`). |

> [!IMPORTANT]
> **Revocation Boundary**: Cryptographic revocation on the server prevents future retrieval of ciphertexts and keys, and triggers key rotation. It cannot physically delete a copy that an authorized user had previously downloaded to their personal machine before access was revoked.

---

## 8. Role-Based Access Control (RBAC)

### 1. System Administrator (`ROLE_ADMIN`)
- View full enterprise storage analytics (Logical volume, Physical volume, Disk saved, Deduplication ratio).
- Trigger manual or policy-mandated re-encryption and key rotation workflows.
- View real-time immutable security audit logs.
- Assign loan applications to officers.

### 2. Loan Officer (`ROLE_OFFICER`)
- Review submitted loan applications and verify supporting documents.
- Download and decrypt authorized documents.
- Grant and revoke document-sharing permissions with custom expiration.

### 3. Customer / Applicant (`ROLE_CUSTOMER`)
- Submit loan applications (Home, Personal, Business, Auto).
- Upload sensitive loan documents (automatically encrypted and deduplicated).
- Download and decrypt own loan documents.
- View document verification status.

---

## 9. Interactive UI & Visual Animations

- **Glassmorphism Design**: Frosted glass backdrop blur cards, modern typography, and ambient glowing orbs with CSS keyframe floating animations.
- **Micro-Interactions**: Shield icon rotational physics on hover, button lift effects, and custom scrollbars.
- **Live Visual Feedback**:
  - `pulse-dot`: Glowing emerald pulse indicating authenticated AEAD encryption active.
  - `pulse-dedup`: Indigo pulse badge indicating that duplicate physical storage was eliminated.
  - Real-time alert notifications for uploads, re-encryptions, and access grants.
- **1-Click Demo Credential Chips**: Rapid login switching between Admin, Officer, and Customer roles.

---

## 10. Step-by-Step Installation & Setup

### Prerequisites
- **Java 17** or later (Eclipse Adoptium Temurin recommended).
- **Maven 3.9+** (pre-packaged Maven included).
- **MySQL 8.x** (optional; the default configuration runs embedded H2 with MySQL compatibility mode).

### 1. Clone the Repository
```powershell
git clone https://github.com/Thanigaivelk-official/Secure-Data-Deduplication-with-Efficient-Re-Encryption-System-for-Loan-Documents.git
cd "Secure-Data-Deduplication-with-Efficient-Re-Encryption-System-for-Loan-Documents"
```

### 2. Configure Environment (Optional)
The application includes `.env.example`. By default, it runs out of the box using the local profile:
```properties
# Default Master Key Reference (256-bit Base64 Key Encryption Key)
MASTER_KEY_SECRET=u6B1Z7p4W8k3N9x2L5v8R1t4Y7q0W3e6B9m2P5z8K1v=
APP_TENANT_ID=DEFAULT_BANK_TENANT
STORAGE_PATH=./storage/encrypted
```

To run with **MySQL 8.x**:
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 3. Run the Application
```powershell
# Set Java and Maven paths (Windows PowerShell example)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;E:\Thanigaivel\maven\apache-maven-3.9.6\bin;$env:Path"

# Start the Spring Boot application
mvn spring-boot:run
```

Open your browser at: **`http://localhost:8080`**

---

## 11. Demonstration Walkthrough (Demo Accounts)

| Role | Username | Password | Purpose & Highlights |
| :--- | :--- | :--- | :--- |
| **System Admin** | `admin` | `Admin@123` | Full dashboard, storage savings metrics, audit logs, re-encryption triggers |
| **Loan Officer** | `officer_sarah` | `Officer@123` | Review loans, verify uploaded docs, grant/revoke sharing |
| **Loan Officer** | `officer_raj` | `Officer@123` | Secondary loan verification officer |
| **Customer** | `customer_john` | `Customer@123` | Home & Auto loan applicant; owns uploaded documents |
| **Customer** | `customer_alice` | `Customer@123` | Uploads identical corporate tax statement -> tests deduplication |
| **Customer** | `customer_robert` | `Customer@123` | Commercial loan applicant |

### Interactive Deduplication & Re-Encryption Walkthrough
1. **Explore Pre-Seeded Deduplication**:
   - Log in as **`admin`** (`Admin@123`).
   - Notice the top metric cards: **Logical Volume** vs. **Physical Stored**, and the **Physical Disk Saved** metric.
   - In the documents table, locate `Alice_Corporate_Tax_Statement.pdf`: it displays the animated **Deduplicated (2 refs)** badge because its content matches John's tax form. Only one encrypted ciphertext exists on disk!
2. **Download & Decryption**:
   - Click the **Download** icon on any document. The backend unwraps the DEK, decrypts the ciphertext via AES-256-GCM, and streams the original plaintext file to your browser.
3. **Trigger Controlled Re-Encryption**:
   - Click **Re-encrypt** on any document.
   - The system decrypts the file, generates a fresh AES-256 DEK and new 96-bit nonce, re-encrypts the ciphertext on disk, increments the key version ($v1 \to v2$), and appends a `RE_ENCRYPTION_EXECUTED` event to the audit log.
4. **Grant Document Sharing**:
   - Click the **Share** button on a document to grant permission to another user ID.
   - The recipient can now download the document without redundant ciphertext duplication.

---

## 12. Automated Verification & Tests

The project includes unit and integration tests covering cryptography, tamper resistance, deduplication, and download workflows:

```powershell
# Run the complete automated test suite
mvn clean test
```

### Verified Test Results
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.loandocs.CryptographyTests
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 (SHA-256 determinism, AES-GCM roundtrip, AEAD tamper detection)
[INFO] Running com.example.loandocs.DeduplicationAndReEncryptionIntegrationTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (Cross-loan deduplication, storage savings, download roundtrip)
[INFO] 
[INFO] Results:
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

---

## 13. Resume Summary & Interview Preparation

### Resume Project Description
> **Secure Data Deduplication with Efficient Re-Encryption for Loan Documents**  
> *Technologies: Java 17, Spring Boot, MySQL, Spring Security, AES-256-GCM, SHA-256, Hibernate, Bootstrap 5*  
> - Engineered a secure cloud-style resource-sharing platform for sensitive loan documents with content-based deduplication and authenticated encryption.
> - Implemented AES-256-GCM authenticated encryption with envelope encryption (KMS abstraction), generating unique 96-bit nonces to prevent replay and bit-flipping attacks.
> - Built a tenant-scoped canonical SHA-256 fingerprinting engine to eliminate redundant physical ciphertext storage across duplicate corporate and legal submissions while preventing cross-tenant equality leakage.
> - Developed efficient re-encryption and key-rotation workflows with role-based access control (Admin, Officer, Customer), access revocation, and append-only audit logging.

### Key Technical Interview Q&A
- **Q: Why AES-GCM instead of CBC?**  
  *A: AES-GCM provides AEAD (Authenticated Encryption with Associated Data). It guarantees both confidentiality and ciphertext authenticity via a 128-bit authentication tag, preventing padding oracle attacks and bit-flipping without needing a separate HMAC.*
- **Q: What is envelope encryption and why is it essential here?**  
  *A: Envelope encryption uses a Data Encryption Key (DEK) for the document payload and a Master Key (KEK) to protect the DEK. This allows rotating master secrets or re-wrapping keys for sharing without having to decrypt and re-encrypt gigabytes of data.*
- **Q: What is equality leakage in deduplication?**  
  *A: Deterministic hashing reveals if two users uploaded identical files. The system solves this via tenant-scoped deduplication, ensuring that equality discovery is isolated strictly within the authorized institution.*

---

## License
This project is open-source and available under the [MIT License](LICENSE).
