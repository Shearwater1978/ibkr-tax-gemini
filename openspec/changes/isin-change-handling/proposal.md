## Why

The application identifies every instrument by ticker alone: `src/fifo.py` keys its lot queues on `trade["ticker"]`, and the `transactions` table has no ISIN column at all, even though IBKR supplies the ISIN (for example OKE = ONEOK INC, ISIN `US6826801036`, conid `10794`) in the trade description and the "Financial Instrument Information" section of every Activity Statement.

That makes the calculation silently wrong for any ticker whose ISIN changes over time. Two different securities that reuse the same ticker symbol — for example after a merger, a delisting and relisting, or a broker-side instrument remap — are collapsed into one FIFO queue, so a sale is matched against lots that were never acquired in that instrument. The result is a wrong cost basis, a wrong realized P&L, and no indication that anything is wrong. The user has no way to see or correct this today, because the identity that changed was never recorded.

## What Changes

- Persist instrument identity (ISIN, conid, and description) alongside the ticker for every imported transaction, so the database records which security each row actually refers to.
- Detect when one ticker symbol maps to more than one ISIN in imported history, and record the change boundary as a dated instrument-identity change rather than merging the histories.
- Track FIFO lots per instrument identity instead of per raw ticker symbol, so the old ISIN keeps its own acquisition history (buys, transfers, splits, dividends) and cannot be used to price a sale of the new ISIN.
- Keep the change transparent for the tax report: the user still sees one row per reported ticker position, with acquisition history intact and the identity change disclosed alongside it, rather than seeing an unexplained split or a duplicated ticker.
- Emit a non-blocking diagnostic naming the ticker, the previous ISIN, the new ISIN, and the change date whenever a change is detected, so the user can verify the boundary against the broker statement.
- Leave existing behavior unchanged when a ticker maps to exactly one ISIN, so current imports, calculations, and reports are unaffected.

## Capabilities

### New Capabilities

- `isin-change-handling`: Persistence of broker instrument identity, detection and dating of ISIN changes per ticker, per-identity FIFO lot tracking, and transparent disclosure of an identity change in calculation output and tax reports.

### Modified Capabilities

None. No existing spec changes its requirements: `fifo-coverage-check`, `calculation-reliability`, `import-integrity`, and the other applied capabilities keep their current behavior. The new capability constrains them without altering what they require.

## Impact

- **Parsing / import:** `src/parser.py` — capture ISIN and conid from the trade description and the "Financial Instrument Information" section, and carry them on every normalized record, including the records produced by `src/ib_normalizer.py` for the live API paths.
- **Storage:** `src/db_connector.py::initialize_schema()` — add ISIN/conid columns via the same additive `ALTER TABLE` path already used for `SourceKey` and `SplitRatio`, with a backfill for existing rows; the change is additive and requires no key rotation or re-encryption.
- **Calculation:** `src/fifo.py` and `src/processing.py` — the FIFO queue key becomes an instrument identity instead of a bare ticker; `src/fifo_coverage.py` must report the identity it covered so planned-sale results stay unambiguous.
- **Reporting:** `src/data_collector.py`, `src/excel_exporter.py`, `src/report_pdf.py` — surface the identity change in the per-ticker summary and history sheets.
- **CLI / API / GUI:** `main.py` and `gui/backend/api.py` — the diagnostic is printed and returned with the calculation result; `GET /calculate/{year}` and the `--ticker` filter must match a ticker across all of its identities.
- **Tests:** `tests/test_parser.py`, `tests/test_fifo.py`, `tests/test_db_connector.py`, `tests/test_import_integrity.py`, `tests/test_fifo_coverage.py` gain ISIN-change cases. No behavior change is expected for existing tests.
- **Migration:** existing databases gain nullable ISIN columns; rows imported before this change have no ISIN and are treated as belonging to the ticker's earliest observed identity, so historical calculations stay stable.
