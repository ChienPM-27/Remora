# Remora progress checkpoint

Updated: 2026-10-30. Current scope: T008 in `specs/002-trustworthy-memory/tasks.md` only. Prior T007 evidence is retained below.

Status: **T008 implemented and automatically verified**. Device acceptance remains pending, so its task checkbox stays open. T007 remains complete with the user's accepted emulator verification.

## Repository state

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
- Regression tests exercise the real SemanticSearchEngine and SearchViewModel, with an observable test DAO and controlled embedding callbacks. No dependency, schema, model ID, backend contract or application architecture changed. T009 has not started.

## T008 verification evidence

| Check | Exit code | Observed result |
| --- | --- | --- |
| Initial targeted Gradle command inside the sandbox | 1 | Wrapper could not create its cache lock under `C:\.gradle`; no tests ran. Repeated outside the sandbox with approval. |
| `:app:testDebugUnitTest --tests com.remora.search.SemanticSearchEngineTest --tests com.remora.ui.search.SearchViewModelTest --console=plain` | 0 | 19/19 passed: 9 engine and 10 ViewModel regressions. |
| `lint testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest --console=plain` | 0 | All 78 app unit tests passed; lint, debug APK and instrumentation APK build succeeded. |
| Final same full command after the ready-count label/import adjustment | 0 | All 78 app unit tests executed and passed again, with 0 failures/errors/skipped. Lint and both APK builds succeeded. Lint XML reports 0 errors and 80 warnings. |
| ADB device inventory outside the sandbox | 0 | `List of devices attached` with no entries. The sandbox invocation first failed creating its ADB directory; the approved invocation succeeded. No T008 device/UI execution is claimed. |

Reports: `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/tests/testDebugUnitTest/`, and `app/build/reports/lint-results-debug.xml`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## T008 remaining acceptance

- AGENTS.md section 9 requires physical-phone verification for the demo path. No device is connected and no T008 waiver has been given. T008 remains unchecked solely for this checkpoint; implementation and automated checks above are complete. The earlier physical-phone waiver was explicitly for T007.
- Verify search on the demo phone: offline note/abbreviation hit and notice, weak-only empty result, time-only date filter, rapid query edits, save/complete/delete/clear refreshing counts/results, and current detail opened from a search result.
- Live-provider relevance/latency and phone responsiveness were not measured by these deterministic local tests. No threshold tuning or provider/model change was made.
- Stop at this T008 checkpoint. T009 is unstarted.

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
