# Remora progress checkpoint

Updated: 2026-10-30. Scope: T007 in `specs/002-trustworthy-memory/tasks.md` only.

Status: **T007 complete**, with emulator verification accepted by the user. This report is included in the T007 implementation commit.

## Repository state

- Branch: `feat/astra-spec-kit-build`.
- Verified starting HEAD: `6e3bfe852e8c5b8ac640a1d48208b949e574edf4`.
- The starting tree contained only the untracked `.agents/skills/archify/` and `.archify/` directories. Both are preserved and excluded from the implementation commit.
- This checkpoint did not exist at the starting HEAD; it was created for this delivery.

## T007 implementation

- `GalleryViewModel` selects detail by card ID from the existing reactive library flow. Updates to processing status and content appear in an open detail sheet; deletion removes that detail.
- `RemoraMainScreen` hosts the shared detail sheet. Timeline, gallery, search and resurfacing entry points pass a card ID. Search ranking, query handling and result refresh remain T008 work.
- Retry, delete and clear delegate to the existing `SaveCoordinator`. Retry no longer writes a stale domain-model snapshot over stored extraction checkpoints. Actions expose progress/errors, ignore repeated requests while busy, and preserve coroutine cancellation.
- Detail offers retry for failed processing and explicit delete confirmation. Settings retains clear-all confirmation and disables repeated clear requests. Both confirmations explain that original gallery images are retained.
- Existing coordinator cleanup is exercised by integration tests: cancel both types of card work, cascade incoming/outgoing links, delete owned images, retain other cards and source/legacy images, and reject late analysis results for deleted cards.
- No project dependency, database schema, backend contract or AI model configuration changed.

## Verification evidence

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

## Verification limits and next task

- Automated T007 coverage passed: 7 integration tests on API 37 and 4 UI tests on API 35, plus the final lint/unit/build checks.
- On 2026-10-30 the user explicitly waived physical-phone testing for this task. No physical-device verification is claimed; T007 is marked complete based on the successful automated checks above.
- UI instrumentation on API 37 remains limited by the existing Espresso compatibility issue; the same UI tests passed on API 35.
- The next task is **T008**: strong-only search and reactive library refresh. T008 has not been started.
