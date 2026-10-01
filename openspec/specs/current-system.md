# Current System Baseline

## Purpose and Scope

IBKR Tax Calculator is a local Python application for Polish PIT-38 preparation. It imports Interactive Brokers Activity Statement or Flex Query CSV data, stores normalized transactions locally, calculates FIFO capital gains and dividend income in PLN, and produces Excel and PDF reports. The repository also contains an Electron desktop shell with a local FastAPI service that exposes the same workflow through a small dashboard.

## Architecture

```text
IBKR CSV files
    |
    v
src/parser.py -- normalize, classify, deduplicate
    |
    v
transactions table via src/db_connector.py
    |
    v
src/processing.py -- legacy identity resolution + tax linking + NBP conversion
    |
    v
src/fifo.py -- per-identity FIFO lots, transfers, splits, realized P&L
    |                         \
    v                          v
src/data_collector.py     main.py adapter
    |                          |
    v                          v
src/excel_exporter.py     src/report_pdf.py
    |
    v
output/tax_report_*.xlsx and output/tax_report_*.pdf
```

The primary CLI entry point is `main.py`. Import mode scans `data/*.csv`; calculation mode reads transactions through `DBConnector`, runs `process_yearly_data`, and optionally exports Excel and PDF. `src/parser.py` also has a direct `--files` CLI. The desktop entry point is `gui/main.js`: it starts `gui/backend/api.py` as a child process and opens `gui/ui/index.html`. The FastAPI service provides `GET /health`, `GET /years`, `POST /import`, `GET /calculate/{year}`, and safe file-opening endpoints for generated reports.

## Components

- `src/parser.py`: parses transaction and Financial Instrument Information sections; extracts ticker, ISIN, conid, and instrument description; deduplicates records before persistence.
- `src/db_connector.py`: opens the configured database, additively initializes the single `transactions` table with nullable identity columns, and queries records through a context manager.
- `src/instrument_identity.py`: resolves legacy blank ISINs to the earliest observed identity and derives dated identity changes from stored history.
- `src/processing.py`: links dividend and tax rows by date/ticker/ISIN, fetches NBP rates, routes FIFO events, emits non-blocking identity-change diagnostics, and filters realized sales to the target year.
- `src/fifo.py`: preserves ticker-keyed behavior for single-identity history and partitions remapped tickers by ISIN; handles buys, sells, transfers, corporate actions, and split adjustments.
- `src/nbp.py`: obtains NBP exchange rates with T-1 lookup and in-process caching.
- `src/data_collector.py`, `src/excel_exporter.py`, `src/report_pdf.py`: transform results and write spreadsheet/PDF reports.
- `gui/backend/api.py`: FastAPI adapter around the CLI and calculation modules.
- `gui/main.js` and `gui/ui/index.html`: Electron process management and vanilla HTML/JavaScript dashboard.
- `tests/`: pytest coverage for parser helpers, FIFO behavior, NBP lookup, processing, splits, rounding, and selected database query construction.

## Use Cases

### Import IBKR data

Given one or more supported CSV files are present in `data/`, when the user runs `python main.py --import-data` or invokes `POST /import`, then the parser reads supported sections, persists ISIN/conid/instrument description where available, removes duplicate signatures, and writes the resulting records to the `transactions` table.

### Calculate a tax year

Given transactions exist through the requested year, when the user runs `python main.py --target-year YYYY`, then the system loads transactions up to December 31, converts non-PLN amounts using NBP rates, matches lots FIFO by ISIN when a ticker has multiple identities, and prints realized P&L, gross dividends, open lot count, and any dated identity-change warnings.

### Generate reports

Given a successful calculation, when Excel or PDF export is requested from the CLI or through `GET /calculate/{year}`, then the system writes reports below `output/` and exposes their availability to the GUI.

### Review results in the desktop UI

Given the Electron shell can start the local Python service, when the user opens the GUI, then the dashboard loads available years, allows re-import, starts a calculation, displays P&L/dividend/inventory metrics, and can open generated reports in the operating system.

## Dependencies and Constraints

- Python 3.10+ with `pandas`, `openpyxl`, `reportlab`, `requests`, `cryptography`, `fastapi`, `uvicorn`, and `sqlcipher3`; pytest tooling is used for verification.
- Electron 33 and vanilla JavaScript/HTML are used for the desktop UI; there is no frontend framework.
- The database schema is a single `transactions` table with PascalCase columns and is addressed through SQLite APIs.
- NBP network access is required for non-PLN conversion; results depend on external availability and rate data.
- FIFO and tax calculations use `Decimal` internally but export several values as floats.
- The application assumes local filesystem access to `data/`, `db/`, and `output/`; several paths still depend on the process working directory.
- The GUI backend binds to `127.0.0.1:8000`; CORS is restricted to local desktop/browser origins and the renderer uses a preload boundary.

## Known Problems and Technical Debt

- Database access now requires the SQLCipher-compatible driver and a non-empty key; installations must verify the native SQLCipher dependency before use.
- Key rotation requires the current key, verifies reopening with the new key, and requires a manual `.env` update after success.
- Import is now atomic and idempotent, preserving existing transactions and reporting inserted/skipped records.
- Instrument ISIN, conid, and description are stored additively; identity-only re-imports update existing rows without changing source keys.
- Remapped tickers receive separate FIFO histories, legacy blank ISINs resolve to the earliest observed identity, and reports disclose identity boundaries while keeping ticker summaries consolidated.
- FIFO coverage selects the identity active on the requested date and includes ISIN in lot evidence.
- Corporate-action ingestion now preserves supported split ratios through persistence and FIFO processing.
- FIFO now raises a diagnostic when a sell exceeds available inventory.
- NBP lookup now raises a diagnostic instead of silently using rate `1.0` after a failure.
- Excel export now raises an observable export error instead of silently reporting failure.
- GUI/API integration now exposes readiness and structured calculation errors; API and Electron integration tests cover the local workflow.
- The API converts a deliberate 404 from `/calculate/{year}` into a 500 through a broad exception handler.
- An encrypted temporary-database regression covers identity import through calculation and PDF report generation; CLI and Electron readiness remain outside end-to-end coverage.
- Electron renderer Node integration is disabled and context isolation is enabled through preload; the API uses restricted CORS.
- Documentation claims, sprint history, restart snapshots, and implementation disagree on encryption, GUI completion, API endpoints, and version numbers.

## Baseline Delta Boundaries

The proposed changes are intentionally separated: `database-security` owns encryption and key rotation; `import-integrity` owns persistence and corporate-action ingestion; `calculation-reliability` owns calculation diagnostics and exchange-rate failure behavior. None removes an existing public entry point, and their requirement names do not overlap.