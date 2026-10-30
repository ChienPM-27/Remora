# Data model

Room database version 2 is an additive migration from version 1, with no destructive fallback.

## memory_card additions

- image_hash: nullable TEXT, unique index. Exact SHA-256 hex of original saved bytes.
- extraction_complete: INTEGER NOT NULL DEFAULT 0. Existing DONE cards backfill to 1.
- resurfacing_status: TEXT NOT NULL DEFAULT 'PENDING'; PENDING / DONE / FAILED.
- resurfacing_attempt_count: INTEGER NOT NULL DEFAULT 0.
- All existing fields and IDs remain unchanged. Existing external image URIs remain valid.

Status progression: PENDING → extraction_complete → DONE when a valid embedding is stored.
Errors use stable categories, max three attempts. Manual retry resets only the failed stage.
A DONE card can independently have PENDING/FAILED connections; a completed empty batch is DONE.
Updates never insert missing cards. Deleting cards cascades both sides of links.

## app_setting addition

consent_granted INTEGER NOT NULL DEFAULT 0. Existing rows backfill to 1 because v1 only wrote
preferences after acceptance. notifications_enabled and paused retain their original values.
paused stops monitoring; notifications_enabled controls capture and connection alerts.

## resurfacing_result

Keep existing source/candidate foreign keys and columns. Replace each source batch transactionally.
A candidate appears at most once per source batch; confidence finite in (0.80, 1.0].
Group/relation compatibility is enforced before persistence.

## Files and session

New image copies in app-private imported_images; streaming cap 20 MiB, SHA-256 computed while copying.
Only app-owned paths may be deleted. Token session preferences are private and excluded from backup;
the password is never persisted or compiled. Session changes invalidate in-memory query/auth caches.
