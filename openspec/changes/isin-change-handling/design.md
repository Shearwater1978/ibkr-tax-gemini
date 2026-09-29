## Context

See `proposal.md` - Why for the motivation.

Three facts about the current implementation shape this design:

1. **FIFO is keyed on a bare ticker.** `src/fifo.py` opens `self.inventory[ticker] = deque()` and every buy, sell, split, and transfer looks up `trade["ticker"]`. There is no identity dimension anywhere in the calculation layer, so separating identities means changing the queue key, not adding a filter.
2. **The identity is present in the source data but discarded.** The ISIN appears twice in an Activity Statement: embedded in the trade/dividend description (`OKE(US6826801036) Cash Dividend`, and with a space in the `MGA (CA5592224011)` variant that `extract_ticker` already special-cases) and in the standalone `Financial Instrument Information` section, which carries `Symbol, Description, Conid, Security ID, Underlying, Listing Exch, Multiplier, Type, Code`. `src/parser.py` currently has no branch for that section at all, so those rows are silently dropped. In the sample data, `OKE` maps to `US6826801036` / conid `10794` in all 19 statements, and some rows legitimately have an empty `Underlying` (e.g. `MED`, `SBRA`), so the section cannot be assumed uniform.
3. **Schema evolution is already additive and idempotent.** `DBConnector.initialize_schema()` creates the table with `CREATE TABLE IF NOT EXISTS`, then probes `PRAGMA table_info` and adds `SourceKey` and `SplitRatio` with guarded `ALTER TABLE`, and backfills legacy rows by hashing existing columns into `SourceKey`. The new columns follow that exact pattern, so no re-encryption, key rotation, or table rebuild is required, and the unique index on `SourceKey` is unaffected.

## Goals / Non-Goals

**Goals:**
- Make the instrument identity a first-class, persisted attribute that survives import, storage, calculation, and export.
- Change the FIFO queue key so identities cannot consume each other's lots.
- Keep the user-facing result a single, comprehensible ticker view with the change disclosed.
- Leave every existing import and calculation equivalent when no ISIN change is present.

**Non-Goals:**
- No security-master lookup. ISINs are taken from IBKR's own data; the system will not validate them against an external registry or infer an ISIN that IBKR did not supply.
- No retroactive repair of already-issued tax returns, and no attempt to decide whether an ISIN change was economically a continuation, merger consideration, or ticker reuse. That judgement stays with the user; the system's job is to stop the wrong lots from merging and to show the boundary.
- No price, FX, or corporate-action revaluation across the boundary. Prices are stored and used exactly as the broker reported them for that identity.
- Not a general multi-listing model; listing exchange stays a descriptive attribute only.

## Decisions

1. **Add `ISIN` (and `Conid`) columns rather than a new `instruments` lookup table.** The identity needs to travel with the row through every query and every export; a normalized `instruments` table would add a join to `get_trades_for_calculation()`, to the coverage check, and to both exporters, for a table holding one row per ticker-identity pair. **Alternative considered:** `instrument_identities(ticker, isin, conid, valid_from, valid_to)` expressing the change date as data rather than a derived value. Rejected for now because the change date is fully derivable from `MIN(Date)` per `(ticker, isin)`, and the project already keeps denormalized attributes on `transactions`. This is the place to move if identities ever need attributes that are not per-transaction (e.g. a sector restriction list).

2. **Derive the identity change from stored data, never at import time.** Import stays a pure per-file operation, exactly as the `ib-live-api` design established when it made `save_to_database()` the shared sink for CSV and live sources. The change is computed by grouping loaded history on `(Ticker, ISIN)`: more than one distinct non-empty ISIN for a ticker means a change, dated at the earliest transaction of the second identity. **Consequence:** detection works on a database populated by a single statement, and re-running a calculation after importing a newer statement surfaces a change that did not exist at the time of the earlier import. This is deliberate and is why detection belongs in the calculation layer.

3. **Partition FIFO by identity, but keep the ticker as the display and filtering key.** `src/fifo.py` will key its queues on an instrument key derived from the row's ISIN with the ticker as fallback, while every emitted lot, gain, and inventory entry carries both `ticker` (for display, `--ticker` filtering, and the existing `RESTRICTED_TICKERS` check in `main.py`) and `isin`. The user's requirement is transparency: one row per ticker, correct lots underneath, change disclosed. Emitting identity-suffixed tickers such as `OKE (US6826801036)` into `Ticker` itself was rejected because it would leak into Excel/PDF labels, the sanctioned-ticker check, and existing report filenames (`output/tax_report_{year}_{ticker}.xlsx`) for every user, including the vast majority with no ISIN change at all.

4. **Rows with no ISIN fall back to the ticker-only key and are backfilled to the earliest identity.** Legacy rows predate identity capture. At read time a missing ISIN resolves to the ticker's earliest observed ISIN, so they join the original identity's lots and never manufacture a spurious change. This mirrors the existing `SourceKey` migration intent and means an old database is not invalidated by the upgrade. **Trade-off:** a genuinely unknown-identity row for a *later* period is attributed to the earliest identity rather than flagged. Given that IBKR populates the ISIN for essentially all equity rows in the sample data, silently misattributing is judged the lesser risk versus blocking a calculation on a heuristic guess; the boundary remains visible in the report either way.

5. **Detection is non-blocking, but identity-aware insufficiency is an error.** The chosen behavior is auto-separation with a warning, so a detected change produces a diagnostic and the calculation completes. A sale exceeding the lots of *its own* identity is different in kind: that is the existing "sell exceeds available inventory" condition, and it will now name the ISIN. The `CalculationError` path in `main.py` and the codes in `src/diagnostics.py` already exist for this; the change adds the ISIN to the message and a dedicated diagnostic code, it does not introduce a new failure mode.

6. **Reuse `Description` as the primary ISIN source; do not depend on the instrument-information section.** `extract_ticker()` already parses the description form. ISIN extraction will use a sibling regex over the same string, which works for trades, dividends, and withholding tax rows alike because they share the `TICKER(ISIN)` shape. The `Financial Instrument Information` section will additionally be parsed to fill `Conid` and to cover descriptions where the ISIN is absent, but its absence in a given statement MUST NOT block the import. Note that `U5801_20210315_20220107.csv` and several `U1601_*` files use a narrower variant of that section, so header lookup must stay name-based via `get_col_idx` rather than positional, in line with how every other section in `parse_csv()` is handled.

## Risks / Trade-offs

- [Risk] A broker description format not covered by the regex leaves the ISIN empty, silently degrading to today's behavior → mitigate by unit-testing both known description forms against real sample lines, and by reporting a count of identity-resolved vs. unresolved rows in the import result so a regression is visible rather than silent.
- [Risk] A ticker is genuinely reused across unrelated securities, and auto-separation splits what the user considers one position → this is the accepted trade-off of the chosen behavior; the dated diagnostic plus the disclosed boundary are the user's correction path, and the old identity's lots stay intact so nothing is destroyed.
- [Risk] Changing the FIFO queue key perturbs calculation ordering and Decimal arithmetic for existing users → mitigate by keying on the ISIN-derived value only when more than one ISIN exists for that ticker, leaving the single-ISIN path unchanged, and by running the existing test suite unmodified as the gate.
- [Risk] `Description` is currently the only place the ISIN survives, and it is free text that could later be truncated or reworded by the broker → mitigate by persisting the resolved ISIN into its own column at import time, so later description changes cannot retroactively alter a stored identity.
- [Trade-off] `Conid` is captured but not used as a calculation key. It is stored for traceability and to disambiguate the rare case of two identities sharing an ISIN; it is deliberately not the primary key because it is broker-specific and not present in every section.

## Migration Plan

1. Add the columns through the existing guarded `ALTER TABLE` path in `initialize_schema()`, alongside `SourceKey` and `SplitRatio`. Existing encrypted databases open without re-keying; the unique index on `SourceKey` is not touched.
2. Extract the ISIN in the parser and persist it on insert. Re-importing is safe and idempotent: the `SourceKey` hash is computed from the existing record fields and is not changed by this work, so a re-import updates identity in place instead of duplicating rows. Verify this explicitly before relying on it.
3. Backfill: rows with an empty ISIN are resolved at read time to the ticker's earliest observed ISIN. No bulk `UPDATE` is required, which keeps the encrypted database's write surface unchanged.
4. Switch the FIFO queue key to the instrument identity, guarded so the single-identity path is unchanged.
5. Add derived-change detection and the diagnostic to the calculation layer, then the disclosure to the Excel and PDF exporters.
6. **Rollback:** revert the code. The added columns are nullable and ignored by the previous code, so a reverted binary continues to read the same database. No destructive migration is performed at any step, which is what makes a code-only rollback sufficient.

## Open Questions

- Should the user be able to declare that an ISIN change is a *continuation* of the same economic position (for example a pure symbol remap with no economic event), merging the two identities after the fact? The spec deliberately auto-separates and leaves that judgement to the user; an explicit override would be a follow-up capability rather than part of this change.


