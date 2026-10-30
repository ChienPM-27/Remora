# Implementation Plan: Trustworthy Screenshot Memory

**Branch**: `feat/astra-spec-kit-build` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

## Summary

Complete the existing local study-memory loop instead of expanding into cloud storage.
Use one save coordinator, durable Room checkpoints, unique WorkManager jobs, persistent controls,
strict search relevance and source-specific connections. Runtime sign-in removes APK passwords.

## Technical Context

**Language/Version**: Existing Kotlin 2.0.21, AGP 8.7.2, Gradle 8.11.1; no version change.
**Primary Dependencies**: Existing Compose BOM 2024.10.01, Room 2.6.1, WorkManager 2.10.0,
OkHttp 4.12.0, kotlinx.serialization, Timber, Ktor from services/build.gradle.kts.
These versions are read from repository manifests; baseline compilation succeeded.
**Storage**: Local Room v1 → v2 additive migration; private image directory. Backend retains
its existing stateless media processing and in-memory demo accounts/quotas.
**Testing**: Existing JUnit/coroutines tests, Ktor tests, Android instrumentation; mandatory lint/build.
**Target Platform**: Android min 28, target/compile 35; Ktor JVM backend, unchanged.
**Project Type**: Android app plus separate backend Gradle build.
**Performance Goals**: No file/network I/O on the main thread; candidate processing bounded to 15;
streamed import size cap 20 MiB; no redundant AI for completed stages.
**Constraints**: Preserve all v1 rows, original media, user changes, no new dependencies/models.
**Scale/Scope**: One local library, a few thousand cards; four existing destinations.

## Constitution Check

Before research and after design: PASS. Explicit Save gates imports, new copies are private,
upgrades additive, network bounded, source context retained, no APK credentials, no provider change.
Existing model IDs are reused as configuration, not newly verified assertions. Real-device and
live-provider checks must be reported honestly. No automatic cloud deployment.

## Project Structure

### Documentation (this feature)

spec.md, plan.md, research.md, data-model.md, contracts/behavior.md, quickstart.md, tasks.md.

### Source Code (repository root)

- app/src/main/java/com/remora/data/local/: v2 entities, migration and checkpoint DAOs.
- app/src/main/java/com/remora/data/: save/delete coordination.
- app/src/main/java/com/remora/work/: unique scheduling and resumable analysis/connections.
- app/src/main/java/com/remora/capture/: consent/preference-gated detection and Save routing.
- app/src/main/java/com/remora/ai/: runtime session, sanitized API errors and vector validation.
- app/src/main/java/com/remora/ui/: persistent settings, coherent library/detail/connections.
- app/src/main/java/com/remora/search/: strict relevance, fallback and reactive refresh.
- services/{gateway,ai-service}/: preserve routes; bound requests and sanitize errors/parsing.
- app/src/{test,androidTest}/ and services/*/src/test/: regression evidence.

**Structure Decision**: Existing modules/manual AppContainer remain. Add small concrete helpers,
not a general repository framework.

## Implementation Design

1. Add consentGranted to preferences. Existing setting rows imply historical consent; no setting
row means no consent. paused controls monitoring, notificationsEnabled controls notifications.
2. Add imageHash, extractionComplete, resurfacingStatus/attemptCount to Room cards, not public DTOs.
Store exact-byte SHA-256 with a unique nullable index. Existing hashes remain null.
3. SaveCoordinator streams private images on IO after Save, hashes, inserts PENDING in a transaction,
reuses exact duplicates, and schedules unique analysis. Startup reconciles pending stages.
4. Analysis checkpoints sanitized extraction before embedding. Update only extant rows. Cancellation
must escape; retry at most three attempts per user-initiated cycle. A separate unique resurfacing
worker checkpoints completed empty batches, retries independently, and honors notifications.
5. Sign in from Settings using the existing login-demo contract; persist only the token in private
preferences. Credentials remain in memory during login and are cleared afterwards. No APK password.
Expired authorization produces a sign-in recovery message; local library/search remain available.
6. Data flows retain source card identity. Library and detail observe current cards, support direct
retry/deletion. Connections show source → candidate and reason/benefit.
7. Search uses existing keyword/time parsing and 0.55 semantic acceptance; remove weak fallback.
Observe card changes; cache query vectors in memory; never log queries or provider response bodies.
8. Gateway adds timeout/body limits and bounded safe errors. AI parsing tolerates invalid shapes
without losing valid fields and handles responses without sensitive exception logging.

## Complexity Tracking

No new modules, services, or dependencies. New fields address concrete data-loss, consent,
duplicate-processing, and connection-cache defects; migration and integration tests justify them.
