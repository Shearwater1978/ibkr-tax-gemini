## ADDED Requirements

### Requirement: Add operations by hand
The app SHALL let the user add an operation by hand with any of these types: buy, sell, transfer in, transfer out,
dividend (with optional withholding tax), withholding-tax correction, split, stock dividend, merger or symbol change,
and spin-off. The instrument SHALL be given by ticker and currency. Adding operations SHALL NOT require a chosen
broker or an imported report.

#### Scenario: First manual buy
- **WHEN** a user with no imported reports adds a buy of 10 AAPL at 150.00 USD with a 1.00 USD fee on 2026-03-02
- **THEN** AAPL appears in the holdings with 10 shares and an invested amount of 1,500.00 USD

#### Scenario: Dividend with withholding tax
- **WHEN** the user adds a dividend of 12.00 USD for AAPL with 1.80 USD withholding tax
- **THEN** Income shows 12.00 gross, 1.80 tax and 10.20 net for that month

#### Scenario: Merger
- **WHEN** the user records a merger in which 10 OLD shares became 5 NEW shares
- **THEN** OLD is no longer held, and NEW is held with 5 shares, carrying OLD's cost and purchase dates

### Requirement: Store manual and imported operations together
Manual operations SHALL be stored in the same operations table as imported ones, marked with their origin. Every
view (holdings, FIFO, returns, dividends, top movers, activity) SHALL include them exactly as it includes imported
operations. A manual operation SHALL be stored with the same type, signs and amounts the importer uses for the same
operation.

#### Scenario: Mixed sources
- **WHEN** a user has an imported buy of 5 AAPL and adds a manual buy of 3 AAPL
- **THEN** the holding shows 8 AAPL shares with both lots in FIFO order

#### Scenario: Same result as an import
- **WHEN** a manual buy is entered with the same date, ticker, quantity, price and fee as an imported fixture buy
- **THEN** both rows have identical type, quantity, price, amount, fee and currency

### Requirement: Identify the instrument from ticker and currency
A manual operation whose ticker and currency match imported operations SHALL use their ISIN and listing exchange.
Otherwise it SHALL be priced as a US listing.

#### Scenario: Known European instrument
- **WHEN** the user adds a buy of SAP in EUR and imported reports already contain SAP in EUR listed on Xetra
- **THEN** the manual lots join the imported SAP holding and its price comes from the Xetra listing

#### Scenario: Unknown instrument
- **WHEN** the user adds a buy of a ticker that no imported report contains
- **THEN** its price is looked up as a US listing, and the holding says when no price is available

### Requirement: Validate manual operations
The app SHALL refuse a manual operation with a missing or invalid field, a date in the future, or a non-positive
quantity or price. It SHALL refuse any add, edit or delete that would leave a sale of more shares than were held at
that date, and it SHALL say how many shares were held.

#### Scenario: Selling more than held
- **WHEN** the user holds 10 AAPL and adds a sale of 12 AAPL on a later date
- **THEN** the save is refused with "You held 10 AAPL on that date"

#### Scenario: Deleting a needed buy
- **WHEN** the user deletes a manual buy that a later sale depends on
- **THEN** the delete is refused and nothing changes

### Requirement: Edit and delete manual operations
The user SHALL be able to edit or delete each manual operation. Delete SHALL ask for confirmation. A dividend and its
withholding tax SHALL be edited and deleted together. Imported operations SHALL NOT offer edit or delete.

#### Scenario: Edit a buy
- **WHEN** the user changes a manual buy's price from 150.00 to 151.00
- **THEN** the invested amount and returns update, and the activity list shows the new price

#### Scenario: Imported row
- **WHEN** the user opens an imported operation
- **THEN** no edit or delete action is offered

### Requirement: Warn about possible duplicates
Two operations SHALL count as possible duplicates when they share date, type, ticker, currency and absolute quantity
(or absolute amount for dividends and tax). After an import, the app SHALL list manual operations that match
imported ones and offer to delete each manual copy. Before saving a manual operation that matches an existing one,
the app SHALL ask whether to save anyway. The app SHALL NOT delete or merge operations without the user's action.

#### Scenario: Report brings an operation already typed in
- **WHEN** the user imports a report containing a buy that matches a manual buy
- **THEN** the import result lists the manual buy as a possible duplicate with "Delete manual copy"

#### Scenario: Typing an operation already imported
- **WHEN** the user saves a manual dividend matching an imported dividend
- **THEN** the app asks "This looks like an operation you already have" with Save anyway and Cancel

### Requirement: Back up manual operations
After every add, edit or delete, the app SHALL write all manual operations as one encrypted journal file to the
backup folder, using the existing encrypted file format and data key. It SHALL verify the new file before removing
the previous journal. When the folder is unavailable, the journal SHALL stay pending and be retried like report
backups. Restoring from a backup folder SHALL restore the newest journal, on Android and on iOS.

#### Scenario: Restore on a new phone
- **WHEN** the user restores on a new phone from a folder holding reports and a journal
- **THEN** both the imported and the manual operations are back

#### Scenario: Cross-platform journal
- **WHEN** the iOS app restores a journal written by the Android app
- **THEN** the same manual operations appear with the same numbers

#### Scenario: Folder unavailable
- **WHEN** the user adds an operation while the backup folder cannot be reached
- **THEN** the operation is saved, the journal is marked pending, and the backup is retried later

### Requirement: Keep manual operations when imports are deleted
"Delete all imported data" SHALL remove imported operations and report backups only. Manual operations and their
journal SHALL be removed only by deleting them individually or by "Erase all data".

#### Scenario: Delete all imports
- **WHEN** a user with imported and manual operations deletes all imported data
- **THEN** only the manual operations remain, and the journal stays in the backup folder
