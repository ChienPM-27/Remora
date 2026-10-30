# Validation guide

Use the existing JDK/Android SDK and backend environment; never paste secrets into commands or reports.

1. Run ./gradlew lint testDebugUnitTest assembleDebug :core:model:test.
2. Run ./gradlew --project-dir services test assemble.
3. Run node scripts/check-docs.js and the existing Spectral contract checks when contracts change.
4. With an Android device/emulator connected, run ./gradlew connectedDebugAndroidTest.
5. Open Remora, accept privacy disclosure, optionally leave capture permission denied.
6. Configure/sign in to the existing AI gateway in Settings. Select an image, add a note, Save.
7. Confirm PENDING appears and advances when AI is available; disable network and repeat to verify retention.
8. Select the same image again: one card remains and duplicate feedback appears.
9. Search a known note, a time-only phrase and an unrelated topic. Offline text search remains useful.
10. Open detail, retry a failure, inspect OCR/original, delete with confirmation.
11. Inspect connection batches for multiple sources and open source/candidate details.
12. Toggle monitoring/notifications, restart the activity/app, and verify persistence.
13. Confirm the upgrade test retains v1 rows and the delete test preserves external media.

Record actual test counts, device/API, build output, and live-AI limitations in verification.md after execution.
A device/emulator is not assumed; physical OEM background behavior and production operations are release gates.
