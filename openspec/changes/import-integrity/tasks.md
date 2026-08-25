# Tasks: Make Imports Safe and Complete

- [x] Define source-record identity and migrate/extend the transaction schema without losing existing data.
- [x] Refactor parser persistence to use validated atomic upserts and return inserted/skipped counts.
- [x] Add split-ratio extraction, persistence, and processing-to-FIFO forwarding.
- [x] Centralize project-relative path resolution for CLI, API, and manual fixes.
- [x] Add parser/database integration tests for idempotency, rollback, malformed rows, and split propagation.
- [x] Update README and API response documentation with import semantics.
- [x] Run the full pytest suite and a repeat-import smoke test against a temporary database.