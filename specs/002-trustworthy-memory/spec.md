# Feature Specification: Trustworthy Screenshot Memory

**Feature Branch**: `feat/astra-spec-kit-build`
**Created**: 2026-10-30
**Status**: Ready for planning
**Input**: Autonomous product ownership: turn Remora's existing implementation into a coherent,
reliable application for preserving, understanding, and rediscovering meaningful screenshots.

## Product interpretation

Remora is a personal study memory for Vietnamese students and independent learners. Its unit
of value is a useful screenshot with the person's reason for saving it and explainable links
to earlier learning. Success means finding and applying saved knowledge, not accumulating photos.

Retain the calm blue/ink visual identity and four destinations: Library, Search, Connections,
Settings. Make capture optional; a learner can start by selecting one image without granting
access to the entire gallery. Keep AI summaries and OCR clearly distinct from the original image
and the user's own note. Invest first in reliable ownership and recovery, then contextual retrieval.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Save once and keep control (Priority: P1)

A learner saves an image with an optional reason, keeps it locally, and can control monitoring
and notifications independently without repeatedly granting privacy consent.

**Why this priority**: Lost images and switches that do nothing undermine the entire product.
**Independent Test**: Save a selected image offline, reopen the app, toggle settings, and verify
the image and optional note remain available without an unwanted upload or consent prompt.

**Acceptance Scenarios**:
1. Given a new install, selecting or dismissing an image sends no image; only Save permits analysis.
2. Given a saved image, removing its original from the gallery does not remove Remora's copy.
3. Given identical image bytes saved twice, the existing card is reused, no additional AI is scheduled,
   and the interface explains the duplicate. Existing notes are preserved.
4. Given existing cards/preferences, upgrading preserves them and disabling notifications does not
   revoke consent or erase preferences. Disabling monitoring stops future capture prompts.
5. Given a failed save (unreadable/oversized image or storage error), the user sees an actionable
   error, and the app does not claim the image was saved.

### User Story 2 - Understand and recover without losing work (Priority: P1)

A learner sees a pending card immediately after local saving, gets its extracted title, summary,
tags and text, and can retry failed processing or remove unwanted cards.

**Why this priority**: AI availability must not determine whether the library is usable.
**Independent Test**: Interrupt processing after extraction, retry, and verify the image is not
uploaded again; delete a pending card and verify it stays deleted.

**Acceptance Scenarios**:
1. Given unavailable AI, saved images remain visible with pending/failed state and a useful explanation.
2. Given successful extraction but failed indexing, retry retains extracted content and resumes indexing.
3. Given a completed card, duplicate execution performs no additional extraction.
4. Given a failed card, Retry is available directly from its detail. Given any card, confirmed deletion
   removes its owned image and related connections; original gallery images remain untouched.
5. Given an AI response with invalid fields, valid text survives, unknown types use a safe fallback,
   and malformed responses produce a recoverable failure without exposing provider details.

### User Story 3 - Find the right memory (Priority: P1)

A learner searches using Vietnamese descriptions, abbreviations, notes, and approximate dates,
including when AI search is unavailable.

**Why this priority**: Retrieval is the payoff of capturing; unrelated results erode trust.
**Independent Test**: Search known notes offline, search an unrelated topic, and change a query quickly.

**Acceptance Scenarios**:
1. Given local cards and unavailable AI, matching local text appears with a clear fallback notice.
2. Given only weak semantic matches and no keyword match, show no results.
3. Given a newer query, an older response cannot replace it. Saving/deleting cards updates counts.
4. Given a time-only query, show cards in that time range with an explanation of the date filter.
5. Given a result, its original note is prominent and opening detail uses the current card state.

### User Story 4 - Reconnect learning with context (Priority: P1)

A learner sees which new screenshot prompted a connection, why an older screenshot helps,
and can open either card without ambiguity.

**Why this priority**: This is Remora's differentiation from a searchable image gallery.
**Independent Test**: Store batches for two source cards; each group remains attached to its own source.

**Acceptance Scenarios**:
1. Given multiple evaluated cards, connections are grouped by source and show Similar, Complementary,
   and Actionable relationships with a concrete reason/benefit.
2. Given no relevant candidates, persist a completed empty evaluation and show an honest empty state.
3. Given repeated work, existing results are reused; evaluation failure remains retryable separately
   from successful extraction. Removing either card removes obsolete links.
4. Given unknown candidate IDs, incompatible relationships, repeated candidates, invalid confidence,
   or confidence <= 0.80, reject them; at most two items per group are accepted.

### Edge Cases

- Process death between local insertion and job enqueue: reconcile pending work on application start.
- Revoked image/notification permissions: library and explicit import remain usable; settings offer recovery.
- Multiple saves/retries: deduplicate image identity and coalesce work per card.
- Cancelled work: propagate cancellation without overwriting a completed stage or recreating a deleted card.
- Corrupt vectors: keep the card readable and searchable by text; exclude invalid vectors from ranking.
- No configured AI access: local library works; settings allow runtime sign-in to the existing service.
- Settings reset/deletion: require in-app confirmation, preserve consent, never delete external originals.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Explicit Save is the sole image-analysis authorization across capture and import.
- **FR-002**: Persist consent, monitoring preference, and notification preference independently.
- **FR-003**: Own new saved image copies; deduplicate exact bytes and preserve existing data during upgrades.
- **FR-004**: Create visible pending cards without waiting for AI and retain successful processing stages.
- **FR-005**: Use finite retries, recover interrupted work, and coalesce repeated work for one card.
- **FR-006**: Permit retry and confirmed deletion from card detail, including owned image/link cleanup.
- **FR-007**: Validate AI fields and vectors while retaining valid local content.
- **FR-008**: Preserve local keyword/time search during AI failure and reject weak unrelated results.
- **FR-009**: Keep visible counts/results/details current after data changes and rapid query edits.
- **FR-010**: Associate every connection with its source and candidate; show reason and learning benefit.
- **FR-011**: Cache completed connection evaluations, including empty ones; independently retry failures.
- **FR-012**: Validate connection identity, confidence, group/relation and per-group limits.
- **FR-013**: Remove sensitive content from diagnostics and present sanitized user-facing errors.
- **FR-014**: Configure/sign in to AI at runtime without embedding shared account passwords in the app.
- **FR-015**: Provide consistent Vietnamese labels, accessible actions, and loading/empty/error states.

### Key Entities *(include if feature involves data)*

- **Memory card**: Owned image, optional user note, creation time, extracted content, processing state,
  stable identity, and checkpoints. The user's note is never fabricated.
- **Connection batch**: Source memory card, evaluation completion/retry state, and zero or more links.
- **Connection**: Source card, candidate card, learning group/relation, explanation and confidence.
- **Preferences**: Consent, monitoring enabled/paused, notification choice; independent from account access.
- **AI session**: Runtime service connection and short-lived authorization; account credentials are not content.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The Save → pending card → processed detail → search → connection flow passes an automated
  smoke scenario; connected-device tests and external AI checks are reported separately.
- **SC-002**: Existing cards and preferences survive an upgrade with zero lost rows in a migration test.
- **SC-003**: Duplicate saves and repeated completed jobs result in zero redundant analysis calls in tests.
- **SC-004**: An unavailable AI service causes zero lost images; local matching remains available.
- **SC-005**: All accepted connections satisfy identity/relation/confidence constraints; negative cases stay empty.
- **SC-006**: Deleting one card removes its library entry and links without altering other cards or gallery images.
- **SC-007**: The interface remains interactive while local files and AI are processed; document device measurements
  rather than promising the historical unmeasured 300 ms save and 4 s AI targets.

## Assumptions

- One person's local library is the supported deployment, initially up to a few thousand cards. No cloud photo
  storage, multi-user synchronization, social sharing, bulk import, or new account-registration service in this slice.
- Existing backend authentication and AI providers are reused. Runtime sign-in replaces packaged demo passwords;
  durable multi-user quotas, production identity, and release signing remain explicit deployment gates.
- Exact byte duplicates reuse the existing note; editing notes and re-embedding are deferred to avoid silent changes.
- Current language/branding remain Vietnamese-first. No new dependency or AI model is needed for this slice.
- Existing v1 data may reference external media; migration preserves those URIs, while every new save owns its copy.
- The autonomous mandate authorizes these reversible product choices; no unresolved costly choice blocks planning.
