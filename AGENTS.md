# AGENTS.md: Remora

Context for coding agents in this repo (primary tool: Antigravity; also readable by Claude Code and Cursor). Read fully before any task.
Reply to the team in Vietnamese. Code, identifiers, comments and commit messages in English.

## 1. Mission and constraints

- Remora: an Android app that turns study screenshots into a "second brain". It understands each image, supports natural-language search, and proactively resurfaces related old screenshots when a new one is saved (Memory Resurfacing).
- Hackathon build: **24 hours**, demo on a real device in front of judges. A stable, demo-able Must-have flow beats extra features.
- Feature freeze at **h22**. After that: bug fixes, tests, demo prep only.
- Scope: **Must** = capture, save/skip notification with user intent, AI card, semantic search, Resurfacing (3 groups). **Should** = time-aware search, retry, settings. **Could** = FTS keyword search, import everything from gallery, backend sync. Do not start a Could item while a Must item is unfinished.
- Reference docs in `docs/reference/` (proposal, technical plan) are context only. Tasks come from Spec Kit `tasks.md`; backend design lives in `docs/architecture/`.

## 2. Working agreement with the agent

- **Spec Kit is the single planner** (constitution → specify → plan → tasks). Superpowers is for execution, debugging and review. Do not create a second plan elsewhere.
- Work one task at a time, from the task list. Keep each change small enough for a short PR.
- Before claiming a task is done: run `./gradlew lint testDebugUnitTest assembleDebug` and report the actual result. Never say "should work".
- Do not add a dependency, change architecture, or rename the data model without asking first.
- Do not recall model IDs, API parameters or library versions from memory. Check the official docs or make a real call, then record the verified value in `docs/decisions.md`.
- If a requirement is ambiguous, state your assumption in one line and proceed with the simplest option. Ask only when the choice is expensive to reverse.
- No speculative abstractions: no DI framework unless already present, no multi-module split, no generic "repository for everything".
- Write unit tests together with the code for pure logic (section 6). Do not defer them.

## 3. Stack (fixed for the MVP)

- Kotlin, Jetpack Compose, Kotlin Coroutines/Flow
- Room (SQLite), WorkManager
- Screenshot detection: MediaStore + ContentObserver. If the process dies in the background, fall back to a WorkManager content-URI trigger or a foreground service. Test on the real demo phone.
- AI: Gemini API behind the backend (`gateway` + `ai-service`, see the Backend block below). The app never holds the real API key.
  - Vision / card extraction: `gemini-3.5-flash-lite` (primary), `gemini-3.5-flash` (fallback). OCR is done by Gemini in the same call; there is no ML Kit step.
  - Embeddings: `gemini-embedding-2` (output dimension 768, taskType RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY).
  - For extraction calls use JSON mode with a response schema. Use the lowest thinking level that keeps JSON quality, to cut latency (verify the parameter name in the docs).
- Logging: Timber. Serialization: one library only (kotlinx.serialization unless the team decides otherwise).
- Vectors: on-device cosine similarity. No external vector database in the MVP.

Backend (decided; formalized in `docs/adr/`):

- Kotlin + Ktor, in a separate Gradle build under `services/` (not part of the Android build), monorepo.
- MVP services: `gateway` (token validation, rate limit, AI quota, request id, minimal auth) and `ai-service`. `core-service` is post-MVP.
- Self-issued JWT. Contract-first OpenAPI; DTOs are defined by the OpenAPI files, not shared directly from the Android `:core:model` module.
- PostgreSQL for accounts and usage only, initialized by SQL scripts, no migration tool in the MVP. The backend never stores images or `ocr_text`.
- Deploy with Docker Compose on a single VM: EC2 `t3.small` (x86) unless ADR 0003 decides otherwise.

Placeholders to fill: `applicationId = TODO`, `minSdk = TODO`, `PROXY_BASE_URL = TODO` (from `local.properties`, never committed).

## 4. Data and contracts

### Room tables

- `memory_card`: id, image_uri, created_at, user_intent, title, summary, tags_json (`{subject, topics[]}`), content_type, ocr_text, embedding (BLOB), status (`PENDING | DONE | FAILED`), attempt_count, error_code
- `resurfacing_result`: id, new_card_id, candidate_card_id, group, relation, new_info, benefit, reason, confidence, created_at
- `app_setting`: notifications_enabled, paused

Use enums (not free strings) for `status`, `content_type`, `group`, `relation`.

### Card extraction (one Gemini call per saved image)

Input: the image and `user_intent`. Output JSON, all fields required:

```json
{
  "title": "string, <= 80 chars",
  "summary": "string, 2-4 sentences, keep formulas, theorem names and numbers verbatim",
  "tags": { "subject": "string", "topics": ["string"] },
  "content_type": "THEORY | PROBLEM | SOLUTION | CODE | NOTES | UNKNOWN",
  "ocr_text": "string, all readable text in the image"
}
```

Validate after parsing. On missing or invalid fields: keep what is valid, set `tags.subject = "chưa phân loại"`, an invalid `content_type` becomes `UNKNOWN`, never crash, never drop the image.

### Embedding

- Embed the text built from, in this order: `user_intent`, `tags`, `summary`, then `ocr_text` truncated to a fixed limit.
- L2-normalize before storing, so cosine similarity equals the dot product.
- BLOB format: float32, little-endian, contiguous. Verify the vector size on decode.

### Resurfacing (two stages)

1. Stage 1, no LLM: cosine top-N (default 15, configurable) over stored cards, excluding the new card.
2. Stage 2, **one** Gemini call for the whole batch. Input: the new card and the N candidates (text fields only, no images). Output JSON:

```json
{
  "results": [
    {
      "candidate_id": 101,
      "group": "SIMILAR | COMPLEMENTARY | ACTIONABLE",
      "relation": "DUPLICATE | SUPPLEMENT | EXPLANATION | CONTRADICTION",
      "new_info": "string",
      "benefit": "string",
      "reason": "string, shown to the user",
      "confidence": 0.0
    }
  ]
}
```

Rules:
- Chỉ trả khi có lợi ích học tập cụ thể, relation phải khớp group:
  - `SIMILAR`: relation `DUPLICATE` hoặc `SUPPLEMENT`
  - `COMPLEMENTARY`: relation `EXPLANATION` hoặc `SUPPLEMENT`
  - `ACTIONABLE`: relation `SUPPLEMENT` hoặc `CONTRADICTION`
- `candidate_id` bắt buộc là số nguyên (INTEGER) khớp chính xác danh sách đầu vào; drop mọi `candidate_id` không có trong input.
- Tối đa 2 kết quả mỗi group; danh sách `results: []` rỗng là hợp lệ khi không có ứng viên liên quan (ca âm tính).
- Ngưỡng lọc: chỉ nhận kết quả khi `confidence > 0.80` (sử dụng điều kiện `>` thay vì `>=`).

### Search

1. Resolve the time range from the query (regex first: hôm qua, tuần trước, hôm bữa as ~2-3 weeks; LLM fallback only if time allows).
2. Filter by `created_at`, embed the query, cosine, apply a relevance threshold and return "no result" below it. Never show junk results.
3. Highlight the user's `user_intent` note on result cards.

## 5. Rules that must not be broken

- **Privacy:** an image leaves the device only after the user taps Save. Never log images or `ocr_text`; log ids, status, latency only. Show a clear statement of what leaves the device.
- **Secrets:** no API keys in the repo, APK, logs or tests. Use `local.properties` / CI secrets.
- **Cost and quota:** batch the Resurfacing candidates into one call, compress images before upload, skip duplicates by hash, never call again if a result is already stored.
- **Errors:** every network call has a timeout. Retry with backoff and a **maximum attempt count** (no infinite retry). 429 and offline → stay `PENDING` and requeue. After the limit → `FAILED` with a retry button.
- **UI never blocks on AI:** insert the card as `PENDING` first; the UI updates when the status changes.
- **Demo determinism:** results for the demo images are cached and reproducible. No random behavior in the demo path.

## 6. Testing

Write unit tests (JUnit, in CI) alongside the code for: cosine and normalization, BLOB encode/decode round-trip, card JSON parsing (valid, missing fields, malformed), time-range resolver, Resurfacing grouping and validation (unknown id, empty results, threshold).
UI: one Compose smoke test for Save → card → search. Everything else is verified manually on the real device.
Keep evidence for judges: CI run screenshot, test report, screen recording of the demo flow.

## 7. Git and CI

- Feature branches, small PRs, conventional commit messages (`feat:`, `fix:`, `test:`, `docs:`).
- CI on every PR: lint, unit tests, assemble APK, upload the APK artifact. Keep `main` green.

## 8. Project layout (proposed; adapt via Spec Kit plan)

```
app/src/main/java/<package>/
  capture/    ContentObserver, notification actions, consent
  data/       Room entities, DAOs, repositories
  ai/         proxy client, prompts (versioned), JSON parsing, embedding
  search/     cosine, time resolver, ranking
  resurfacing/ candidate selection, batch evaluation, grouping
  ui/         Compose screens: gallery, card detail, search, resurfacing
  work/       WorkManager workers (analyze, retry)
docs/         decisions.md, architecture diagram, evaluation results
```

## 9. Definition of done (per task)

Meets the task's acceptance criteria; unit tests added where section 6 applies; `lint`, tests and `assembleDebug` pass; no secrets or sensitive logs; verified once on the real phone for anything touching capture, notifications or the demo path; a short note in `docs/decisions.md` if a decision or verified API value was made.

## 10. Known risks (check early)

- Background capture killed by the phone vendor: test in h2-6 on the demo phone.
- Gemini latency or quota during the demo: preprocess the demo set, cache Resurfacing results, keep a hotspot and a backup device.
- Model IDs and API parameters change: verify, do not assume.