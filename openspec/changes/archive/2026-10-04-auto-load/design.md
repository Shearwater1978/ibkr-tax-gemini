## Context

Startup currently only waits for the backend and loads the year list. Import is idempotent (already stored records are skipped), so running it on every start is safe.

## Decisions

- Startup sequence: wait for backend, import, load years, calculate the first (latest) year. Reuse `runImport`, `loadYears`, and `calculate`.
- Import failure is non-fatal: calculation still runs on stored data.
- The latest year may be slow to calculate (rate lookups); the existing loader and status text show progress.

## Risks / Trade-offs

- Every start re-parses the files, adding a delay proportional to the data size; accepted for always-fresh data.
