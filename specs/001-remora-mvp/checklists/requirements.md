# Specification Quality Checklist: Remora MVP (Second Brain for Study Screenshots)

**Purpose**: Validate specification completeness and quality before proceeding to planning  
**Created**: 2026-10-22  
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) in user stories and requirements
- [x] Focused on user value, student workflows, and cognitive learning needs
- [x] Written clearly for stakeholders and judges
- [x] All mandatory sections completed (Problem Statement, Scope, Scenarios, Edge Cases, Requirements, Entities, Success Criteria, Assumptions)

## Requirement Completeness

- [x] 5 Must-have user stories prioritized and independently testable
- [x] Each user story has Given/When/Then acceptance scenarios
- [x] Each user story includes at least one boundary, negative, or failure scenario
- [x] Strict privacy boundary specified (no image transmission without explicit Save)
- [x] Graceful degradation and sanitization fallbacks clearly stated
- [x] Memory Resurfacing rules specified (3 groups, relation-group compatibility, confidence > 0.80, candidate_id integer preservation, empty results valid)

## Feature Readiness

- [x] Clear boundary between Must-have (MVP) and Should/Could-have
- [x] Measurable, technology-agnostic success criteria defined (SC-001 through SC-007)
- [x] Core entities defined with essential pedagogical and technical attributes
- [x] Assumptions explicitly documented

## Approved AI Pipeline Decisions and Planning Updates (2026-10-24)

These decisions supersede any conflicting earlier wording in this artifact:

- Search phrase "tuần trước" means the rolling window from 14 days before now through 7 days before now, not the previous calendar week. Add fixed-clock tests for the boundaries.
- When a screenshot hash matches an existing card, ask the user whether to reuse/link to the existing card or save a separate card. Never silently deduplicate or silently create a duplicate.
- Prefer a versioned JSON decoder and lazy normalization for legacy tags JSON. Use Room migration only if a concrete requirement cannot be met safely without a database schema change.
- SQLite FTS remains conditional. Implement it only if a controlled, versioned benchmark demonstrates meaningful benefit over semantic-only retrieval without unacceptable latency, reliability, or privacy regressions.
- These planning updates do not authorize product-code changes. Product-code implementation remains blocked until the user approves the final implementation plan.
