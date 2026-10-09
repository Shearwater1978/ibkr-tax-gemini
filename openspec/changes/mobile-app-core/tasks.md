## 1. Report upload

- [ ] 1.1 Add file picker and import UI with per-import status
- [ ] 1.2 Port or share report parsing and normalization for supported formats
- [ ] 1.3 Detect and skip duplicate imports
- [ ] 1.4 Show clear errors for unsupported or corrupted files

## 2. Anonymization and secure storage

- [ ] 2.1 Pseudonymize identifiers before storing, logging or displaying data (using the `data-anonymization` mechanism)
- [ ] 2.2 Hand the original report to the encrypted Drive backup; keep a pending state and retry on failure
- [ ] 2.3 Verify no raw identifiers appear in logs or errors

## 3. Portfolio overview

- [ ] 3.1 Aggregate holdings per instrument across reports and accounts
- [ ] 3.2 Build the holdings list, totals and unrealized gain/loss
- [ ] 3.3 Mask account references and add the empty state

## 4. Market prices

- [ ] 4.1 Decide the market data provider and base currency (open questions)
- [ ] 4.2 Implement a provider interface that sends only ticker symbols
- [ ] 4.3 Cache last prices with timestamp and currency; mark stale prices
- [ ] 4.4 Handle offline and provider errors; support pull-to-refresh

## 5. Tests and docs

- [ ] 5.1 Unit tests for aggregation, duplicate detection and pseudonymization
- [ ] 5.2 Tests for price staleness and failure handling
- [ ] 5.3 Update README / specs references
