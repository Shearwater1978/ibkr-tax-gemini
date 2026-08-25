# Tasks: Prevent Silent Calculation Errors

- [ ] Define calculation diagnostic and completeness models shared by processing, exporters, CLI, and API.
- [ ] Remove non-PLN rate `1.0` fallbacks and add explicit missing-rate handling.
- [ ] Detect residual unmatched sell quantities in FIFO and propagate blocking diagnostics.
- [ ] Make Excel/PDF export failures observable and preserve intentional API status codes.
- [ ] Add focused tests for NBP outages, unmatched sells, exporter failures, and API 404 behavior.
- [ ] Update user documentation with incomplete-result and recovery behavior.
- [ ] Run the full pytest suite plus API and report smoke tests.