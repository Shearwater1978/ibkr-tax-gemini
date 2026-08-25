# Design: Prevent Silent Calculation Errors

1. Make NBP lookup return an explicit success/failure result or raise a domain-specific error. Keep the existing cache and T-1 lookup, but remove the silent `1.0` fallback for non-PLN currencies.
2. Add a calculation diagnostics model containing missing-rate events, unmatched sell quantities, unsupported corporate actions, and report-export errors. Thread it through CLI output and API responses.
3. Make FIFO detect residual quantity after inventory consumption. Default behavior should fail the requested calculation; an explicit diagnostic mode may return a report marked incomplete.
4. Ensure PDF and Excel exporters receive the same validated result and cannot be reported as successful when an exporter fails. Preserve existing successful summary fields for GUI compatibility.
5. Add focused tests for NBP outage, unmatched sells, same-day ordering, exporter failure, and API status mapping. Add fixtures that verify a valid calculation remains unchanged.

The change improves observability without altering valid FIFO or NBP results.