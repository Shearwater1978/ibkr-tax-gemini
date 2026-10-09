# Synthetic IBKR Flex Query fixtures

Shared by the Android and iOS test suites. Every file is synthetic: the only account is `U00000001` and the only name is `Synthetic Test User`. Never add real broker reports or identifiers here.

| Fixture | Expected import outcome |
|---|---|
| `valid_basic.csv` | Accepted. Buys and sells (USD, EUR), a fully sold position (MSFT), a 2-for-1 split (SAP), a dividend with withholding tax, and Forex, SubTotal and Total rows that must be skipped. |
| `valid_followup.csv` | Accepted. A later report for the same account, used for multi-report aggregation and duplicate detection. |
| `unsupported_format.csv` | Rejected as an unsupported format; no records stored. |
| `malformed_trades.csv` | Rejected as unparseable (bad date, bad quantity, truncated rows); no partial records stored. |
| `empty.csv` | Rejected; no records stored. |

`expected/<name>.json` holds the normalized records the Python reference parser (`src/parser.py`) produces for each valid fixture; decimals are strings. `tests/test_mobile_fixtures.py` keeps them in sync, and mobile parsers must produce the same records.

To regenerate after an intentional fixture change, run `parse_csv` on the fixture and write the result with decimals as strings. Review the diff: never edit expectations just to make a mobile test pass.
