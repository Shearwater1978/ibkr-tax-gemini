# Design: Manual operations in one unified database

## Context
Both apps store operations in a SQLCipher database (schema version 6). `transactions` is the single source of truth
for FIFO, holdings, returns, dividends and activity. Each row has a unique `source_key` and a non-null `report_id`.
`report_records` lists which reports contain which `source_key`, so deleting one import keeps the rows that other
reports also contain. Backups are encrypted copies of the report files (`IBKB` format), and a restore re-imports them.

## Decision 1: One table, with an origin column
`transactions` stays the only operations table. Schema version 7 adds:

| Column | Report rows | Manual rows |
|---|---|---|
| `origin` | `'report'` | `'manual'` |
| `report_id` | the report (NOT NULL) | NULL |
| `source_key` | as today | `manual:<uuid>`, plus `:tax` for a dividend's withholding tax |
| `entry_id` | NULL | the manual entry the row belongs to (`<uuid>`) |

- A new `manual_entries` table holds one row per entry: `entry_id`, the form `type`, the form `fields` (JSON of
  decimal strings) and `entered_at` (UTC, ISO). Edit forms and the backup journal read it. `transactions.entry_id`
  references it with `ON DELETE CASCADE`, so deleting an entry deletes its rows.
- A CHECK constraint keeps `origin`, `report_id` and `entry_id` consistent.
- SQLite cannot make a column nullable in place. The migration therefore creates `transactions_v7`, copies every row
  with `origin = 'report'`, drops the old table, renames the new one and recreates the index. All of this runs in one
  database transaction, and a failure leaves version 6 intact.
- `report_records` and `reports` are unchanged. Manual rows never appear in `report_records`.
- Alternative rejected: a separate `manual_operations` table. Every reader (FIFO, dividends, activity) would need a
  union, and keeping them in step is where bugs come from.

## Decision 2: Manual rows look exactly like imported rows
For each operation type, a manual row has the same `event_type`, quantity sign, price, amount, fee sign and
`split_ratio` that the importer writes for the same operation. One function per platform builds rows from the form,
and tests compare it field by field with imported fixture rows. FIFO, holdings and dividends therefore need no
changes. That matters because the FIFO and dividend rules are covered by tests and must not change silently.

| Form | Rows written |
|---|---|
| Buy, sell | one `BUY` or `SELL` row (quantity, price per share, fee) |
| Transfer in, out | one `TRANSFER` row (positive or negative quantity; cost per share for transfer in) |
| Dividend | one `DIVIDEND` row (gross) and, if tax > 0, one `TAX` row (negative) with the same date, ticker and currency |
| Withholding-tax correction | one `TAX` row (positive or negative) |
| Split | one `SPLIT` row with `split_ratio` = new ÷ old and a positive quantity, like IBKR's positive split row: the shares added for a forward split, the new share count for a reverse split |
| Stock dividend | one `STOCK_DIV` row (shares received) |
| Merger or symbol change | a negative `MERGER` row for the old ticker and a positive `STOCK_DIV` row for the new ticker, as IBKR books them |
| Spin-off | one `SPINOFF` row (shares of the new company received) |

- FIFO links the two rows of a merger through their date and the description stem before the trailing parentheses.
  Manual mergers write the descriptions `Manual corporate action <uuid> (<old ticker>)` and `(<new ticker>)`, so
  existing matching carries cost and purchase dates to the new shares.
- A dividend and its tax share one `entry_id`, so they are edited and deleted together. This keeps withholding tax
  linked to its dividend.

## Decision 3: Instrument identity from ticker and currency
- The user enters a ticker and a currency.
- If imported rows already have that ticker and currency, the manual row copies their `isin`, `conid` and
  `listing_exchange`, so prices and FIFO grouping match the imported instrument.
- Otherwise `isin` and `listing_exchange` are empty, and prices are fetched as for a US listing (Finnhub with a key,
  else Yahoo).
- FIFO only separates instruments by ISIN when one ticker has several ISINs. Empty ISINs never trigger that split,
  so manual and imported rows of one ticker share their lots.

## Decision 4: Validation
- **Required fields:** a date that is not in the future, an upper-case ticker (letters, digits, dot), a three-letter
  currency, and positive quantities and prices. Fees and tax are 0 or more. A split needs two positive whole share
  counts.
- **No overselling:** before saving, the app runs FIFO on all rows with the change applied. If the result is an
  unmatched sell, the save is refused with the quantity held on that date. The same check applies when editing or
  deleting an entry, for example deleting a buy that a later sell needs.
- **Exact numbers:** amounts are parsed as exact decimals in the user's locale and stored as plain decimal text,
  like imported values.

## Decision 5: Possible duplicates, warned about but never removed automatically
Two rows match when they have the same date, `event_type`, ticker, currency and absolute quantity. For dividends and
tax, the absolute amount takes the place of quantity.
- **After an import:** the result lists every manual entry that now matches an imported row, with "Delete manual
  copy" next to each.
- **Before a manual save:** if it matches an existing row, the app asks "This looks like an operation you already
  have" and offers Save anyway or Cancel.
- Matching is done in app code over the matching ticker's rows. The source keys stay different, so the database
  never merges rows.

## Decision 6: Encrypted manual journal in the backup folder
- **Payload:** UTF-8 JSON
  `{"kind":"manual-journal","version":1,"sequence":N,"savedAt":"…","entries":[…]}`. Each entry holds its
  `entry_id`, its form type and form fields, and the rows built from them, so a newer app can rebuild the rows.
- **Encryption:** the existing `IBKB` v1 format under the data key, with a fresh random neutral file name.
- **Writing:** after every add, edit or delete, the app writes a new journal with `sequence + 1` and verifies it by
  reading it back. Only then does it delete the previous journal file. If the folder is unreachable, the journal is
  marked pending and the existing retry backoff applies.
- **Restore:** the app decrypts every file. CSV payloads are reports, as today. JSON payloads with
  `kind = manual-journal` are journals, and only the one with the highest `sequence` is restored. Its rows are
  inserted after the reports, so duplicate warnings work as after an import.
- **Deleting:** "Delete all imported data" keeps the journal. "Erase all data" deletes it with everything else.
- **Shared fixture:** `mobile/fixtures/manual/journal_v1.json` (plaintext) and `journal_v1.bin` (encrypted with the
  key fixture's data key). Both are built with Python like the key fixtures, and both apps' tests read them.

## Decision 7: Deleting and editing
- Manual entries can be edited and deleted one at a time, after a confirmation for delete. Imported rows show no
  edit or delete actions.
- "Delete all imported data" removes report rows and report backups only. Manual entries stay, because the user
  typed them and there is no file to re-import them from.
- There is no "delete all manual entries" action in this change. "Erase all data" covers that.

## Decision 8: Where it appears in each skin
A new `Feature.MANUAL_ENTRY` joins the parity list, so `SkinCatalogTest` fails until every skin places it.

| Skin | Place |
|---|---|
| Classic | Operations tab: "Add operation" |
| Calm | Activity tab: "Add operation" |
| Bento | Activity tab: "Add operation" |
| Focus | "+" menu: "Add operation" |

In every skin, a manual row in the activity list carries a "Manual" label, and tapping it opens edit.

## Risks and trade-offs
- **Desktop divergence:** desktop `manual_history.csv` is a different format and is not read by the phone apps.
  Users who use both must type entries twice. This is acceptable for now and recorded as a non-goal.
- **Duplicate matching is heuristic:** a real second purchase on the same day with the same quantity triggers a
  warning. The user can keep both rows, so no data is lost.
- **The rebuild migration rewrites the largest table.** It runs once per install inside a transaction. A test
  migrates a version 6 database with fixture rows and compares every row before and after.
- **Whole-journal rewrite:** each change writes the whole journal. For hundreds of entries it stays small (tens of
  KB), and it keeps restore simple.

## Migration plan
1. Schema version 7 migration on Android, then on iOS, with the same SQL on both.
2. Existing installs keep all rows (`origin = 'report'`). Backups made before version 7 restore unchanged.
3. Rollback: downgrades are not supported, as with every earlier version bump. Reinstalling an older version
   means erasing and restoring from backups.
