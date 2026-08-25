# Design: Make Imports Safe and Complete

1. Define a stable record identity from normalized source file/row data and use a unique constraint or equivalent upsert strategy. Existing transactions remain untouched when an import contains no valid records.
2. Parse all input files into a validated batch first. Write the batch in one database transaction and roll back the complete batch if validation or persistence fails.
3. Resolve paths relative to the project/data configuration rather than the process working directory, while retaining CLI overrides where available.
4. Extend corporate-action parsing to capture split ratios from supported IBKR descriptions and persist the ratio in a backward-compatible field. `processing.py` must forward the persisted ratio instead of assigning `1`.
5. Add integration tests covering repeated overlapping imports, failed batches, malformed rows, and a split flowing from CSV through the database into FIFO.

The existing `--import-data`, parser `--files`, and `POST /import` entry points remain available and report inserted/skipped counts.