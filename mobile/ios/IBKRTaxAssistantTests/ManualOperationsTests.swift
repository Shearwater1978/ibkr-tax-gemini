import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

private func fixtureRow(_ json: [String: Any]) -> StoredTransaction {
    StoredTransaction(
        sourceKey: json["sourceKey"] as! String, date: json["date"] as! String, eventType: json["eventType"] as! String, ticker: json["ticker"] as! String,
        quantity: json["quantity"] as! String, price: json["price"] as! String, currency: json["currency"] as! String, amount: json["amount"] as! String,
        fee: json["fee"] as! String, description: json["description"] as! String, splitRatio: json["splitRatio"] as? String, isin: json["isin"] as! String,
        conid: json["conid"] as! String, instrumentDescription: json["instrumentDescription"] as! String, listingExchange: json["listingExchange"] as! String
    )
}

private func trade(_ type: ManualType, _ date: String, _ quantity: String, ticker: String = "AAPL", price: String = "100.00") -> [String: String] {
    ["date": date, "ticker": ticker, "currency": "USD", "quantity": quantity, "price": price, "fee": "1.00"]
}

/// Row building, validation and FIFO checks (mobile-manual-operations tasks 7.1); mirrors the Android `ManualOperationsTest`.
final class ManualRowsTests: XCTestCase {
    private let entries = (Fixtures.json("manual/journal_v1.json") as! [String: Any])["entries"] as! [[String: Any]]
    private let today = CalendarDay(year: 2026, month: 10, day: 13)

    private func rows(_ entry: [String: Any]) -> [StoredTransaction] { (entry["rows"] as! [[String: Any]]).map(fixtureRow) }

    private func build(_ type: ManualType, _ fields: [String: String], id: String = "e") -> [StoredTransaction] {
        ManualRows.build(entryId: id, type: type, fields: fields)
    }

    func testEveryFixtureEntryBuildsTheRowsOfTheJournal() {
        for entry in entries {
            let type = ManualType(rawValue: entry["type"] as! String)!
            let fields = entry["fields"] as! [String: String]
            XCTAssertEqual(ManualValidation.check(type, fields, today: today), [], type.rawValue)
            XCTAssertEqual(ManualRows.build(entryId: entry["entryId"] as! String, type: type, fields: fields), rows(entry), type.rawValue)
        }
    }

    func testTheWholeJournalGivesTheExpectedLots() {
        let all = FifoInventory.stableSorted(entries.flatMap(rows)) { $0.date < $1.date }
        guard case .success(let lots, _) = FifoInventory.openLots(ManualFifo.events(all)) else { return XCTFail("unmatched") }
        let byTicker = Dictionary(grouping: lots, by: \.ticker)
        func quantity(_ ticker: String) -> ExactDecimal { (byTicker[ticker] ?? []).reduce(ExactDecimal.zero) { $0.adding($1.quantity) } }

        XCTAssertEqual(quantity("AAPL").compare(d("6")), 0)
        XCTAssertEqual(quantity("MSFT").compare(d("3")), 0)
        XCTAssertEqual(quantity("SAP").compare(d("8")), 0)
        XCTAssertEqual(byTicker["SAP"]?.first?.price.compare(d("60")), 0)
        XCTAssertEqual(quantity("GE").compare(d("10")), 0)
        XCTAssertEqual(byTicker["GE"]?.first?.price.compare(d("100")), 0)
        XCTAssertEqual(byTicker["KO"]?.first?.price.signum, 0)
        XCTAssertEqual(byTicker["OGN"]?.first?.price.signum, 0)
        XCTAssertNil(byTicker["OLDCO"])
        // The merger carries cost and the purchase date over to the new shares.
        let newco = byTicker["NEWCO"]?.first
        XCTAssertEqual(newco?.quantity.compare(d("5")), 0)
        XCTAssertEqual(newco?.price.compare(d("100")), 0)
        XCTAssertEqual(newco?.date, "2021-05-10")
    }

    func testKnownInstrumentsLendTheirIdentity() {
        let imported = StoredTransaction(sourceKey: "k", date: "2023-01-10", eventType: "BUY", ticker: "SAP", quantity: "1", price: "1", currency: "EUR", amount: "1",
                                         fee: "0", description: "IBKR Trade", splitRatio: nil, isin: "DE0007164600", conid: "14204", instrumentDescription: "SAP SE",
                                         listingExchange: "IBIS")
        let lookup = ManualInstruments.lookup([imported])
        let fields = ["date": "2024-01-02", "ticker": "SAP", "currency": "EUR", "quantity": "2", "price": "150", "fee": "1"]

        let known = ManualRows.build(entryId: "e", type: .buy, fields: fields, instrument: lookup)[0]
        XCTAssertEqual([known.isin, known.conid, known.instrumentDescription, known.listingExchange], ["DE0007164600", "14204", "SAP SE", "IBIS"])
        var usd = fields
        usd["currency"] = "USD"
        let unknown = ManualRows.build(entryId: "e", type: .buy, fields: usd, instrument: lookup)[0]
        XCTAssertEqual(unknown.isin, "")
        XCTAssertEqual(unknown.listingExchange, "")
    }

    func testInvalidEntriesAreRefusedFieldByField() {
        let bad = ["date": "2026-10-14", "ticker": "aapl", "currency": "US", "quantity": "0", "price": "1,5", "fee": "-1"]
        XCTAssertEqual(ManualValidation.check(.buy, bad, today: today),
                       [.futureDate, .invalid("ticker"), .invalid("currency"), .outOfRange("quantity"), .invalid("price"), .outOfRange("fee")])
        XCTAssertEqual(ManualValidation.check(.dividend, ["date": "2024-01-02", "ticker": "KO", "currency": "USD", "amount": "1"], today: today), [.missing("tax")])
        XCTAssertEqual(ManualValidation.check(.dividend, ["date": "2024-01-02", "ticker": "KO", "currency": "USD", "amount": "1.00", "tax": "1.50"], today: today),
                       [.outOfRange("tax")])
        XCTAssertEqual(ManualValidation.check(.split, ["date": "2024-01-02", "ticker": "KO", "currency": "USD", "newShares": "1.5", "oldShares": "1", "heldBefore": "4"],
                                              today: today), [.outOfRange("newShares")])
        XCTAssertEqual(ManualValidation.check(.merger, ["date": "2024-01-02", "ticker": "KO", "currency": "USD", "quantity": "1", "newTicker": "KO", "newQuantity": "1"],
                                              today: today), [.invalid("newTicker")])
        XCTAssertEqual(ManualValidation.check(.buy, trade(.buy, "2024-02-30", "1"), today: today), [.invalid("date")])
    }

    func testSellingMoreThanHeldIsRefusedWithTheHeldQuantity() {
        let before = build(.buy, trade(.buy, "2024-01-02", "10"), id: "b")
        let after = before + build(.sell, trade(.sell, "2024-03-01", "12"), id: "s")
        XCTAssertEqual(ManualFifo.oversell(before: ManualFifo.events(before), after: ManualFifo.events(after)), Oversell(ticker: "AAPL", date: "2024-03-01", held: d("10")))
        XCTAssertNil(ManualFifo.oversell(before: ManualFifo.events(before), after: ManualFifo.events(before + build(.sell, trade(.sell, "2024-03-01", "10"), id: "s"))))
    }

    func testDataThatWasIncompleteBeforeDoesNotBlockUnrelatedEntries() {
        let broken = build(.sell, trade(.sell, "2024-03-01", "1"), id: "s")
        XCTAssertNil(ManualFifo.oversell(before: ManualFifo.events(broken), after: ManualFifo.events(broken + build(.buy, trade(.buy, "2024-04-01", "3", ticker: "KO"), id: "k"))))
    }

    func testPossibleDuplicatesMatchOnDateTypeTickerCurrencyAndSize() {
        let manual = build(.buy, trade(.buy, "2023-01-10", "10"))[0]
        var imported = manual
        (imported.sourceKey, imported.quantity, imported.description) = ("hash", "10.0", "IBKR Trade")
        XCTAssertTrue(ManualDuplicates.matches(manual, imported))
        imported.quantity = "11"
        XCTAssertFalse(ManualDuplicates.matches(manual, imported))

        let dividend = build(.dividend, ["date": "2023-05-18", "ticker": "AAPL", "currency": "USD", "amount": "2.40", "tax": "0"])[0]
        var other = dividend
        (other.sourceKey, other.amount) = ("x", "2.4")
        XCTAssertEqual(ManualDuplicates.find([dividend], in: [other]).map(\.1), [other])
    }
}

/// The save and delete flow over an in-memory store; mirrors the Android `ManualOperationsServiceTest`.
final class ManualOperationsServiceTests: XCTestCase {
    private final class MemoryStore: ManualStore {
        var imported: [StoredTransaction]
        var entries: [String: ManualEntry] = [:]
        var order: [String] = []

        init(imported: [StoredTransaction] = []) { self.imported = imported }

        func allRows() throws -> [StoredRow] {
            let rows = imported.map { StoredRow(entryId: nil, row: $0) } + order.compactMap { entries[$0] }.flatMap { e in e.rows.map { StoredRow(entryId: e.entryId, row: $0) } }
            return FifoInventory.stableSorted(rows) { $0.row.date < $1.row.date }
        }

        func save(_ entry: ManualEntry) throws {
            if entries[entry.entryId] == nil { order.append(entry.entryId) }
            entries[entry.entryId] = entry
        }

        func delete(_ entryId: String) throws -> Bool {
            order.removeAll { $0 == entryId }
            return entries.removeValue(forKey: entryId) != nil
        }
    }

    private var ids = 0
    private var changes = 0

    private func service(_ store: ManualStore) -> ManualOperations {
        ManualOperations(store: store, now: { Date(timeIntervalSince1970: 1_791_878_400) }, today: { CalendarDay(year: 2026, month: 10, day: 13) },
                         newId: { self.ids += 1; return "e\(self.ids)" }, onChanged: { self.changes += 1 })
    }

    private let importedBuy = StoredTransaction(sourceKey: "hash", date: "2023-01-10", eventType: "BUY", ticker: "AAPL", quantity: "10", price: "150.00", currency: "USD",
                                                amount: "1500.00", fee: "-1.00", description: "IBKR Trade", splitRatio: nil, isin: "US0378331005", conid: "900001",
                                                instrumentDescription: "APPLE INC", listingExchange: "NASDAQ")

    private func field(_ entry: ManualEntry?, _ name: String) -> String? {
        guard let data = entry?.fields.data(using: .utf8), let json = try? JSONSerialization.jsonObject(with: data) as? [String: String] else { return nil }
        return json[name]
    }

    func testAValidBuyIsSavedWithItsFormAndTheImportedIdentity() throws {
        let store = MemoryStore(imported: [importedBuy])
        XCTAssertEqual(try service(store).save(.buy, trade(.buy, "2024-02-01", "3")), .saved(entryId: "e1"))
        let entry = store.entries["e1"]
        XCTAssertEqual(entry?.type, "BUY")
        XCTAssertEqual(field(entry, "quantity"), "3")
        XCTAssertEqual(entry?.enteredAt, "2026-10-13T08:00:00Z")
        XCTAssertEqual(entry?.rows.first?.isin, "US0378331005")
        XCTAssertEqual(entry?.rows.first?.listingExchange, "NASDAQ")
        XCTAssertEqual(changes, 1)
    }

    func testProblemsAreReportedAndNothingIsSaved() throws {
        let store = MemoryStore()
        XCTAssertEqual(try service(store).save(.buy, trade(.buy, "2026-10-14", "0")), .problems([.futureDate, .outOfRange("quantity")]))
        XCTAssertTrue(store.entries.isEmpty)
        XCTAssertEqual(changes, 0)
    }

    func testSellingMoreThanHeldIsRefused() throws {
        let store = MemoryStore(imported: [importedBuy])
        XCTAssertEqual(try service(store).save(.sell, trade(.sell, "2024-02-01", "12")), .oversold(Oversell(ticker: "AAPL", date: "2024-02-01", held: d("10"))))
        XCTAssertTrue(store.entries.isEmpty)
    }

    func testAPossibleDuplicateNeedsConfirmation() throws {
        let store = MemoryStore(imported: [importedBuy])
        let operations = service(store)
        let fields = trade(.buy, "2023-01-10", "10", price: "150.00")

        guard case .possibleDuplicate(_, let existing) = try operations.save(.buy, fields) else { return XCTFail("no duplicate warning") }
        XCTAssertEqual(existing, importedBuy)
        XCTAssertTrue(store.entries.isEmpty)
        XCTAssertEqual(try operations.save(.buy, fields, confirmDuplicate: true), .saved(entryId: "e2"))
        XCTAssertEqual(try operations.duplicatesOfImports().count, 1)
    }

    func testEditingReplacesTheEntryAndIsCheckedWithoutItsOldRows() throws {
        let store = MemoryStore()
        let operations = service(store)
        _ = try operations.save(.buy, trade(.buy, "2024-01-02", "5"))
        XCTAssertEqual(try operations.save(.buy, trade(.buy, "2024-01-02", "8"), entryId: "e1"), .saved(entryId: "e1"))
        XCTAssertEqual(store.entries["e1"]?.rows.first?.quantity, "8")
        _ = try operations.save(.sell, trade(.sell, "2024-03-01", "8"))
        // Checked without the entry's old rows: counting both buys (8 + 5) would hide that 5 cannot cover the sale of 8.
        guard case .oversold = try operations.save(.buy, trade(.buy, "2024-01-02", "5"), entryId: "e1") else { return XCTFail("edit was not refused") }
    }

    func testDeletingABuyThatASaleNeedsIsRefused() throws {
        let store = MemoryStore()
        let operations = service(store)
        _ = try operations.save(.buy, trade(.buy, "2024-01-02", "5"))
        _ = try operations.save(.sell, trade(.sell, "2024-03-01", "5"))

        XCTAssertEqual(try operations.delete("e1"), .oversold(Oversell(ticker: "AAPL", date: "2024-03-01", held: .zero)))
        XCTAssertEqual(try operations.delete("e2"), .deleted)
        XCTAssertEqual(try operations.delete("e1"), .deleted)
        XCTAssertEqual(try operations.delete("e1"), .notFound)
    }

    func testASplitUsesTheSharesHeldBeforeItsDate() throws {
        let store = MemoryStore()
        let operations = service(store)
        let split = ["date": "2024-06-10", "ticker": "AAPL", "currency": "USD", "newShares": "4", "oldShares": "1"]

        XCTAssertEqual(try operations.save(.split, split), .nothingHeld(ticker: "AAPL", date: "2024-06-10"))
        _ = try operations.save(.buy, trade(.buy, "2024-01-02", "3"))
        XCTAssertEqual(try operations.save(.split, split), .saved(entryId: "e2"))
        let row = store.entries["e2"]?.rows.first
        XCTAssertEqual(row?.quantity, "9")
        XCTAssertEqual(row?.splitRatio, "4")
        XCTAssertEqual(field(store.entries["e2"], "heldBefore"), "3")
    }
}
