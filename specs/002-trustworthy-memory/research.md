# Research: repository evidence and decisions

2026-10-30; baseline origin/main and feature branch: c857a41. Initial untracked .archify/ and
.agents/skills/archify/ are user work and excluded from task commits.

| Evidence | Decision and rationale | Alternative considered |
| --- | --- | --- |
| SettingsScreen toggles only remember state; ConsentViewModel treats notifications as consent | Persist independent consent; connect controls to Room and service lifecycle | Keep ephemeral switches: misleading |
| Save logic duplicated in RemoraMainScreen, CaptureActionReceiver, GalleryViewModel | One concrete save coordinator with durable images and hash identity | More per-entry fixes: drift/races |
| Worker uploads on every retry and has no DONE guard | Checkpoint extraction, unique jobs, separate connection retries | Repeat uploads: cost/privacy and data regression |
| MemoryCardEntity decodes arbitrary BLOB length | Validate 768 finite components; reject invalid vectors without losing card | Throw while loading library: unusable data |
| ResurfacingManager caches neither success nor empty; GalleryViewModel drops newCardId | Persist batch state and source context | Infer source from latest card: incorrect links |
| Search emits WEAK results and logs query text | Strict threshold, local text fallback, privacy-safe diagnostics | Fill list with weak matches: violates purpose |
| MainActivity constructs ViewModels directly | Lifecycle-owned factories and consumed capture intents | Reset/leak state on activity recreation |
| BuildConfig contains DEMO_PASSWORD | Runtime login using existing endpoint and private token-only session | New public account service: deployment scope |
| Gateway has no HttpTimeout; AI logs provider bodies | Bounded timeout and sanitized status-only failures | Infrastructure overhaul: unnecessary |

Baseline Android lint, testDebugUnitTest, assembleDebug and core tests: BUILD SUCCESSFUL (2m 2s).
No dependencies or model parameters changed. Existing model benchmarks in docs/decisions.md are
historical evidence, not a new live verification. Backend provider availability remains a separate check.
The plan skill's research-agent calls failed with rate limits; research completed by the primary agent.
