# Remora Project Constitution

## Core Principles

### I. Privacy First (User Consent & Zero Sensitive Logging)
- An image MUST NOT leave the user's device until the user explicitly taps "Save" (Lưu) with user consent.
- The backend (`ai-service`) MUST be strictly stateless regarding user media; it MUST NOT store images, binary data, or `ocr_text` in persistent databases.
- Stage 2 Resurfacing batch evaluation MUST send only concise text metadata (title, summary, tags, content_type, user_intent); it MUST NEVER send images or `ocr_text` of candidate cards to the backend.
- Structured application logs MUST only record opaque identifiers (`request_id`, `card_id`), HTTP status codes, and latency metrics (`latency_ms`). Logs MUST NEVER contain raw screenshot images, extracted `ocr_text`, or personal notes.

### II. Absolute Secrets Protection
- API keys (Google Gemini API key), private tokens, and JWT signing secrets MUST NEVER be committed to Git repositories, packaged into Android APKs, written to client-side logs, or committed in test fixtures.
- The Android client MUST NEVER possess or directly request Google Gemini API keys. All AI operations MUST route through the backend `gateway` using short-lived Bearer JWTs.
- Secrets MUST be injected exclusively via untracked local configuration (`local.properties`), protected host environment files (`/opt/remora/.env` with `chmod 600`), or secure CI secrets.

### III. Single-Call Resurfacing Batching & Cost Optimization
- Stage 2 of Memory Resurfacing MUST batch the entire top-N candidate set (default 15 candidates) into exactly **one single LLM invocation**. Calling LLM endpoints in a loop over individual candidates is strictly prohibited.
- Images MUST be compressed and pre-processed on-device before transmission.
- Duplicate screenshots MUST be deduplicated by SHA-256 hash before calling AI extraction; already processed cards MUST NOT trigger redundant backend requests.

### IV. Fault Tolerance & Graceful Degradation
- Every network request across client-to-backend and service-to-service communication MUST have explicit timeout limits (e.g. 12s - 15s).
- Network retries MUST use exponential backoff with a hard maximum attempt count (`max_attempts`); infinite retry loops are prohibited.
- When encountering HTTP `429 Too Many Requests` or device offline conditions, cards MUST remain in `PENDING` state and be enqueued in WorkManager.
- If processing exceeds the maximum retry budget, the card MUST transition to `FAILED` status with an interactive "Retry" (Thử lại) button. The app MUST NEVER crash or drop the user's image due to AI/network failures.

### V. Asynchronous Non-Blocking UI
- The user interface MUST NEVER block waiting for AI model inference or network calls.
- Upon tapping Save, the app MUST immediately insert a `memory_card` record with `status = PENDING` into Room SQLite and dismiss the notification/sheet instantly.
- The UI MUST reflect the pending state immediately and update reactively via Kotlin Coroutines/Flow when processing completes (`status = DONE` or `status = FAILED`).

### VI. Simplicity & Anti-Speculative Abstraction
- The codebase MUST strictly adhere to YAGNI (You Aren't Gonna Need It) and lazy senior developer principles.
- No speculative abstractions: NO dependency injection frameworks (such as Hilt/Dagger) in the 24h MVP (manual dependency injection and factory functions only).
- NO multi-module premature splitting, and NO generic "repository for everything" bloat. Keep architecture flat, purposeful, and verifiable.

### VII. Concurrent Unit Testing for Pure Logic
- Unit tests MUST be authored simultaneously alongside pure business logic code, never deferred to later milestones.
- Mandatory test coverage includes:
  - Vector cosine similarity calculation and L2-normalization.
  - Float32 little-endian BLOB encode/decode round-trip verification.
  - Card extraction JSON parsing (handling valid payloads, missing fields, malformed schemas, and sanitization fallbacks).
  - Ambiguous Vietnamese time-range resolution (regex date filters).
  - Resurfacing batch grouping, relation-group conformance rules, candidate ID validation, and confidence threshold filtering (`confidence > 0.80`).

### VIII. Local Ownership and Data Preservation
- Saved screenshots and notes MUST remain useful offline. New saves MUST retain an app-owned image copy.
- Database upgrades MUST preserve cards and preferences through explicit migrations.
- Deletion MUST remove owned copies and connections without deleting original gallery images.
- Successful AI stages and empty resurfacing batches MUST be checkpointed to avoid repeated uploads.
- Deterministic synthetic test fixtures MUST remain separate from the user's actual library.

### IX. Coherent Product and Honest Release Gates
- Remora serves students and independent learners preserving meaningful screenshots. Library, search, connections, and settings MUST form real end-to-end journeys.
- Consent MUST be independent of monitoring/notification preferences. Controls MUST persist their actual behavior.
- Search MUST reject below-threshold results; every connection MUST identify the source card and a concrete learning benefit.
- Shared account passwords MUST NOT be compiled into distributable APKs. Provider keys remain backend-only.
- Loading, offline, error, empty, and retry states MUST be explicit; invented user notes and placeholder actions are prohibited.
- Production readiness requires operational auth/quotas, release signing, provider availability, and real-device evidence. Passing a build alone does not establish readiness.
- The user's 2026-10-30 product-development mandate supersedes the elapsed hackathon h22 freeze.

---

## Quality Gates & Definition of Done

1. **Verification Before Assertion:**
   - Before claiming any task or PR is complete, the agent or developer MUST execute:
     ```bash
     ./gradlew lint testDebugUnitTest assembleDebug
     ```
   - Actual command execution output MUST be reported. Phrases like "should work" or "looks good" without verified execution logs are strictly forbidden.

2. **Zero Unverified Assumptions:**
   - Model IDs, API parameters, rate limits, and library versions MUST NOT be guessed from memory. Every parameter must be verified against official documentation or verified live requests and documented in `docs/decisions.md`.

3. **Security & Privacy Audit:**
   - Every change must verify that no API keys, private credentials, or raw student screenshot text are leaked into git commits, APK builds, or runtime logs.

4. **Documentation Integrity:**
   - Any architectural decision, configuration change, or verified parameter MUST be accompanied by a concise update in `docs/decisions.md`.

---

## Development Workflow & Working Agreement

1. **Spec Kit as Single Planner:**
   - Spec Kit is the authoritative lifecycle planner (`constitution` → `specify` → `clarify` → `plan` → `tasks` → `analyze` → `implement`). No secondary plans or rogue task trackers may be created.
2. **Atomic Task Execution:**
   - Work proceeds strictly one task at a time following the dependency order in `tasks.md`. Each change must remain compact and reviewable.
3. **Communication Protocol:**
   - Communication with the human team is conducted in **Vietnamese**.
   - Code, identifiers, test names, schema fields, architectural comments, and git commit messages are written exclusively in **English**.
4. **Disambiguation Rule:**
   - If a requirement is ambiguous, state the chosen assumption in one clear sentence and proceed with the simplest viable option. Prompt the user for input only when a decision is expensive or destructive to reverse.

---

## Governance

- This Constitution guides active specifications; explicit user instructions take precedence. Historical MVP documents remain evidence, not the current task queue.
- The 2026-10-30 autonomous product mandate authorizes reversible product and architecture choices with documented assumptions; deployments and paid infrastructure remain separate operational actions.
- Any amendment to these principles requires explicit human approval, documentation in `docs/decisions.md`, and a formal version bump:
  - **MAJOR**: Breaking changes to core principles, privacy boundaries, or hackathon scope.
  - **MINOR**: Addition of new principles, quality gates, or expanded operational constraints.
  - **PATCH**: Wording improvements, typos, and clarifying adjustments.
- All code reviews, CI checks, and agent tasks MUST verify full compliance against this document before merging.

**Version**: 2.0.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-30
