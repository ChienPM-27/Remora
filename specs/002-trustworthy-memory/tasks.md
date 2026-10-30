# Tasks: Trustworthy Screenshot Memory

**Input**: spec.md, plan.md, research.md, data-model.md, contracts/behavior.md
**Branch**: feat/astra-spec-kit-build
**Execution**: Sequential tasks with tests alongside logic and a verification checkpoint per commit.

## Phase 1: Setup
- [x] T001 Verify baseline and product direction in specs/002-trustworthy-memory/research.md and .specify/memory/constitution.md. Android lint/unit/APK/core build passed before source changes.

## Phase 2: Foundation
- [x] T002 Add Room v2 additive migration and safe vector decoding in app/src/main/java/com/remora/data/local/RemoraDatabase.kt and entity/MemoryCardEntity.kt; add consent_granted default 0 with existing rows backfilled to 1, nullable unique image_hash, extraction_complete default 0 (DONE backfilled to 1), resurfacing_status default PENDING and resurfacing_attempt_count default 0. Include migration/vector tests in app/src/androidTest/java/com/remora/data/DatabaseMigrationTest.kt and app/src/test/java/com/remora/data/local/MemoryCardEntityTest.kt. Covers FR-002/003/007, SC-002.

## Phase 3: US1 — Save and control (P1)
**Independent validation**: duplicate offline save, restart, preferences and consent remain independent.
- [x] T003 [US1] Implement one durable SaveCoordinator and unique WorkScheduler in app/src/main/java/com/remora/data/SaveCoordinator.kt and work/WorkScheduler.kt; route capture/import through it. Stream exact-byte SHA-256 with 20 MiB cap, retain blank intent as null, reuse duplicates without replacing notes, clean failed copies and reconcile pending jobs. Include real Room/file tests in app/src/androidTest/java/com/remora/data/SaveCoordinatorTest.kt. Covers FR-001/003/004/005, SC-003/004.
- [x] T004 [US1] Connect persistent consent/settings to capture lifecycle in app/src/main/java/com/remora/ui/settings/SettingsScreen.kt, ui/consent/ConsentViewModel.kt, MainActivity.kt and capture/. Provide permission recovery, optional import-first use and consumed capture intents; use lifecycle-owned ViewModels. Test settings/consent logic. Covers FR-001/002/015.

## Phase 4: US2 — Understand and recover (P1)
**Independent validation**: retry after embedding failure uses saved extraction; deleted/completed cards are not processed again.
- [x] T005 [US2] Add runtime token-only AI session and login settings in app/src/main/java/com/remora/ai/AiGatewayClient.kt, ai/AiSessionStore.kt, ui/settings/SettingsScreen.kt and app/build.gradle.kts; remove APK demo passwords, sanitize errors and validate 768 finite nonzero vector elements. Add API/logic tests under app/src/test/java/com/remora/ai/. Covers FR-007/013/014.
- [x] T006 [US2] Checkpoint extraction and bounded attempts in app/src/main/java/com/remora/work/CardAnalyzeWorker.kt; propagate cancellation, avoid missing/completed-card work and schedule separate resurfacing. Add checkpoint/error tests in app/src/test/java/com/remora/work/. Covers FR-004/005/007, SC-003/004.
- [x] T007 [US2] Wire live detail retry and confirmed deletion/clear in app/src/main/java/com/remora/ui/gallery/CardDetailScreen.kt, ui/gallery/GalleryViewModel.kt, ui/RemoraMainScreen.kt and ui/timeline/TimelineScreen.kt. Cancel card jobs, remove links and only owned files; add cancellation/deletion integration coverage in app/src/androidTest/java/com/remora/data/SaveCoordinatorTest.kt. Covers FR-006/009/015, SC-006.

## Phase 5: US3 — Find the right memory (P1)
**Independent validation**: offline keyword hit, empty weak-only matches, query cancellation and reactive counts.
- [x] T008 [US3] Enforce strong-only search and reactive library refresh in app/src/main/java/com/remora/search/SemanticSearchEngine.kt and ui/search/SearchViewModel.kt; remove query logs, keep intent/time/abbreviation behavior and live details. Add regression tests in app/src/test/java/com/remora/search/SemanticSearchEngineTest.kt and ui/search/SearchViewModelTest.kt. Covers FR-008/009/013/015.
  - Implementation and automated verification completed on 2026-10-30: 19 search regressions and all 78 app unit tests passed; lint and debug/test APK builds passed. See docs/PROGRESS_REPORT.md.
  - On 2026-10-30 the user explicitly waived physical-phone testing for branch feat/astra-spec-kit-build. T008 is complete based on the verified automated checks; no physical-device execution is claimed. This checkpoint precedes T009.

## Phase 6: US4 — Reconnect with context (P1)
**Independent validation**: two source batches remain distinct; empty result cached; independent retry does not re-extract.
- [x] T009 [US4] Persist empty/success batch checkpoints and independent retry in app/src/main/java/com/remora/resurfacing/ResurfacingManager.kt and work/ResurfaceWorker.kt; validate finite confidence in (0.80, 1.0], unique known candidates and <=2/group. Display source/candidate/reason/benefit in ui/resurfacing/ResurfacingScreen.kt and ui/gallery/GalleryViewModel.kt. Extend app/src/test/java/com/remora/resurfacing/ tests. Covers FR-010/011/012, SC-005.
  - Completed on 2026-10-30: 111/111 app unit tests and 13/13 core tests passed; lint and debug/test APK builds passed. Five Room persistence/retry tests passed on Pixel_7, Android 17 / API 37, emulator-5558. See docs/PROGRESS_REPORT.md.
  - Successful and empty batches commit atomically, completed work is reused, and failed connections retry without extraction. Source-specific live batches retain both card identities and learning explanations. Physical-phone testing remains waived for this branch; live-provider and Compose UI execution are not claimed for T009.

## Phase 7: Cross-cutting verification and polish
- [ ] T010 Update backend error contracts before implementation in services/gateway/openapi.yaml and services/ai-service/openapi.yaml; bound gateway deadlines/body reads, sanitize upstream errors and robustly parse card fields in services/*/src/main/kotlin/com/remora/*/Application.kt. Add Ktor/parser regressions in services/*/src/test/kotlin/. Covers FR-005/007/013.
- [ ] T011 Complete UI smoke/migration/save/delete verification in app/src/androidTest/, add actual lint to .github/workflows/ci.yml, document setup/product in README.md and decisions in docs/decisions.md; run lint testDebugUnitTest assembleDebug, core/backend tests and available device checks; record actual results/limitations in specs/002-trustworthy-memory/verification.md. Covers all SC-001 through SC-007 and FR-015.

## Dependencies & Execution Order
T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011.
US1 delivers the first useful increment. All four stories remain within this delivery scope.
No parallel code edits are scheduled because shared UI/DAO files overlap. Independent read-only
research can run in parallel; Android and backend validation may run concurrently after edits settle.

## Implementation Strategy
Keep each task reviewable, run meaningful regression tests alongside changes, and commit verified
checkpoints. Do not mark hardware/provider acceptance complete when only local tests passed.
No production deployment or real-user data deletion is part of automated verification.
