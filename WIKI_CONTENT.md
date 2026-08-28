# IBKR Tax Calculator (PIT-38 Poland)

**Current release: v2.2.0**

Local Python and Electron application for importing Interactive Brokers
Activity Statements or Flex Query reports, calculating FIFO capital gains and
dividends for Polish tax residents, and producing Excel/PDF reports.

This file is the source draft for the project GitHub Wiki. After review and
merge, copy its content to the Wiki manually.

## Features

- Local SQLCipher database with AES-256 encryption.
- Parser for IBKR Activity Statements and Flex Query CSV reports.
- Idempotent imports with duplicate detection and import summaries.
- FIFO matching for buys, sells, transfers, splits, and supported corporate actions.
- Official NBP exchange rates using the previous-working-day rule.
- Excel and PDF report generation.
- FIFO coverage preflight for checking planned sales before a real sale is recorded.
- Electron desktop dashboard backed by a local FastAPI service.

## Installation

Requirements: Python 3.10+, a working SQLCipher driver, and Node.js for the
desktop GUI.

```bash
git clone https://github.com/Shearwater1978/ibkr-tax-gemini.git
cd ibkr-tax-gemini
pip install -r requirements.txt
```

Create a `.env` file in the project root:

```ini
SQLCIPHER_KEY=your_secret_key
DATABASE_PATH=db/ibkr_history.db.enc
```

The key must be non-empty. The application refuses to use a plaintext SQLite
database in place of the configured encrypted database.

## Project Structure

```text
project-root/
|- main.py                         CLI entry point
|- WIKI_CONTENT.md                 Wiki source draft
|- requirements.txt                Python dependencies
|- data/                           Imported broker CSV files and fixtures
|- db/                             Encrypted local database files
|- output/                         Generated Excel/PDF reports
|- src/
|  |- parser.py                    CSV parsing and normalization
|  |- db_connector.py              SQLCipher database access
|  |- processing.py                Tax and event processing
|  |- fifo.py                      FIFO inventory and realized gains
|  |- fifo_coverage.py             Non-destructive coverage preflight
|  |- nbp.py                       NBP exchange-rate lookup and caching
|  |- excel_exporter.py            Excel output
|  `- report_pdf.py                PDF output
|- gui/
|  |- backend/api.py               FastAPI adapter
|  `- ui/index.html                Electron dashboard
`- tests/                          Pytest suite
```

## Import Data

Place IBKR CSV reports in `data/`, then import them from the project root:

```bash
python main.py --import-data
```

The import scans `data/*.csv`, normalizes supported records, ignores duplicate
records, and stores transactions in the encrypted database. Keep all historical
reports: FIFO cost basis may require purchases from earlier years.

## Calculate Reports

Run a tax calculation for a year:

```bash
python main.py --target-year 2024 --export-pdf --export-excel
```

The calculation reads imported transactions through the requested year,
applies FIFO matching and NBP conversion, and writes reports under `output/`.
If an NBP rate is unavailable or a sale cannot be matched, the calculation
returns a diagnostic rather than presenting incomplete tax totals as valid.

## FIFO Coverage Preflight

Coverage is evidence for planning a possible sale. It does **not** calculate
tax, calculate profit, create a simulated sale, or persist a `SELL` transaction.

### CLI

Check one asset using a repeated structured argument:

```bash
python main.py --planned-sale AAPL:10:2024-12-31
```

Check multiple assets and request machine-readable JSON:

```bash
python main.py \
  --planned-sale AAPL:10:2024-12-31 \
  --planned-sale MSFT:5:2024-12-31 \
  --coverage-output json
```

Alternatively, provide a JSON file containing an array of objects:

```json
[
  {"ticker": "AAPL", "quantity": 10, "as_of": "2024-12-31"},
  {"ticker": "MSFT", "quantity": 5, "as_of": "2024-12-31"}
]
```

```bash
python main.py --coverage-file planned-sales.json --coverage-output json
```

Each result contains the ticker, requested quantity, available quantity,
missing quantity, as-of date, status, history indicator, and FIFO lot evidence.
Statuses are:

- `COVERED`: available quantity is at least the requested quantity.
- `PARTIAL`: some quantity is available, but the request is not fully covered.
- `NOT_COVERED`: no quantity is available.

`history_found: false` distinguishes missing broker history from imported
history that simply has no remaining holdings. Lot evidence is listed oldest
first and includes acquisition date, quantity contribution, and source identity
when available.

### API

The local backend exposes `POST /coverage`:

```json
{
  "planned_sales": [
    {"ticker": "AAPL", "quantity": 10, "as_of": "2024-12-31"}
  ]
}
```

The response contains an overall status and one structured result per ticker.
The endpoint only reads imported history and does not modify calculation data.

### Desktop GUI

The dashboard includes a FIFO Coverage Preflight section. Enter the same JSON
array of planned sales, run the check, and review requested, available, missing,
as-of, status, and FIFO lot columns. Empty-history and incomplete-result states
are shown explicitly.

## Calculation Logic

### FIFO

The engine consumes the oldest available acquisition lots first. Splits adjust
lot quantity and unit price while preserving total cost. Transfers and
supported corporate actions adjust inventory without treating them as ordinary
taxable sales.

### NBP Rates

For non-PLN events, the application uses the official NBP rate from the last
working day before the event and caches rate lookups where possible.

### Dividends and Withholding Tax

Dividend and withholding-tax rows are linked by date and ticker. Reports show
gross dividend amounts and tax withheld in PLN when the required data is present.

## Reports

### Excel

The Excel workbook includes summary metrics, realized sales and FIFO lot
matches, dividends, and open inventory lots.

### PDF

The PDF contains a cover, portfolio holdings, filtered trade history, dividend
summary, and PIT-38 helper figures. It is an analysis and preparation aid, not
a guarantee that a tax filing is legally correct.

## Desktop GUI

Install and start the Electron dashboard:

```bash
cd gui
npm install
npm start
```

The Electron shell starts the local backend at `127.0.0.1:8000`, waits for the
health endpoint, and uses the same encrypted database and report files as the
CLI.

## Security and Data Handling

- Financial history is stored in the configured SQLCipher database.
- Keep `.env`, `SQLCIPHER_KEY`, and database backups private.
- Do not commit real broker reports, keys, or unredacted financial output.
- Losing the encryption key makes the database unreadable; re-importing files
  requires the original reports.

## Troubleshooting

- **SQLCipher connection failed:** verify the SQLCipher dependency, `DATABASE_PATH`,
  and a non-empty `SQLCIPHER_KEY`.
- **No data found:** place reports in `data/` and run `python main.py --import-data`.
- **Unmatched sale:** import the complete acquisition history, not only the
  current tax year; use FIFO coverage preflight before relying on a planned sale.
- **Missing NBP rate:** verify network access to the NBP API and retry the
  calculation.
- **PDF generator unavailable:** install the dependencies from `requirements.txt`.
- **GUI backend unavailable:** start the GUI from the repository's `gui/`
  directory and check that port `8000` is free.

## Disclaimer

Educational purpose only. This project is not financial, legal, or tax advice.
Review imported data, calculations, and generated reports with a qualified tax
professional before filing.
