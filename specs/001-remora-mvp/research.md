# Research & Technical Architecture Decisions: Remora MVP

**Feature Branch**: `001-remora-mvp`  
**Date**: 2026-10-22  
**Status**: APPROVED / VERIFIED  
**Context**: Hackathon 24h Build (Android + Ktor Microservices Backend)

---

## 1. Technical Context & Stack Decisions

### 1.1 Android Client Architecture & Stack
- **Decision**: Kotlin, Jetpack Compose, Room SQLite, WorkManager, Kotlin Coroutines & Flow, OkHttp/Retrofit or Ktor Client, Timber.
- **Application ID & Target SDK**: `applicationId = com.remora`, `minSdk = 28` (Android 9.0 Pie), `targetSdk = 34` / `compileSdk = 34`.
- **Dependency Injection**: **Manual DI / Factory functions** (AppContainer pattern). Absolutely NO Dagger/Hilt or Koin in the 24h MVP to eliminate annotation processing overhead, classpath conflicts, and unnecessary setup time.
- **Serialization**: `kotlinx.serialization` as the single serialization engine across both client and backend.
- **Rationale**: Minimal footprint, fastest compilation time, native coroutine integration, zero code-generation bugs during the hackathon.

### 1.2 Backend Microservices Architecture & Stack
- **Decision**: Kotlin + Ktor (Netty engine) hosted under `services/` monorepo with an independent Gradle build (separate from Android Gradle build).
- **Services in MVP**:
  1. `gateway`: Acts as the single entry point (port 8080 internal, TLS via Caddy on port 443). Embeds minimal demo authentication (`/v1/auth/login-demo`), JWT verification (HMAC-SHA256), per-user rate limiting (60 req/min), daily quota guard, request correlation tracing (`X-Request-Id`), and reverse-proxy routing to `ai-service`.
  2. `ai-service`: Internal service (port 8082, never exposed publicly). Holds the Gemini API key, manages versioned prompts, enforces structured JSON output schemas, and handles Gemini Vision extraction, text embeddings, and Resurfacing batch evaluation.
  3. `postgres`: PostgreSQL 16 Alpine container (port 5432, internal Docker network). Stores accounts, quotas, and access audit logs. Initialized via `init.sql`. **Zero migration tools (Flyway/Liquibase) in the MVP**.
- **Post-MVP Deferrals**: `auth-service` (split out when DAU > 1,000), `core-service` (cloud sync & `pgvector` multi-device backup).
- **Deployment Platform**: Docker Compose on AWS EC2 `t3.medium` (2 vCPU, 4GB RAM) in `ap-southeast-1` Singapore for the live demo. `t3.small` (2GB RAM + 2GB swap) retained strictly as a cost-fallback option.

---

## 2. AI Model Pipeline & Parameter Decisions

### 2.1 Vision / Card Extraction Model
- **Decision**: Primary: `gemini-3.5-flash-lite`. Secondary / Fallback: `gemini-3.5-flash`.
- **Rationale**:
  - Live benchmarking on 2026-10-22 verified that `gemini-3.5-flash-lite` provides higher Free Tier quota (15 RPM / 1,000 RPD) with solid accuracy on lecture slides (p50 latency ~3.3s).
  - `gemini-3.5-flash` provides ultra-fast latency (p50 ~1.6s) but Free Tier quota was empirically measured at only 5 RPM / 20 RPD on 2026-10-22.
  - Fallback policy: If `gemini-3.5-flash-lite` returns 429 or 5xx, retry with `gemini-3.5-flash` before backing off.
- **Extraction Protocol**: Single multimodal call performing both OCR and semantic comprehension. **Zero on-device ML Kit OCR dependency**.
- **Output Schema**:
  ```json
  {
    "title": "string, <= 80 chars",
    "summary": "string, 2-4 sentences, keep formulas, theorem names and numbers verbatim",
    "tags": { "subject": "string", "topics": ["string"] },
    "content_type": "THEORY | PROBLEM | SOLUTION | CODE | NOTES | UNKNOWN",
    "ocr_text": "string, all readable text in the image"
  }
  ```
- **Sanitization Fallback**: If fields are missing/corrupted, keep valid fields, default `tags.subject = "chưa phân loại"`, default `content_type = "UNKNOWN"`, and never drop the image or crash.

### 2.2 Text Embedding Model & Format
- **Decision**: Model `gemini-embedding-2` via Google Gemini API.
- **Output Dimensionality**: Verified and fixed at **768 dimensions** (`outputDimensionality = 768`).
- **Task Types**: `RETRIEVAL_DOCUMENT` when embedding a saved card; `RETRIEVAL_QUERY` when embedding a search query.
- **Embedded Text Composition**: Concatenated in strict order:
  `user_intent + " " + tags.subject + " " + tags.topics.join(" ") + " " + summary + " " + ocr_text.take(1000)`.
- **Normalization & Storage**: L2-normalized on receipt. Serialized as a contiguous 768-element Float32 little-endian array stored directly as an SQLite `BLOB` (3,072 bytes).
- **Similarity Calculation**: Because vectors are L2-normalized, Cosine Similarity simplifies to the **Dot Product** (`sum(a[i] * b[i])`), yielding maximum computational speed on mobile CPU.

### 2.3 Memory Resurfacing Pipeline (2-Stage)
- **Stage 1 (On-Device Filtering - Zero LLM cost)**:
  - Retrieves all stored cards (excluding the newly saved card).
  - Computes dot product against the new card's embedding vector.
  - Selects Top-N candidates (default N = 15).
- **Stage 2 (Batch LLM Evaluation - Exactly 1 Call)**:
  - Input: New card metadata (5 text fields: `title`, `summary`, `tags`, `content_type`, `user_intent`) + N candidates (6 text fields each: `candidate_id`, `title`, `summary`, `tags`, `content_type`, `user_intent`).
  - **Zero images and zero `ocr_text` transmitted in Stage 2**.
  - Output Schema:
    ```json
    {
      "results": [
        {
          "candidate_id": 101,
          "group": "SIMILAR | COMPLEMENTARY | ACTIONABLE",
          "relation": "DUPLICATE | SUPPLEMENT | EXPLANATION | CONTRADICTION",
          "new_info": "string",
          "benefit": "string",
          "reason": "string, shown to student",
          "confidence": 0.92
        }
      ]
    }
    ```
- **Strict Verification Rules**:
  1. `candidate_id` MUST be an integer matching an ID from the input candidates. Any non-matching ID is discarded immediately.
  2. Relations MUST strictly align with the group:
     - `SIMILAR`: only `DUPLICATE` or `SUPPLEMENT`.
     - `COMPLEMENTARY`: only `EXPLANATION` or `SUPPLEMENT`.
     - `ACTIONABLE`: only `SUPPLEMENT` or `CONTRADICTION`.
  3. Maximum 2 items per group.
  4. Confidence filter: `confidence > 0.80` (strictly greater than).
  5. Negative cases: If no candidates share a genuine pedagogical link, an empty array (`"results": []`) is returned and accepted as valid.

---

## 3. Search & Time Resolver Strategy

### 3.1 Two-Phase Search Algorithm
1. **Temporal Expression Resolution (Regex First)**:
   - Evaluates query against Vietnamese regex patterns:
     - "hôm nay", "ngày hôm nay" $\rightarrow [startOfDay, now]$
     - "hôm qua" $\rightarrow [startOfYesterday, endOfYesterday]$
     - "tuần này" $\rightarrow [startOfWeek, now]$
     - "tuần trước" $\rightarrow [startOfLastWeek, endOfLastWeek]$
     - "hôm bữa", "mấy hôm trước", "dạo này" $\rightarrow [now - 21\text{ days}, now]$
     - "tháng này", "tháng trước" $\rightarrow$ respective calendar month bounds.
   - If a temporal pattern matches, cards in Room SQLite are pre-filtered by `created_at` timestamp before vector matching.
2. **Vector Similarity Ranking**:
   - Embeds user query via `gemini-embedding-2` (`taskType = RETRIEVAL_QUERY`).
   - Computes on-device dot product across candidate cards.
   - Applies relevance cutoff threshold (`MIN_SEARCH_SIMILARITY = 0.65`).
   - If top result is below threshold, return "Không tìm thấy kết quả" (zero junk results).
   - Results visually highlight the student's original `user_intent`.

---

## 4. Privacy, Security & Resilience Controls

### 4.1 Zero-Leakage Privacy Policy
- **Capture Hook**: `MediaStore` + `ContentObserver` detects new image files in `Pictures/Screenshots`.
- **Egress Boundary**: The image file is strictly read and retained on the device local cache until the user taps **Save**. If the user taps **Skip**, dismisses the notification, or ignores it, the image URI is dereferenced and never transmitted.
- **Client Compression**: Before upload, images are downscaled (max dimension 1560px) and converted to JPEG (80-85% quality, ~300KB-500KB), reducing transmission latency by 80%.
- **Deduplication**: Image SHA-256 hash is computed locally. If an existing `DONE` card shares the identical hash, AI extraction is bypassed, linking to the existing card data.

### 4.2 Non-Blocking Asynchronous UI
- Tapping Save creates a Room record with `status = PENDING` within < 300ms.
- WorkManager schedules background execution (`OneTimeWorkRequestBuilder<CardAnalyzeWorker>`) with `NetworkType.CONNECTED`.
- On HTTP 429 or network disconnect: card remains `PENDING`, WorkManager retries with exponential backoff (10s, 30s, 60s).
- Maximum retry attempts: 3. Upon exceeding 3 attempts, card transitions to `FAILED` with a user-facing Retry button.

---

## 5. Summary of Architecture Decisions (ADR Alignment)

| Area | Decision | Reference |
| --- | --- | --- |
| Microservices vs Monolith | Pragmatic Microservices: `gateway` (with embedded auth) + `ai-service` | ADR 0001 |
| Authentication | Self-Issued JWT (HMAC-SHA256) with demo seed credentials | ADR 0002 |
| Cloud Infrastructure | Docker Compose on AWS EC2 `t3.medium` (demo), `t3.small` (fallback) | ADR 0003 |
| Service Communication | Contract-first OpenAPI 3.1.0, internal HTTP with `X-Internal-Secret` | ADR 0004 |
| Data Ownership | Client owns images, OCR, vectors; Backend owns accounts, quotas, audit logs | ADR 0005 |
