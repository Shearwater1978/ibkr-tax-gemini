# Tasks: Prevent Silent Calculation Errors

- [x] Define calculation diagnostic and completeness models shared by processing, exporters, and CLI.
- [x] Remove non-PLN rate `1.0` fallbacks and add explicit missing-rate handling.
- [x] Detect residual unmatched sell quantities in FIFO and propagate blocking diagnostics.
- [x] Make Excel/PDF export failures observable.
- [x] Add focused tests for NBP outages, unmatched sells, and exporter failures.
- [x] Update user documentation with incomplete-result and recovery behavior.
- [x] Run the full pytest suite plus report smoke tests.