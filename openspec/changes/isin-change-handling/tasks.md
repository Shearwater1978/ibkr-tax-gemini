## 1. Storage and Import

- [ ] 1.1 Add guarded `ISIN` and `Conid` columns to `DBConnector.initialize_schema()` following the existing `SourceKey`/`SplitRatio` `ALTER TABLE` pattern, include them in `get_trades_for_calculation()`, and verify an existing encrypted database opens and reads without key rotation
- [ ] 1.2 Add an ISIN extractor alongside `extract_ticker()` covering both the glued (`OKE(US6826801036)`) and spaced (`MGA (CA5592224011)`) description forms, and verify with parameterized tests in `tests/test_parser.py` against real sample lines from `data/`
- [ ] 1.3 Parse the `Financial Instrument Information` section in `parse_csv()` using name-based `get_col_idx` lookups to populate conid and to supply the ISIN when the description lacks one, and verify the narrower section variant in `U5801_20210315_20220107.csv` parses without error
- [ ] 1.4 Persist the resolved ISIN and conid on every normalized record for trades, dividends, taxes, and corporate actions, and verify an import against a real `data/` statement persists `US6826801036` for `OKE`
- [ ] 1.5 Verify re-import idempotency: re-running `--import-data` over already-imported statements updates identity in place and inserts no duplicate rows, confirming the `SourceKey` hash is unaffected
- [ ] 1.6 Report identity-resolved versus unresolved row counts in the import result, and verify the counts appear for a sample import

## 2. Detection and FIFO Identity

- [ ] 2.1 Implement derivation of dated identity changes by grouping loaded history on `(Ticker, ISIN)`, dating the boundary at the earliest transaction of the second identity, and verify with tests covering a ticker with one ISIN (no change) and a ticker with two (one change, correct date)
- [ ] 2.2 Resolve rows with an empty ISIN to the ticker's earliest observed ISIN at read time, and verify legacy rows with no ISIN neither create a spurious change nor alter existing FIFO results
- [ ] 2.3 Change the `src/fifo.py` queue key to an instrument identity while keeping `ticker` on every emitted lot, gain, and inventory entry, and verify the existing test suite passes unmodified
- [ ] 2.4 Verify a sale after an ISIN change draws its cost basis only from lots of the same identity, and that the previous identity retains its own open lots with original dates and prices
- [ ] 2.5 Verify a sale exceeding its own identity's inventory raises the existing insufficient-inventory diagnostic naming ticker and ISIN, and never consumes lots from the other identity
- [ ] 2.6 Verify dividends and corporate actions alter only the inventory of their own identity, and that splits, transfers, and spinoffs continue to work under the new key

## 3. Diagnostics, API, and Reporting

- [ ] 3.1 Add a non-blocking identity-change diagnostic carrying ticker, previous ISIN, new ISIN, and change date via `src/diagnostics.py`, and verify the calculation completes and returns results when a change is present
- [ ] 3.2 Verify no identity-change diagnostic is emitted and output is byte-identical to current behavior when no ticker has more than one ISIN
- [ ] 3.3 Propagate the diagnostic through `main.py` CLI output and the `GET /calculate/{year}` response in `gui/backend/api.py`, and verify both surfaces show it without surfacing a 500
- [ ] 3.4 Make the `--ticker` filter and the ticker-scoped calculation match all identities of that ticker, and verify each reported lot and result identifies its ISIN
- [ ] 3.5 Update `src/fifo_coverage.py` so a coverage check after a change computes available quantity from the identity in effect on the as-of date, and verify each evidence lot reports its ISIN
- [ ] 3.6 Add the ISIN column to the Excel and PDF history rows and disclose the identity change in the report, and verify a report for a remapped ticker shows the ticker once with both identities attributed correctly

## 4. Verification

- [ ] 4.1 Run the full existing test suite and verify it passes with no modifications to pre-existing assertions
- [ ] 4.2 Add regression tests for the OKE remap scenario end to end (import through report) using a temporary database, and verify the old ISIN keeps its acquisition history and the boundary is disclosed
- [ ] 4.3 Verify the documented rollback: after adding the columns, running the pre-change code against the same database still reads and calculates correctly
- [ ] 4.4 Update `WIKI_CONTENT.md` and `openspec/specs/current-system.md` to describe identity tracking, and verify every documented command and path exists
