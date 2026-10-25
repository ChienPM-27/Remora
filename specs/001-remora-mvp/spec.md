# Feature Specification: Remora MVP (Second Brain for Study Screenshots)

**Feature Branch**: `001-remora-mvp`

**Created**: 2026-10-22

**Status**: Approved

## Approved AI Pipeline Planning Decisions (2026-10-24)

- `tuần trước` means a rolling window from 14 days ago through 7 days ago, not the previous calendar week.
- When a screenshot hash matches an existing card, ask the user whether to reuse/link the existing card or save a separate card. Never silently deduplicate or silently create a duplicate.
- Prefer a versioned JSON decoder for legacy `tags_json`; add Room migration only if a requirement cannot be met safely through decoding/lazy normalization.
- SQLite FTS is conditional and must not be implemented unless a controlled benchmark demonstrates meaningful benefit over semantic-only retrieval without unacceptable latency, reliability, or privacy regressions.

**Input**: User description: "Remora MVP: An Android application that turns study screenshots into a second brain for students. It captures screenshots, understands content, supports natural-language and time-aware search, and proactively resurfaces relevant past knowledge via Memory Resurfacing."

## Clarifications & Resolution Log

### Resolved from Project Constitution & Decisions Log
- **AI Vision Model & Fallback:** Primary model is `gemini-3.5-flash-lite` (latency ~3.3s, high quota), with `gemini-3.5-flash` as secondary/fallback. OCR is performed directly in this call (zero ML Kit dependency).
- **Embedding Specifications:** Model is `gemini-embedding-2` configured with `outputDimensionality = 768` (L2-normalized into 768-float32 little-endian BLOB), supporting `taskType = RETRIEVAL_DOCUMENT` (for card storage) and `RETRIEVAL_QUERY` (for search).
- **Resurfacing Logic & Confidence:** Evaluated in 1 single batch call for top-15 candidates; strictly enforces relation-group alignment; confidence threshold is strictly `confidence > 0.80`; empty list (`[]`) is standard for non-related inputs; all candidate IDs are validated as integers.
- **Privacy & Storage Boundaries:** Images never leave the device before tapping Save. Backend is completely stateless regarding user images and full OCR text.
- **Staging / Demo Infrastructure:** AWS EC2 `t3.medium` (4GB RAM) for the demo instance; `t3.small` (2GB RAM + 2GB swap) as backup.

### Resolved Android Application Parameters
- **`applicationId`:** `com.remora` (Display Name: Remora)
- **`minSdk`:** `28` (Android 9.0 Pie) — guarantees full modern compatibility with MediaStore ContentObserver and Jetpack Compose.

---

## 1. Problem Statement & Target Users

### Problem Statement
- Students frequently capture screenshots of lecture slides, homework problems, math formulas, code snippets, and reference materials during study sessions.
- Over time, hundreds or thousands of unorganized images accumulate in the photo gallery without notes, subject tags, or searchability.
- Students quickly forget why they saved an image or what it contains, making retrieval tedious and stressful, particularly before exams.
- Consequence: Screenshots become a "forgotten graveyard" of static images—knowledge exists on the device but is practically unreachable when needed.

### Target Users
- High school, college, and university students studying STEM and humanities subjects who rely on screenshots as their primary digital note-taking shortcut.

### Core Solution (The 4-Step Journey)
1. **Capture (Thu thập):** Automatically detects new study screenshots on the device and presents an instant, non-intrusive action prompt allowing the user to Save (with an optional intent note), Skip, Mute, or Pause.
2. **Understand (Thấu hiểu):** Automatically transcribes text, formulas, and diagrams, generates an executive 2–4 sentence summary, assigns academic subject and topic tags, and categorizes the learning format into a structured knowledge card.
3. **Retrieve (Truy xuất):** Enables students to search across all saved knowledge cards using casual Vietnamese queries (even without exact keywords) and interprets fuzzy temporal references (such as "hôm bữa chụp", "tuần trước").
4. **Reconnect (Gợi nhớ tri thức - Memory Resurfacing):** Proactively resurfaces related historical cards when a new card is saved, categorizing connections into 3 learning groups (Similar, Complementary, Actionable) with clear explanations.

---

## 2. Scope Boundaries (MoSCoW)

### Must-Have (MVP 24h Hackathon)
- Automatic screenshot detection with interactive notification (Save, Skip, Mute, Pause).
- First-time consent screen with transparent data privacy disclosure.
- Intent capture: students can record a quick note stating why they saved the screenshot.
- Zero-leakage privacy boundary: an image never leaves the device before the user taps Save.
- AI card extraction: extracts title, summary, subject, topic tags, content type, and verbatim text.
- Resilient card lifecycle: instantaneous PENDING card creation, asynchronous transition to DONE or FAILED, with sanitization fallbacks for incomplete AI responses.
- Natural-language semantic search with relevance filtering and user intent highlighting.
- Memory Resurfacing 2-stage pipeline: on-device candidate ranking followed by batch relationship evaluation into 3 groups (SIMILAR, COMPLEMENTARY, ACTIONABLE) with `confidence > 0.80`.

### Should-Have (Post-Must Items)
- Vietnamese fuzzy time-range resolver (resolving phrases like "hôm qua", "tuần trước", "hôm bữa" to concrete date ranges before search).
- Background retry queue with exponential backoff for offline conditions and rate limits.
- Quick settings toggle to enable or disable screenshot monitoring.

### Could-Have (Explicitly Out of MVP Scope)
- Full-text keyword search (FTS) engine.
- Bulk gallery import of all historical images.
- Multi-device cloud synchronization and user account management beyond demo mode.

---

## 3. User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Screenshot Capture & Instant Actions (Priority: P1)

As a student studying on my phone,  
I want the system to detect when I take a screenshot and prompt me to save or discard it,  
So that I can capture study materials effortlessly without manually opening an app.

**Why this priority**: Capture is the entry point of the entire Remora workflow. If capture fails, no knowledge enters the second brain.

**Independent Test**: Can be tested independently on a real device by taking a screenshot while viewing a document; a prompt appears offering Save, Skip, Mute, and Pause actions.

**Acceptance Scenarios**:
1. **Given** background screenshot monitoring is active, **When** the user captures a screenshot of a study slide, **Then** an action notification appears within 1.5 seconds offering "Save", "Skip", "Mute", and "Pause".
2. **Given** the app is in the background or minimized, **When** a screenshot is taken in another app (e.g. web browser or PDF reader), **Then** the notification appears reliably without crashing or interrupting the active app.
3. **Given** the user taps "Skip" or dismisses the notification, **Then** the screenshot is ignored, no processing is initiated, and no data leaves the device.
4. *(Edge / Failure Case)*: **Given** the device operating system terminates background processes or revokes media access permissions, **When** the user opens Remora, **Then** the app detects the permission loss, guides the user to re-enable permissions, and restarts monitoring gracefully.

---

### User Story 2 - Privacy-First Saving with Student Intent (Priority: P1)

As a student saving a slide,  
I want to attach a quick note explaining why I saved it and know exactly what data leaves my device,  
So that my notes remain private and I can recall my original context later.

**Why this priority**: Student context (`user_intent`) is essential for semantic retrieval, and privacy consent is a non-negotiable legal and ethical constraint.

**Independent Test**: Can be tested by taking a screenshot, confirming the consent dialog on first run, inputting "Prepare for calculus midterm", and tapping Save. The card is immediately recorded with the intent note.

**Acceptance Scenarios**:
1. **Given** the user opens Remora for the first time, **When** screenshot capture is enabled, **Then** a transparent consent screen explains that images are only sent for AI analysis after tapping Save, and never stored permanently on the server.
2. **Given** a screenshot action notification, **When** the user taps "Save" and enters an intent note (e.g. "Công thức cần nhớ thi giữa kỳ"), **Then** the note is attached to the card and saved locally.
3. **Given** an image is captured on the device, **When** the user has NOT tapped Save, **Then** zero network requests are made, and zero image bytes leave the phone.
4. *(Edge / Failure Case)*: **Given** the user captures an image whose SHA-256 hash matches an existing card, **When** the user taps Save, **Then** the system informs the user that a duplicate exists and asks whether to reuse/link to the existing card or save a separate card. No AI request is made until the user's choice is explicit; the system must not silently discard the new save or silently create a duplicate.

---

### User Story 3 - Asynchronous Knowledge Card Extraction & Resilience (Priority: P1)

As a student who just saved a screenshot,  
I want the card to be created instantly and processed reliably in the background,  
So that my studying is never stalled waiting for AI processing, even during poor network conditions.

**Why this priority**: Waiting for an AI network call destroys user experience; background resilience ensures zero lost study notes.

**Independent Test**: Can be tested by tapping Save, verifying the card appears immediately in the gallery as PENDING, and verifying it updates to DONE when processing succeeds, or shows a Retry button when offline.

**Acceptance Scenarios**:
1. **Given** the user taps Save, **When** the save command is registered, **Then** a knowledge card with status PENDING appears in the local gallery within 300ms, and the notification is dismissed immediately.
2. **Given** a PENDING card, **When** background processing completes, **Then** the card status updates to DONE, populating a title (<= 80 chars), a 2–4 sentence summary, subject and topic tags, content type (THEORY, PROBLEM, SOLUTION, CODE, NOTES, or UNKNOWN), and verbatim extracted text.
3. *(Sanitization Fallback)*: **Given** the AI response contains missing or malformed fields, **When** the card is processed, **Then** the system keeps all valid fields, assigns subject = "chưa phân loại", sets content type = UNKNOWN, and never drops the image or crashes.
4. *(Edge / Failure Case)*: **Given** the device is offline or the server returns a rate limit (HTTP 429), **When** processing is attempted, **Then** the card remains PENDING, is queued for retry with exponential backoff; if retries exceed the limit, it transitions to FAILED with a user-facing "Retry" (Thử lại) button.

---

### User Story 4 - Natural-Language Semantic Search with Time Awareness (Priority: P1)

As a student preparing for an exam,  
I want to search my cards using natural Vietnamese descriptions and approximate time phrases,  
So that I can quickly find specific formulas or lecture slides even when I don't remember exact keywords.

**Why this priority**: Storing cards is useless if students cannot retrieve them quickly during revision.

**Independent Test**: Can be tested by searching "cái slide về phân phối chuẩn hôm bữa chụp" against a set of 30–50 pre-loaded cards; the Gauss normal distribution card appears at the top.

**Acceptance Scenarios**:
1. **Given** a library of saved knowledge cards, **When** the user types a descriptive query (e.g. "định luật bảo toàn cơ năng"), **Then** matching cards are returned ranked by semantic similarity, with the user's original intent note highlighted.
2. **Given** a query containing Vietnamese fuzzy time expressions (e.g. "hôm qua", "tuần trước", "hôm bữa"), **When** the search executes, **Then** the system filters cards by the resolved creation date range before semantic ranking.
3. *(Low Relevance / Irrelevant Query)*: **Given** the user enters a query that has no semantic match in the library (similarity score falls below the relevance threshold), **When** search completes, **Then** the system displays "Không tìm thấy kết quả" rather than displaying irrelevant junk results.

---

### User Story 5 - Proactive Knowledge Resurfacing (Memory Resurfacing) (Priority: P1)

As a student saving a new screenshot,  
I want to be proactively reminded of relevant previous cards organized by learning relationships,  
So that I can connect new lessons with prior knowledge, review prerequisites, and spot contradictions.

**Why this priority**: Memory Resurfacing is Remora's signature differentiator, turning disconnected screenshots into an active cognitive second brain.

**Independent Test**: Can be tested by saving a new card on "Định lý Thales"; the system surfaces the candidate card "Hệ quả Thales" under COMPLEMENTARY and "Bài tập Thales" under ACTIONABLE, with confidence > 0.80.

**Acceptance Scenarios**:
1. **Given** a newly saved knowledge card and a library of existing cards, **When** Resurfacing evaluation completes, **Then** surfaced candidates are categorized into up to 3 groups:
   - **SIMILAR (Tương đồng):** Relation MUST be `DUPLICATE` or `SUPPLEMENT`.
   - **COMPLEMENTARY (Bổ trợ):** Relation MUST be `EXPLANATION` or `SUPPLEMENT`.
   - **ACTIONABLE (Có giá trị sử dụng):** Relation MUST be `SUPPLEMENT` or `CONTRADICTION`.
2. **Given** surfaced candidates, **When** results are presented, **Then** each group contains at most 2 items, every item has an explicit educational reason shown to the student, and `confidence > 0.80`.
3. *(Negative Case - Zero Related Cards)*: **Given** a newly saved card in a completely unrelated subject (e.g. English grammar saved into a math/physics library), **When** Resurfacing evaluation runs, **Then** the system returns an empty list (`[]`), presenting zero unsolicited notifications and zero forced connections.
4. *(Candidate ID Integrity)*: **Given** the batch evaluation results, **When** processing returned candidate IDs, **Then** any candidate ID not present in the original input candidate pool is strictly discarded.

---

## 4. Edge Cases & System Boundaries

1. **Duplicate Screenshot Detection:**
   - When a student accidentally captures the same slide twice, SHA-256 image fingerprinting identifies the duplicate locally, reusing the existing knowledge card without invoking external AI analysis.
2. **Rate Limit & Daily Quota Depletion:**
   - If the AI service hits rate limits (5 RPM / 20 RPD on free tiers), cards remain safely in PENDING status. The student can continue taking screenshots without data loss; processing resumes automatically when quota resets.
3. **Network Disconnection & Offline Study:**
   - If the student is on an airplane or has no cell reception, screenshots are saved locally with intent notes. WorkManager resumes background processing seamlessly once network connectivity is restored.
4. **Aggressive OS Background Process Killing:**
   - Certain phone manufacturers kill long-running background observers. Remora provides a foreground service fallback or notification-based trigger to ensure screenshot capture reliability during live study sessions.
5. **No Hallucination in Resurfacing:**
   - Resurfacing prompts explicitly penalize forced connections. If a candidate does not have a genuine pedagogical relationship, it is excluded.

---

## 5. Functional Requirements *(mandatory)*

### Capture & Consent
- **FR-001**: System MUST monitor device media storage for newly created screenshot images.
- **FR-002**: System MUST display an actionable notification within 1.5 seconds of screenshot capture offering Save, Skip, Mute, and Pause.
- **FR-003**: System MUST provide a first-time onboarding consent screen explaining data privacy boundaries before enabling capture.
- **FR-004**: System MUST allow users to attach an optional textual intent note (`user_intent`) during capture.
- **FR-005**: System MUST guarantee that zero image data leaves the device unless the user explicitly confirms Save.
- **FR-026**: When a saved screenshot hash matches an existing card, the system MUST ask the user whether to reuse/link to the existing card or save a separate card; it MUST NOT silently deduplicate or silently create a duplicate.

### Knowledge Card Management & AI Processing
- **FR-006**: System MUST create a local knowledge card in `PENDING` state immediately upon user confirmation to save.
- **FR-007**: System MUST extract structured data for each saved card: title (<= 80 chars), summary (2–4 sentences), subject tag, topic tags list, content type, and full verbatim text. All extracted text fields (title, summary, tags, OCR) MUST pass sensitive-data redaction (payment card numbers via Luhn, contextual OTP codes, labeled passwords/PINs) before being stored, embedded, or logged. Redaction runs after AI extraction and therefore does not affect what is uploaded to the AI gateway.
- **FR-008**: System MUST classify content type into one of: `THEORY`, `PROBLEM`, `SOLUTION`, `CODE`, `NOTES`, `UNKNOWN`.
- **FR-009**: System MUST apply graceful sanitization if AI extraction omits fields: default subject to "chưa phân loại", content type to `UNKNOWN`, preserving the image unconditionally.
- **FR-010**: System MUST transition card status to `DONE` upon successful analysis and embedding generation.
- **FR-011**: System MUST transition card status to `FAILED` with a user-executable Retry action if processing exceeds maximum configured attempts.
- **FR-012**: System MUST generate an embedding vector representing the combined content (`user_intent`, tags, summary, truncated OCR text) for each card.

### Retrieval & Search
- **FR-013**: System MUST support natural-language semantic search across all saved cards using vector similarity.
- **FR-014**: System MUST interpret ambiguous Vietnamese time expressions in search queries ("hôm qua", "tuần trước", "hôm bữa") to constrain card creation dates. For `tuần trước`, the approved window is rolling 7–14 days before the current time, not a calendar week.
- **FR-015**: System MUST reject and filter out search results whose similarity score falls below the relevance threshold, displaying an empty state instead of irrelevant items.
- **FR-016**: System MUST visually highlight the student's original `user_intent` on search result cards.

### Memory Resurfacing
- **FR-017**: System MUST perform Stage 1 candidate selection by retrieving top-N (default 15) most semantically similar historical cards on-device.
- **FR-018**: System MUST batch the entire candidate pool into a single evaluation request for Stage 2 Resurfacing.
- **FR-019**: System MUST group resurfaced cards into three distinct learning groups: `SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`.
- **FR-020**: System MUST enforce group-relation compatibility:
  - `SIMILAR` allows only `DUPLICATE` or `SUPPLEMENT`.
  - `COMPLEMENTARY` allows only `EXPLANATION` or `SUPPLEMENT`.
  - `ACTIONABLE` allows only `SUPPLEMENT` or `CONTRADICTION`.
- **FR-021**: System MUST limit results to a maximum of 2 cards per group.
- **FR-022**: System MUST filter out any Resurfacing candidate with confidence score `<= 0.80` (only accept `confidence > 0.80`).
- **FR-023**: System MUST accept an empty results list (`[]`) as valid when no genuine pedagogical link exists.
- **FR-024**: System MUST strictly discard any resurfaced result whose candidate identifier does not match an input candidate.

### System Configuration & Security
- **FR-025**: System MUST NOT log raw student images, full OCR transcriptions, personal notes, or raw search query text in application logs.

---

## 6. Key Entities

- **MemoryCard (Thẻ tri thức):**
  - Represents a captured study asset.
  - Attributes: identifier, local image URI, creation timestamp, user intent note, title, summary, subject, topics list, content type, OCR transcription, vector embedding, processing status (`PENDING`, `DONE`, `FAILED`), retry attempt count, error code, image content hash (SHA-256 of original bytes, nullable).
- **ResurfacingLink (Liên kết gợi nhớ):**
  - Represents a discovered pedagogical connection between a newly saved card and a historical card.
  - Attributes: identifier, new card ID, candidate card ID, group (`SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`), relation (`DUPLICATE`, `SUPPLEMENT`, `EXPLANATION`, `CONTRADICTION`), new information, learning benefit, user-facing explanation reason, confidence score (> 0.80), creation timestamp.
- **AppSetting (Cài đặt ứng dụng):**
  - Controls system-wide capture and notification behavior.
  - Attributes: notifications enabled flag, temporary pause state, pause expiration time.

---

## 7. Success Criteria *(mandatory)*

### Measurable Outcomes
- **SC-001 (Capture Prompt Speed):** Screenshot capture action prompt appears on screen within **1.5 seconds** of screenshot capture on the real demo phone.
- **SC-002 (Instant Non-Blocking UI):** Tapping Save dismisses the prompt and displays the new card in the gallery as PENDING within **300 milliseconds**.
- **SC-003 (Card Analysis Latency):** Background card extraction and embedding generation complete within a median latency of **< 4.0 seconds** under standard network conditions.
- **SC-004 (Retrieval Accuracy):** For the benchmark query *"slide phân phối chuẩn hôm bữa chụp"*, the target Gaussian distribution slide is returned as the **#1 rank result** in a pre-loaded library of 30–50 demo cards.
- **SC-005 (Fuzzy Time Filter Precision):** Queries containing "hôm qua", "tuần trước", and "hôm bữa" accurately bound the candidate search space to their respective date windows (1 day, rolling 7–14 days before now, ~2–3 weeks) with zero date-overflow errors.
- **SC-006 (Resurfacing Precision & Negative Compliance):**
  - For a new card on "Định lý Thales", the system correctly identifies and displays the prerequisite theorem and practice problem with `confidence > 0.80`.
  - For an unrelated English grammar card, the system returns exactly zero surfaced items (`[]`), achieving **100% negative-case precision**.
- **SC-007 (Demo Determinism & Stability):** The complete end-to-end demo flow (Capture $\rightarrow$ Save with intent $\rightarrow$ AI card extraction $\rightarrow$ Resurfacing alert $\rightarrow$ Semantic search) executes without crashing, unhandled exceptions, or random failures across multiple practice runs.

---

## 8. Assumptions

1. **Target Environment:** The student device runs Android with active internet connectivity during the presentation demo.
2. **Language Scope:** The primary language for student intent notes, subject categorization, and search queries is Vietnamese.
3. **Data Localization:** Images remain stored on the local device file system; the backend server is completely stateless regarding user images.
4. **Demo Pre-seed:** A set of 30–50 realistic high school and college screenshots across Mathematics, Physics, Chemistry, and Computer Science will be pre-loaded and cached to guarantee demo determinism.
