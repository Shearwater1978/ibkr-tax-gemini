# Wiki / Glossary

## How to Import Data
We no longer use `ingest.py`. Use the parser module directly:
`python -m src.parser --files "path/to/data/*.csv"`

## Database
The file `data/ibkr_history.db.enc` is encrypted. You cannot open it with standard SQLite browser unless you provide the key (PRAGMA key).

## Excel Report Explanation
* **Sales P&L:** Shows every closed position. If one Sell matched multiple Buys, it creates multiple rows.
* **Dividends:** Shows Gross amount and Tax Paid (converted to PLN).
* **Open Positions:** Your current inventory (FIFO queue).

## Sanctions / Restricted Assets
The PDF report highlights assets like Yandex (YNDX) or Sberbank (SBER) in RED. This serves as a warning for tax filing (blocked assets).

## Developer Notes
To reset the project state for an LLM, use content from `RESTART_PROMPT.md`.