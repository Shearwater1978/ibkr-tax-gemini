## 1. Workspace page

- [x] 1.1 Merge the Tax Calculation, Data, and Coverage pages into one `page-workspace` with the three sections in that order, keeping all element ids and handlers; verify in the browser that all sections render
- [x] 1.2 Replace the three tabs with one Workspace tab and point `calculate()` and every "Go to Tax Calculation" button to the Workspace page; verify navigation and that no references to the removed page ids remain

## 2. Behaviour checks

- [x] 2.1 Verify with mocked responses that calculate success lands on the Dashboard and failure keeps the error visible on Workspace with previous results intact
- [x] 2.2 Verify import, IB status, and coverage controls still report results from the Workspace page

## 3. Verification

- [x] 3.1 Verify against the real backend that years load and a calculation and coverage check work from the Workspace page
- [x] 3.2 Run `black --check .`, `openspec validate workspace-page --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
