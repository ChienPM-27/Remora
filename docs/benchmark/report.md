# AI Pipeline Benchmark Report — v1 (2026-10-24)

Branch: `feat/ai-pipeline-hardening`. Harness: `app/src/test/java/com/remora/benchmark/PipelineBenchmarkTest.kt`.
Dataset: `app/src/test/resources/benchmark/pipeline_benchmark_v1.json` (version 1, 24 synthetic study cards,
16 lexical queries, 8 temporal cases, 10 resurfacing-validation cases, 5 tags cases). All content is synthetic:
no real screenshots, no private gallery data, consent-safe by construction. Ground truth (`relevant_ids`,
expected ranges, expected valid sets) was assigned manually before running any channel, and is independent of
model output.

## Method

- Deterministic offline channels only: lexical retrieval (`QueryParser` + `LexicalMatcher`), temporal
  resolution (`TimeResolver`, fixed clock 1700000000000), Resurfacing validation (`ResurfaceValidator`),
  tags decoding (`CardTagsCodec`).
- Run: `./gradlew :app:testDebugUnitTest --tests "com.remora.benchmark.*"` (JDK 21).
- Gates asserted by the harness are product-derived, not invented: validator accuracy 100%, temporal accuracy
  100%, tags decoding 100%, and zero matches on no-relevant-result (junk) queries. Retrieval precision/recall
  are recorded as baseline numbers without arbitrary thresholds.

## Result summary

| Channel | Cases | Result |
|---|---|---|
| Temporal resolution | 8 | 8/8 PASS (100%) |
| Resurfacing validation | 10 | 10/10 PASS (100%) |
| Tags decoding (v1/v2/malformed) | 5 | 5/5 PASS (100%) |
| Lexical retrieval junk gate | 2 | PASS after coverage-rule fix (see below) |
| Lexical retrieval micro P/R | 16 queries | P=0.786, R=1.000 |

### Baseline → change driven by the benchmark

- Baseline run with the original `isMatch` rule (≥ 1/2 of terms) FAILED the junk gate:
  query "công thức nấu ăn" matched cards [3, 5, 20] through the generic term "công thức" (2 of 4 terms).
- Evidence-driven fix: queries with ≥ 3 content terms now require 2/3 term coverage
  (`QueryMatcher.kt` `LexicalResult.isMatch`); queries with ≤ 2 terms keep the ≥ 1/2 rule so
  single-word/abbreviation queries still match. After the fix the junk gate passes with R unchanged at 1.000.

### Lexical baseline by slice (post-fix)

| Slice | Precision | Recall |
|---|---|---|
| abbreviation (pttk, xstk) | 1.00 | 1.00 |
| mixed_language (EN/VN) | 1.00 | 1.00 |
| formula | 1.00 | 1.00 |
| diagram (ERD) | 1.00 | 1.00 |
| low_quality (mờ/garbled OCR) | 1.00 | 1.00 |
| overlapping_subjects | 1.00 | 1.00 |
| no_relevant_result (junk) | 1.00 (0 matches) | — |
| vietnamese | 0.50 | 1.00 |
| vietnamese_diacritics_folding | 0.33 | 1.00 |

Known residual weakness (recorded, not fixed): generic academic terms ("định lý", "tích phân"/"tích phần")
pull in same-subject cards (e.g. "định lý bayes" also matches Fubini and Gauss cards). Recall stays 1.0; the
production hybrid engine's rank fusion reorders these below exact matches, but the strong-match set itself is
over-inclusive for such queries. Candidate future fix (needs a larger fixture to validate): field-weighted
coverage or requiring the most specific term to match. No threshold was tuned without measurement.

## Blocked channels (no GEMINI_API_KEY in this environment)

Live-model quality could not be measured here: vision card extraction, embedding semantic quality
(Recall@k/MRR on real 768-d vectors), and Stage-2 batch LLM resurfacing judgments. Per the governing rule,
no prompt, model id, or semantic threshold was changed without a baseline. To run these channels:

1. Provision `GEMINI_API_KEY` (+ `INTERNAL_SERVICE_KEY`, `JWT_SECRET`, `DEMO_USERNAME`, `DEMO_PASSWORD`).
2. Start `services/` via docker compose.
3. Extend the fixture with approved images + expected extraction fields and query→card relevance labels.
4. Drive `/v1/ai/extract-card`, `/v1/ai/embeddings`, `/v1/ai/resurface-batch` and compute:
   extraction field exact-match, Recall@5/10 and MRR for search, resurfacing precision and negative-case rate.

Model ids in use (recorded in `docs/decisions.md` §1): `gemini-3.5-flash-lite` primary,
`gemini-3.5-flash` fallback, `gemini-embedding-2` @ 768 dims. Thresholds under measurement freeze:
`SEMANTIC_ACCEPT=0.55`, `SEMANTIC_WEAK=0.45`, resurfacing `confidence > 0.80` (strict).

## T044 — FTS / structured-filter evaluation memo

Question: add SQLite FTS (T033) or structured label filters to retrieval?

Evidence available today: the on-device lexical channel (QueryMatcher, in-memory) already reaches
R=1.0 / P≥0.33–1.0 by slice on the versioned fixture, and the production engine fuses it with cosine
similarity. FTS over `ocr_text` would add a third lexical channel with materially the same content as the
in-memory matcher for a 24-card-to-few-hundred-card corpus; its measurable benefit (recall on exact keyword
queries) is already covered, while its costs are real (Room schema change + migration, FTS maintenance,
index sync on every save).

Decision per the conditional gate in `tasks.md` (T033/T044): **do not implement FTS now.** Conditions to
revisit: (a) corpus grows so full-scan matching shows measurable latency, or (b) the live-embedding
benchmark (once unblocked) shows semantic recall failures that a persisted lexical index demonstrably fixes.
Structured label filters: same conclusion — hierarchy labels (subject/course/chapter) are now decodable
(T038) but nothing populates them yet; filtering on `tags.subject` is already implicitly covered by the
lexical field weights, and adding explicit filters needs populated labels plus labeled queries first.

## Scale benchmark v2 — lexical channel at 1k/5k cards (2026-10-26)

The v1 evidence rested on a 24-card fixture; too small to justify the FTS decision. v2 scales the lexical
measurement. Harness: `app/src/test/java/com/remora/benchmark/LexicalScaleBenchmarkTest.kt` (plain JVM,
JUnit4, no new dependencies; the v1 fixture and `PipelineBenchmarkTest` are untouched).

### Dataset construction

- Deterministic generator (fixed seed 20261026; the test asserts a rebuild is byte-identical) drawing
  title/subject/topics/intent/summary/ocr from a fixed synthetic Vietnamese study vocabulary mirroring the
  v1 fixture style. All content synthetic and consent-safe; no real user data anywhere in the artifacts.
- Two corpus sizes: **1,000** and **5,000** cards. Composition per size:
  - `planted`: 5 cards per planted query (10 queries) containing ALL target terms — the generator knows the
    exact ground truth by construction.
  - `genericSharing` (4% of corpus per pool, 5 pools): the generic half of a query without the specific
    target term ("định lý" without "bayes", "phân tích"/"tích phân" without "từng phần", "đạo hàm" without
    "riêng", "phân phối" without "chuẩn", "đường đi" without "euler"). These probe the coverage gate.
  - `nearMiss` (2% per pool, 2 pools): single-term probes ("SQL" without "join"; "quan hệ" without
    ERD/nhiều).
  - `noise`: the remainder, unrelated synthetic study cards sharing no query term.
- 12 queries: 2 exact Vietnamese keywords ("định lý Bayes", "phân phối chuẩn"), 1 diacritic-stripped
  multi-word ("tich phan tung phan"), 2 course-code abbreviations ("ctdl", "mmt"), 2 mixed EN/VN ("big O
  complexity", "sql join"), 1 formula, 1 multi-word ERD (4 terms), 1 graph query ("đường đi Euler"), and
  2 junk no-result queries ("công thức nấu ăn", "cách chơi game offline").

### Measured results

Per query: P/R vs planted ground truth; latency = sequential in-memory scan over the whole corpus
(production-like; one untimed JIT warm-up pass, then 3 measured passes whose result sets must be identical).

| size | planted/generic/nearMiss/noise | scan/query P50 | scan/query P95 | per card | micro P (planted) | micro R |
|---|---|---|---|---|---|---|
| 1,000 | 50 / 200 / 40 / 710 | 14.1 ms | 21.3 ms | ~14.1 µs | 0.181 | 1.000 |
| 5,000 | 50 / 1,000 / 200 / 3,750 | 61.5 ms | 80.3 ms | ~12.3 µs | 0.043 | 1.000 |

Per-query at 1k / 5k (P, R): bayes-exact 0.111/0.024 R=1.0; tich-phan-folded 0.100/0.024 R=1.0;
ctdl-abbrev 0.833/0.833 R=1.0; mmt-abbrev 1.000 R=1.0; big-o-mixed 1.000 R=1.0; sql-join 0.200/0.048
R=1.0; dao-ham-rieng 0.111/0.024 R=1.0; phan-phoi-chuan 0.111/0.024 R=1.0; erd-multi 1.000 R=1.0;
duong-di-euler 0.111/0.024 R=1.0. Junk queries matched **0 cards** at both sizes (gate holds).
Stability: 3 repeated passes returned identical result sets at both sizes (asserted).

Gates: recall >= 0.95 per planted query — actual **R = 1.000 on all 10** at both sizes; junk = 0 matches.

### Findings at scale

- Recall holds 1.000 at 1k and 5k; the small-fixture recall conclusion survives scaling.
- Precision on queries whose generic prefix alone reaches the 2/3 coverage gate collapses
  (0.111 → 0.024 as generic-sharing distractors grow) — the v1 "known residual weakness" confirmed and
  quantified at scale. Discriminative queries (abbreviation "mmt", mixed-language, 4-term ERD) keep
  P = 1.000. In production the hybrid engine's rank fusion reorders these below exact matches, but the
  strong-match set is over-inclusive; a candidate fix (field-weighted coverage / most-specific-term
  requirement) needs its own labeled corpus first.
- Two matcher behaviors surfaced that 24 cards could not show: (a) the acronym-containment rule matches
  "ctdl" inside the initials of "Công thức Định lý Bayes" ("ctdlb") — one false positive per corpus;
  (b) the <=2-term half-coverage rule lets single-term "SQL" cards match "sql join" (the sql near-miss
  pool leaks: P 0.200 at 1k, 0.048 at 5k).
- Latency is linear at ~12–14 µs per card per query on the development JVM (full scan, no caching):
  ~61–80 ms per query at 5,000 cards. Fine for the MVP corpus, but this is the concrete number to watch —
  it is the revisit trigger for an indexed lexical channel, and a phone CPU will be slower than this JVM.

### FTS verdict — cannot be benchmarked in this environment (deferred)

A real SQLite FTS4/FTS5 candidate could NOT be executed here; evidence from the repo:

1. Plain JVM unit tests (`testDebugUnitTest`) have no SQLite engine on the classpath:
   `app/build.gradle.kts` test deps are junit, kotlinx-coroutines-test, turbine only; there is no
   Robolectric and no JDBC SQLite driver (`org.xerial:sqlite-jdbc`) anywhere in `app/build.gradle.kts` or
   `gradle/libs.versions.toml`. Room's SQLite/FTS APIs are Android runtime classes — they cannot load in a
   plain JVM test.
2. The instrumented path (`androidTest`, where `androidx.room:room-testing` lives) requires an emulator or
   device; no emulator exists in this environment — the same blocker already recorded for `MigrationTest`
   in `docs/decisions.md` §21 ("chưa chạy được: không có emulator").
3. Adding a Robolectric/sqlite-jdbc dependency to fake one is forbidden by this task's constraints
   (no new Gradle dependencies).

Per the T033/T044 conditional gate, a comparison that cannot be run means: **FTS stays SKIPPED —
deferred, benchmark unavailable (2026-10-26)**. Revisit conditions: (a) an instrumented/emulator runtime
becomes available so a real FTS candidate can be measured fairly, or (b) corpus scan latency degrades on
a real device (measured ~12–14 µs/card/query on the dev JVM; linear), or (c) the live-embedding benchmark
(once unblocked) shows semantic recall failures that a persisted lexical index demonstrably fixes.
