## Purpose

Makes the count of imported reports lead to the reports it counts.

## ADDED Requirements

### Requirement: Open the report list from the report count
Where a skin shows how many reports are imported, tapping that count SHALL open the list of imported reports, newest first, with each report's details and the same delete action and confirmation as on the import screen.

#### Scenario: Tap the count
- **WHEN** the user taps "Reports imported 3" on Bento's Data screen
- **THEN** the three imported reports are listed, each with a delete action
