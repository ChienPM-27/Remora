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
