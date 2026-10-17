# Parity fixtures

Synthetic fixtures that pin the behavior of the Python reference, which the mobile apps reproduce:

- `flex-query/`: IBKR Activity Statement CSVs, and in `expected/` the normalized records (`<name>.json`) and deduplication keys (`source_keys/<name>.json`) that `src/parser.py` produces for each valid one;
- `fifo/`: FIFO scenarios (`scenarios.json`) and the open lots, or the unmatched-sell diagnostic, that the desktop FIFO pipeline produces for each (`expected_open_lots.json`).

Everything here is made up (account `U00000001`, "Synthetic Test User"). Never add real reports or identifiers. Files are byte-exact (`.gitattributes`: `tests/fixtures/parity/** -text`); never renormalize line endings or reformat them.

## Ownership

This repository owns these fixtures and their generation. The mobile apps live in the private repository [broker-companion](https://github.com/Shearwater1978/broker-companion), which keeps byte-identical **copies** in its `fixtures/flex-query` and `fixtures/fifo`. Change fixtures here first, then copy the two folders there unchanged. Generating or testing them does not need the mobile repository.

`flex-query/README.md` is part of the copied set and stays byte-identical in both repositories; this file describes the core side.

## Tests

`tests/test_mobile_fixtures.py` checks that the parser records, source keys and FIFO lots match the committed expectations, that the generator reproduces them, and that the CSVs use only synthetic account identifiers:

```sh
pytest tests/test_mobile_fixtures.py
```

## Regenerating

After an intentional change to a fixture or to the reference (`src/parity_reference.py`, which wraps `src/parser.py` and the FIFO pipeline):

```sh
python tools/generate_parity_fixtures.py --check   # report expectation files that differ; exit 1 if any
python tools/generate_parity_fixtures.py           # rewrite the differing expectation files
```

`--fixtures <dir>` points it at another fixture root, for example a temporary copy holding only the input CSVs and `fifo/scenarios.json`, to inspect fresh output without touching this folder. Review every change: expectations change only with an intentional fixture or reference change, never to make a mobile test pass. NBP rates are stubbed and no network is used.

## Comparing with the mobile copies

broker-companion's `tools/check_parity_fixtures.py` compares the two folders by relative path and SHA-256, read-only and offline, and exits 1 on any missing, extra or changed file:

```sh
python <broker-companion>/tools/check_parity_fixtures.py --core-fixtures tests/fixtures/parity --mobile-fixtures <broker-companion>/fixtures
```
