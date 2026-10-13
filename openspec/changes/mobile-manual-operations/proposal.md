# Change: Manual operations in one unified database (mobile)

## Why
Some users have small accounts, or a broker whose reports the app cannot read. For them, typing a handful of
operations is easier than exporting and importing broker reports. Today every stored operation must come from an
imported report, so these users cannot use the app at all.

## What Changes
- Users can add, edit and delete operations by hand on Android and iOS. The types are the ones reports produce:
  buy, sell, transfer in and out, dividend with withholding tax, withholding-tax correction, split, stock dividend,
  merger or symbol change, and spin-off.
- Manual operations and imported operations live in the same `transactions` table. A new `origin` column marks each
  row as `report` or `manual`, and manual rows have no report. Holdings, FIFO, returns, dividends, top movers and
  activity read the same table as before, so they include manual operations without separate code paths.
- Report import stays as it is, for Interactive Brokers only. Users can mix both sources. When an import brings an
  operation that looks like a manual one, or a manual one looks like an imported one, the app warns and offers to
  delete the manual copy. It never deletes on its own.
- A manual operation names its instrument by ticker and currency only. If imported reports already know that ticker
  and currency, it reuses their ISIN and listing exchange. Otherwise it is priced as a US listing.
- Manual operations get their own encrypted backup: one journal file in the backup folder, written in the existing
  encrypted file format and rewritten after each change. Restore brings it back on a new phone, on either platform.
- A save that would sell more shares than are held at that date is refused, with the quantity held.
- Adding operations by hand does not require a broker choice.
- Manual entry becomes a feature that every skin must offer.

## Non-goals
- The desktop tool (Python, PIT-38) is unchanged. It already has `manual_history.csv`, and its tax logic needs its
  own OpenSpec change.
- No ISIN or exchange picker; possible later.
- No import of other brokers' reports, and no CSV import of manual operations.
- Imported operations stay read-only.

## Impact
- Specs:
  - New capability: `mobile-manual-operations`.
  - Modified: `mobile-operations` (manual rows in the list), `mobile-skins` (parity list),
    `mobile-report-upload` (deleting imports keeps manual operations), `mobile-broker-choice` (no broker needed).
- Database: schema version 7 on both platforms. The `transactions` table is rebuilt to add `origin` and make
  `report_id` nullable. Existing rows become `origin = 'report'`.
- Backup: a new encrypted payload kind, the manual journal. Report backups and the key manifest are unchanged.
- Shared fixtures: a synthetic manual journal in `mobile/fixtures/manual/` is read by both apps' tests.
- Privacy notice: manual operations are stored and backed up like imported ones. Nothing new leaves the phone
  except encrypted backups.
