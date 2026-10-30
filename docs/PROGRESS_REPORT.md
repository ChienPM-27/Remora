# Remora progress checkpoint

Updated: 2026-10-30. Current scope: T009 in `specs/002-trustworthy-memory/tasks.md` only. Prior T008 and T007 evidence is retained below.

Status: **T009 complete**. All 111 app unit tests, 13 core tests, lint, both APK builds and five Room instrumentation tests passed. The user's explicit physical-phone waiver for this branch remains in effect.

## T009 repository state

- Branch: `feat/astra-spec-kit-build`.
- Starting HEAD: `744ddfb3b4dd80717b33955115fc81e7b399b115` (`fix: enforce strong search and refresh results from live library`).
- The starting tree had staged T008 completion/phone-waiver notes in this report and tasks.md, plus untracked `.agents/skills/archify/` and `.archify/`. The T008 agreement is retained; both user directories remain untouched and excluded from the task checkpoint.

## T009 implementation

- ResurfacingManager loads current source state by ID, reuses completed batches across instances, and checkpoints both successful and empty evaluations. Per-card locks coalesce concurrent invocations. Missing, deleted, unprocessed, failed and already completed sources do not start another AI request.
- ResurfacingResultDao completes the source checkpoint and replaces its links in one Room transaction. Inserts that fail roll back the checkpoint and previous rows together. Only currently existing candidate IDs are inserted; a candidate deleted during evaluation can leave a completed empty batch. Updates target only resurfacing columns and never rewrite extraction, notes or vectors.
- Attempts are independent from card analysis and bounded to three per retry cycle, with the existing WorkManager exponential backoff. Offline/429 stay PENDING within the budget; exhaustion or a nonretryable client failure becomes FAILED. Cancellation propagates. Notification failure cannot undo a completed checkpoint or trigger another batch.
- SaveCoordinator retries only failed resurfacing state. Explicit restart replaces that card's unique resurfacing job so an old worker finishing its failure cannot swallow the retry. Ordinary pending/reconciliation requests retain KEEP; completed/missing cards are no-ops. Card analysis is not scheduled by this action.
- Shared validation accepts finite confidence strictly above 0.80 and at most 1.0, known unique candidates, compatible group/relation pairs and at most two links per group. It keeps the first valid occurrence and requires a nonblank reason and learning benefit.
- The client parser follows the existing gateway/AI-service success contracts: results must be an explicit array, each row contains the required fields, and candidate_id must be an exact integer literal. Invalid rows are discarded; a malformed/missing array fails rather than becoming a cached negative evaluation. The real client/evaluator are exercised with local HTTP interceptors and one concise text-only batch; no image, OCR or vector enters the request.
- GalleryViewModel combines current cards and link flows into separate source batches. Source/candidate edits and deletion update the feed. The screen shows the actual source, grouped candidates, reasons, benefits, original capitalization, and distinct pending/failed/completed-empty states. Both sides open the existing live detail by ID; retry delegates to the separate coordinator action.
- No dependency, database schema, public card model, backend route or AI model configuration changed.

## T009 verification evidence

| Check | Exit code | Observed result |
| --- | --- | --- |
| Initial `:app:testDebugUnitTest --tests com.remora.resurfacing.ResurfacingValidatorTest --console=plain` | 1 | 8 tests ran; 4 failed on the original validator, reproducing nonfinite/out-of-range confidence, duplicate candidate and missing explanation defects. |
| Targeted resurfacing/Gallery tests plus `:core:model:test` after the first fixes | 0 | The new pipeline/parser/validator tests and existing Gallery/core regressions passed. |
| First `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest :core:model:test --console=plain` | 0 | All 110 app tests and 13 core tests passed; lint and both APK builds succeeded. |
| Final same full command after the retry scheduling race fix and source-deletion guard | 0 | All 111 app unit tests passed with 0 failures/errors/skipped. Core test results contain 13 passing tests; the unchanged core task was up to date in this invocation. Lint and both APK builds succeeded, BUILD SUCCESSFUL in 22s. Lint XML has 0 errors and 80 warnings. |
| Pre-push `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest :core:model:test --console=plain` | 0 | BUILD SUCCESSFUL in 4s; 1 task executed and 86 were up to date. App/core tests, lint analysis and both APK builds reused the verified unchanged outputs; no new test execution is claimed. The sandbox attempt first failed on the wrapper cache lock, then the approved invocation succeeded. |
| Initial ADB inventory outside the sandbox | 0 | No connected devices. Pixel_7 was then started headlessly with the installed emulator, read-only data and snapshot saving disabled. |
| Boot query inside the sandbox | 1 | ADB could not create `\\.android`; repeated outside the sandbox with approval. |
| Targeted `:app:connectedDebugAndroidTest` on emulator-5558 | 0 | 5/5 ResurfacingPersistenceTest cases passed, 0 failures/errors/skipped, on Pixel_7, Android 17 / API 37. Runtime API level was read from ADB. BUILD SUCCESSFUL in 1m 39s. |
| `git diff --check` | 0 | No whitespace errors in the tracked diff. |

The final unit results contain 36 resurfacing tests (8 validator, 15 manager, 4 parser, 3 real evaluator/client, 5 live batch projection and 1 entity mapping) and 10 GalleryViewModel tests. These are included in the 111 app tests, not additional counts.

The Room tests verify empty checkpoint reuse by a new manager, source batch replacement, insert rollback, deleted source/candidate safety and retry scheduling that preserves extraction fields.

Targeted instrumentation command:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.remora.resurfacing.ResurfacingPersistenceTest' '-Pandroid.injected.device.serial=emulator-5558' --console=plain
```

Reports: `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `core/model/build/test-results/test/TEST-*.xml`, `app/build/reports/lint-results-debug.xml`, and `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 17-_app-.xml`. APKs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## T009 verification limits

- Physical-phone testing is waived by the existing 2026-10-30 branch agreement, recorded in the T008 section below. No physical-device execution is claimed.
- Emulator execution covers Room persistence and coordinator retry. Compose interaction, physical background capture/notifications, and live-provider relevance/latency were not exercised by T009. Source grouping and live updates are covered by deterministic ViewModel tests; provider calls use local HTTP interceptors.
- T009 is checked off. T010 and T011 remain outside this task's scope.

## Prior T008 repository state

- Branch: `feat/astra-spec-kit-build`.
- Verified T008 starting HEAD / T007 commit: `1c8802537a9b8eecb7fee86431a2d6fa49059851` (`feat: complete live card retry and deletion`). T007 is checked off in that commit.
- The starting tree contained only the untracked `.agents/skills/archify/` and `.archify/` directories. Both are preserved and excluded from the implementation commit.
- T007 introduced this report. Its prior starting HEAD was `6e3bfe852e8c5b8ac640a1d48208b949e574edf4`.

## T008 implementation

- Search accepts lexical evidence or cosine >= 0.55, as specified by the existing plan. The weak fallback, weak match enum/outcome flag and associated UI labels are removed. Weak-only unrelated candidates produce an empty outcome.
- The engine uses the existing Room library Flow and ranks a consistent card snapshot. Local keyword, note, abbreviation and time search include saved cards without a usable vector, including pending cards and corrupt-vector records. Semantic matches and the ready count require a DONE card with a valid stored vector.
- SearchViewModel observes that library for its lifetime. Completion, insertion, deletion and clear update counts and refresh the active query. Displayed card notes/status/content update during refresh; deleted IDs disappear immediately. Query edits and library changes cancel/invalidate previous requests, and late results cannot replace newer state or restore deleted cards.
- The existing 24-entry in-memory query-vector cache is retained; library refresh reuses successful embeddings. Debounce, immediate keyboard submit, recent queries, time labels, abbreviation expansion, lexical highlighting and OCR snippets remain available.
- Search diagnostics contain counts/status only. Raw/expanded queries, time phrases and exception details are not logged. Failure messages remain sanitized Vietnamese text.
- Search continues to open the shared T007 live detail by card ID. The ready-count UI describes semantic readiness and time-only results explicitly describe the date filter.
- Regression tests exercise the real SemanticSearchEngine and SearchViewModel, with an observable test DAO and controlled embedding callbacks. No dependency, schema, model ID, backend contract or application architecture changed. At this T008 checkpoint, T009 was unstarted.

## T008 verification evidence

| Check | Exit code | Observed result |
| --- | --- | --- |
| Initial targeted Gradle command inside the sandbox | 1 | Wrapper could not create its cache lock under `C:\.gradle`; no tests ran. Repeated outside the sandbox with approval. |
| `:app:testDebugUnitTest --tests com.remora.search.SemanticSearchEngineTest --tests com.remora.ui.search.SearchViewModelTest --console=plain` | 0 | 19/19 passed: 9 engine and 10 ViewModel regressions. |
| `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest --console=plain` | 0 | All 78 app unit tests passed; lint, debug APK and instrumentation APK build succeeded. |
| Final same full command after the ready-count label/import adjustment | 0 | All 78 app unit tests executed and passed again, with 0 failures/errors/skipped. Lint and both APK builds succeeded. Lint XML reports 0 errors and 80 warnings. |
| ADB device inventory outside the sandbox | 0 | `List of devices attached` with no entries. The sandbox invocation first failed creating its ADB directory; the approved invocation succeeded. No T008 device/UI execution is claimed. |

Reports: `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/tests/testDebugUnitTest/`, and `app/build/reports/lint-results-debug.xml`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## T008 verification limits and branch agreement

- On 2026-10-30 the user explicitly stated that physical-phone testing will not be performed on branch `feat/astra-spec-kit-build`. This branch agreement overrides the physical-phone checkpoint in AGENTS.md section 9 and applies to T008 and subsequent work on this branch. It does not claim that hardware tests ran.
- T008 is checked off based on its completed implementation, 19 passing search regressions, all 78 passing app unit tests, successful lint and both APK builds. The final tests were re-read before this documentation-only completion update; production code is unchanged and no additional test execution is claimed.
- Live-provider relevance/latency and phone responsiveness were not measured by these deterministic local tests. No threshold tuning or provider/model change was made.
- This completed T008 checkpoint is retained as historical evidence; the current T009 checkpoint is recorded above.

## T007 implementation

- `GalleryViewModel` selects detail by card ID from the existing reactive library flow. Updates to processing status and content appear in an open detail sheet; deletion removes that detail.
- `RemoraMainScreen` hosts the shared detail sheet. Timeline, gallery, search and resurfacing entry points pass a card ID. Search ranking, query handling and result refresh remain T008 work.
- Retry, delete and clear delegate to the existing `SaveCoordinator`. Retry no longer writes a stale domain-model snapshot over stored extraction checkpoints. Actions expose progress/errors, ignore repeated requests while busy, and preserve coroutine cancellation.
- Detail offers retry for failed processing and explicit delete confirmation. Settings retains clear-all confirmation and disables repeated clear requests. Both confirmations explain that original gallery images are retained.
- Existing coordinator cleanup is exercised by integration tests: cancel both types of card work, cascade incoming/outgoing links, delete owned images, retain other cards and source/legacy images, and reject late analysis results for deleted cards.
- No project dependency, database schema, backend contract or AI model configuration changed.

## Prior T007 verification evidence

Exit codes below are the actual process results, not inferred results.

| Check | Exit code | Observed result |
| --- | --- | --- |
| Initial Gradle invocation inside the sandbox | 1 | Wrapper could not create its cache lock; no checks ran. Repeated outside the sandbox with approval. |
| First `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest` | 1 | APK and unit tests succeeded; instrumentation compilation found two test-only errors. Fixed the invalid Compose import and nullable `WorkInfo` access. |
| `:app:assembleDebugAndroidTest lint testDebugUnitTest assembleDebug --console=plain` after those fixes | 0 | Lint and both APK builds succeeded. Unit tests were up to date from the successful test execution in the preceding invocation. |
| Targeted instrumentation on `emulator-5554`, Pixel 7, Android 17 / API 37 | 1 | 11 tests ran: all 7 `SaveCoordinatorTest` tests passed; all 4 `CardDetailScreenTest` tests stopped in Espresso with `NoSuchMethodException: InputManager.getInstance`. |
| Final `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest --console=plain` after the close-during-action regression fix | 0 | Lint, unit tests and debug APK succeeded; instrumentation APK compilation also succeeded. Unit tests executed again for the changed production code. |
| Four Compose tests on `emulator-5556`, temporary Android 15 / API 35 emulator | 0 | 4/4 passed, 0 skipped: live state/retry, confirmed deletion/cancel, busy/error state, and confirmed clear-all. |

The API 37 failure matches [Android Test issue #2507](https://github.com/android/android-test/issues/2507). A temporary API 35 AOSP x86_64 runtime is used to verify UI behavior with the existing test dependencies. Its archive and SHA-1 were verified against Google's official system-image index before extraction.

Targeted instrumentation command:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.remora.data.SaveCoordinatorTest,com.remora.ui.CardDetailScreenTest' '-Pandroid.injected.device.serial=emulator-5554' --console=plain

$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.remora.ui.CardDetailScreenTest' '-Pandroid.injected.device.serial=emulator-5556' --console=plain
```

## Prior T007 verification limits

- Automated T007 coverage passed: 7 integration tests on API 37 and 4 UI tests on API 35, plus the final lint/unit/build checks.
- On 2026-10-30 the user explicitly waived physical-phone testing for this task. No physical-device verification is claimed; T007 is marked complete based on the successful automated checks above.
- UI instrumentation on API 37 remains limited by the existing Espresso compatibility issue; the same UI tests passed on API 35.
- At the T007 commit, the next task was T008. Its current implementation and remaining acceptance are recorded above.
