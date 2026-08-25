# Tasks: Make Imports Safe and Complete

- [ ] Define source-record identity and migrate/extend the transaction schema without losing existing data.
- [ ] Refactor parser persistence to use validated atomic upserts and return inserted/skipped counts.
- [ ] Add split-ratio extraction, persistence, and processing-to-FIFO forwarding.
- [ ] Centralize project-relative path resolution for CLI, API, and manual fixes.
- [ ] Add parser/database integration tests for idempotency, rollback, malformed rows, and split propagation.
- [ ] Update README and API response documentation with import semantics.
- [ ] Run the full pytest suite and a repeat-import smoke test against a temporary database.