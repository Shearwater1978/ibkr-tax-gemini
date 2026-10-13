# Synthetic manual journal

`journal_v1.json` is a version 1 manual journal: the payload both apps write to the backup folder for operations
entered by hand (OpenSpec change `mobile-manual-operations`, Decisions 2 and 6). It is made up: it uses no real
account, trade or person. It was built with Python's `decimal` module (28-digit context, like the apps), not with
either app.

```json
{"kind": "manual-journal", "version": 1, "sequence": 12, "savedAt": "…", "entries": [
  {"entryId": "…", "type": "BUY", "enteredAt": "…", "fields": {…}, "rows": [{…}]}
]}
```

- **`type` and `fields`:** what the user entered. All numbers are decimal strings.

  | `type` | `fields` |
  |---|---|
  | `BUY`, `SELL` | `date`, `ticker`, `currency`, `quantity`, `price`, `fee` |
  | `TRANSFER_IN` | `date`, `ticker`, `currency`, `quantity`, `price` (cost per share) |
  | `TRANSFER_OUT` | `date`, `ticker`, `currency`, `quantity` |
  | `DIVIDEND` | `date`, `ticker`, `currency`, `amount` (gross), `tax` (withheld, 0 or more) |
  | `TAX` | `date`, `ticker`, `currency`, `amount` (signed correction) |
  | `SPLIT` | `date`, `ticker`, `currency`, `newShares`, `oldShares`, `heldBefore` |
  | `STOCK_DIV` | `date`, `ticker`, `currency`, `quantity` |
  | `MERGER` | `date`, `ticker` (old), `currency`, `quantity` (old shares), `newTicker`, `newQuantity` |
  | `SPINOFF` | `date`, `ticker` (new company), `currency`, `quantity`, `fromTicker` |

- **`rows`:** the `transactions` rows stored for the entry, with the same field names as the database columns in
  camelCase. They follow the importer's conventions:
  - Sells and transfers out have a negative quantity, and `amount` is quantity × price.
  - Fees are stored negative, like IBKR commissions.
  - Corporate actions have price, amount and fee `0`.
  - A dividend's tax is a separate negative `TAX` row.
- **Source keys:** `manual:<entryId>`, plus `:tax` for a dividend's tax row and `:new` for the new shares of a
  merger.
- **Splits:** the row has `splitRatio` = newShares ÷ oldShares and a positive quantity, like IBKR's positive split
  row. For a forward split that is the shares added (heldBefore × ratio − heldBefore); for a reverse split it is the
  new share count (heldBefore × ratio).
- **Mergers:** the new shares are a `STOCK_DIV` row. Both rows have the description
  `Manual corporate action <entryId> (<ticker>)`, so FIFO links them and carries cost and purchase dates over.
- **Other descriptions:** `Manual`.
- **Instrument fields:** `isin`, `conid` and `listingExchange` are empty because no imported report knows these
  tickers.
- **Matching an import:** the first entry has the same date, quantity, price and fee as the first AAPL buy in
  `flex-query/valid_basic.csv`, so tests can check that a manual row equals the imported row.

`journal_v1.bin` is the same JSON bytes in the encrypted backup file format, built with pyca/cryptography under the
data key 0x00…0x1f (the `dataKey` of `keys/manifest_v1.json`). The header is magic `IBKB`, version byte 1, a 16-byte
file id and a 12-byte nonce, and the payload is AES-256-GCM with that 33-byte header as associated data. Never put
real operations here.
