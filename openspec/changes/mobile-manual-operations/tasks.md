Android leads and iOS follows, as with the skins. Each numbered section is one PR of about 1,500 changed lines or
fewer, branched from `main`.

## 1. Shared fixtures
- [x] 1.1 Build `mobile/fixtures/manual/journal_v1.json` (synthetic entries of every type) and `journal_v1.bin` (encrypted with the key fixture's data key) with Python, documented in a README like `fixtures/keys`
- [x] 1.2 Add expected rows for each manual entry, matching the rows the importer writes for the same operations _The rows are in the journal; `ManualJournalFixtureTest` (both platforms) checks the encrypted copy and that the manual buy equals the imported `valid_basic.csv` buy._

## 2. Android: unified storage
- [ ] 2.1 Schema version 7: rebuild `transactions` with `origin`, nullable `report_id`, `entry_id`, `entered_at` and the CHECK constraint, in one transaction
- [ ] 2.2 Migration test: a version 6 database with fixture rows migrates with every row unchanged and `origin = 'report'`
- [ ] 2.3 Repository for manual entries: insert, replace and delete an entry's rows; list entries; imported rows stay read-only
- [ ] 2.4 Keep "Delete all imported data" and per-import deletion from touching manual rows; "Erase all data" removes them

## 3. Android: manual operations
- [ ] 3.1 Entry model and row builder for every type (Decision 2), tested field by field against the fixture rows
- [ ] 3.2 Instrument lookup that reuses ISIN, conid and listing exchange from imported rows (Decision 3)
- [ ] 3.3 Validation, including the FIFO oversell check on add, edit and delete (Decision 4)
- [ ] 3.4 Duplicate matching for imports and manual saves (Decision 5)

## 4. Android: backup and restore
- [ ] 4.1 Journal payload and writer: write and verify the new journal, then delete the old one; pending state and retry
- [ ] 4.2 Restore: recognise journals by content, restore the newest after the reports
- [ ] 4.3 Decrypt and restore `journal_v1.bin` in tests

## 5. Android: screens
- [ ] 5.1 Shared "Add operation" form with a type picker and per-type fields, plus edit and delete with confirmation
- [ ] 5.2 Activity rows: "Manual" label and tap to edit in all skins; empty states offer "Add operation"
- [ ] 5.3 Import result: "Possible duplicates" with "Delete manual copy"
- [ ] 5.4 `Feature.MANUAL_ENTRY` placed in Classic, Calm, Bento and Focus (Decision 8); `SkinCatalogTest` passes
- [ ] 5.5 Update the user guide, the privacy notice and `mobile/README.md`

## 6. iOS: unified storage
- [ ] 6.1 Same schema version 7 migration and migration test
- [ ] 6.2 Manual-entry repository and deletion rules

## 7. iOS: manual operations
- [ ] 7.1 Row builder, instrument lookup, validation and duplicate matching, with the shared fixture tests

## 8. iOS: backup and restore
- [ ] 8.1 Journal writer and restore; decrypts the Android-built `journal_v1.bin`

## 9. iOS: screens
- [ ] 9.1 Form, activity labels, import duplicates, and placement in all four skins; `SkinCatalogTests` passes

## 10. Verification
- [ ] 10.1 Back up manual entries on Android, restore on iOS, and the other way round
- [ ] 10.2 Sync the delta specs into `openspec/specs` and archive the change
