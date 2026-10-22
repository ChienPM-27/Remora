<!--
SYNC IMPACT REPORT
- Version Change: Unratified Draft -> 1.0.0
- Ratification Date: 2026-10-22
- Primary Sources: AGENTS.md (Sections 2, 5, 9), docs/architecture/00-overview.md (Section 4), docs/decisions.md
- Core Principles Ratified:
  1. Privacy First (User Consent & Zero Sensitive Logging)
  2. Absolute Secrets Protection (No API Keys in Artifacts)
  3. Single-Call Resurfacing Batching & Cost Optimization
  4. Fault Tolerance & Graceful Degradation (Timeout, Bounded Retry, 429 Handling)
  5. Asynchronous Non-Blocking UI (Instant PENDING Card Creation)
  6. Simplicity & Anti-Speculative Abstraction (No Premature DI/Frameworks)
  7. Concurrent Unit Testing for Pure Logic (Zero Test Deferral)
  8. Deterministic Demo Execution (Reproducible Seed & Cache)
  9. Strict Feature Freeze at h22 (Bug Fixes & Demo Prep Only)
- Quality Gates & DoD: Mandatory `./gradlew lint testDebugUnitTest assembleDebug` reporting.
- Deferred Items / Follow-up TODOs: None.
-->

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

### VIII. Deterministic Demo Execution
- The demo path MUST be deterministic, reliable, and reproducible in front of judges.
- A pre-seeded demo dataset of 30–50 screenshots across 3–4 subjects (with matched theory-problem-solution pairs) MUST be pre-processed and cached.
- Resurfacing results and search rankings for the primary demo scenario MUST yield consistent, validated outputs without stochastic hallucinations or random failures.

### IX. Strict Feature Freeze at h22
- At milestone **h22** of the 24-hour hackathon, a strict feature freeze takes effect.
- Zero new features may be initiated after h22. All remaining time MUST be dedicated solely to bug fixing, regression testing, demo rehearsals, and presentation preparation.

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
   - Spec Kit is the authoritative lifecycle planner (`constitution` → `specify` → `plan` → `tasks` → `implement`). No secondary plans or rogue task trackers may be created.
2. **Atomic Task Execution:**
   - Work proceeds strictly one task at a time following the dependency order in `tasks.md`. Each change must remain compact and reviewable.
3. **Communication Protocol:**
   - Communication with the human team is conducted in **Vietnamese**.
   - Code, identifiers, test names, schema fields, architectural comments, and git commit messages are written exclusively in **English**.
4. **Disambiguation Rule:**
   - If a requirement is ambiguous, state the chosen assumption in one clear sentence and proceed with the simplest viable option. Prompt the user for input only when a decision is expensive or destructive to reverse.

---

## Governance

- This Constitution constitutes the supreme law of the Remora project. It supersedes all informal agreements, ad-hoc suggestions, and speculative refactoring.
- Any amendment to these principles requires explicit human approval, documentation in `docs/decisions.md`, and a formal version bump:
  - **MAJOR**: Breaking changes to core principles, privacy boundaries, or hackathon scope.
  - **MINOR**: Addition of new principles, quality gates, or expanded operational constraints.
  - **PATCH**: Wording improvements, typos, and clarifying adjustments.
- All code reviews, CI checks, and agent tasks MUST verify full compliance against this document before merging.

**Version**: 1.0.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-22
