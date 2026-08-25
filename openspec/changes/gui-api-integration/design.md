## Context

The current `main` branch contains the maintained Python calculation, import, database, and reporting modules, but no tracked `gui/` implementation. The `gui-start` branch provides a useful Electron/FastAPI proof of concept with `gui/main.js`, `gui/backend/api.py`, and `gui/ui/index.html`, but its API has broad CORS, weak Electron isolation, startup races, and broad exception handling. The new layer must call the current `main` modules rather than copy older source files.

## Goals / Non-Goals

**Goals:**

- Reintroduce the desktop dashboard and local API as a tested application layer.
- Preserve the current import counts, calculation diagnostics, SQLCipher configuration, and report contracts.
- Make backend readiness, HTTP errors, file opening, and renderer privileges explicit.

**Non-Goals:**

- Rewriting FIFO, NBP, parser, or report calculation logic.
- Adding cloud sync, authentication, or remote deployment.
- Merging the entire `gui-start` branch wholesale.

## Decisions

1. **Start from current `main`, then copy only GUI assets from `gui-start`.** This avoids overwriting maintained Python code. A full branch merge is rejected because the reference branch contains divergent `src/` implementations.
2. **Keep FastAPI as a thin adapter.** Endpoints call `run_import_routine`, `DBConnector`, `process_yearly_data`, and exporters; they do not duplicate business rules. A narrow exception mapping preserves 404 and maps calculation diagnostics to stable error payloads.
3. **Use explicit response models.** Import responses include inserted/skipped counts; calculation responses include summary, completeness, and independent Excel/PDF availability; diagnostic failures include a machine-readable code.
4. **Add a health/readiness endpoint and process lifecycle handling.** Electron waits for readiness before loading data and reports backend exit/startup failures. The fixed localhost binding remains local-only, with explicit CORS origins.
5. **Use Electron preload isolation.** Set `nodeIntegration` false and `contextIsolation` true; expose only the minimal report-opening or API bridge required by the UI through a preload script.
6. **Validate report paths from a project-controlled output directory.** The API constructs filenames from a validated year and fixed report type, never from arbitrary client paths.

## Risks / Trade-offs

- [Risk] Native SQLCipher installation can fail on a target machine → surface backend startup diagnostics and document the dependency.
- [Risk] The reference UI uses polling and inline scripts → retain the visual workflow but move privileged behavior behind preload and add a readiness state.
- [Risk] API and core calculation contracts evolve independently → add contract tests against the current Python functions and keep response models explicit.
- [Risk] Opening files is OS-specific → isolate platform handling and return stable errors when the OS cannot open a generated file.

## Migration Plan

1. Add the GUI/API implementation and tests on a feature branch from current `main`.
2. Run Python tests, API contract tests, Black, and an Electron startup smoke test.
3. Merge through a pull request; keep the old `gui-start` branch unchanged as historical reference.
4. Roll back by reverting the GUI/API commit; Python core and its database remain independently usable through `main.py`.
