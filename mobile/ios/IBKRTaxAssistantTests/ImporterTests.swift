import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

/// `BigDecimal` / Python `Decimal` behavior the importer relies on.
final class ExactDecimalTests: XCTestCase {
    func testParsingKeepsScaleAndFormatsLikeBigDecimal() {
        let cases = [
            "10": "10", "10.00": "10.00", "-1.00": "-1.00", "+5": "5", "0.000": "0.000", "-0": "0", "007.50": "7.50",
            ".5": "0.5", "1.": "1", "0.0000001": "1E-7", "0.000001": "0.000001", "1E+3": "1E+3", "1.5e2": "1.5E+2", "123E-2": "1.23",
        ]
        for (input, expected) in cases { XCTAssertEqual(d(input).description, expected, input) }
        for invalid in ["", "abc", "1.2.3", "1e", "--1", "1 000", "."] { XCTAssertNil(ExactDecimal(invalid), invalid) }
    }

    func testEqualityIsScaleSensitiveLikeBigDecimalEquals() {
        XCTAssertEqual(d("2.0"), d("2.0"))
        XCTAssertNotEqual(d("2.0"), d("2.00"))
        XCTAssertEqual(d("-0.00"), d("0.00"))
    }

    func testMultiplicationIsExact() {
        XCTAssertEqual((d("-8") * d("175.00")).description, "-1400.00")
        XCTAssertEqual((d("1234.5678") * d("123.456789")).description, "152415.7763907942")
        XCTAssertEqual((d("12345678901234567890.5") * d("98765432109876543210.25")).description, "1219326311370217952289932936891510440477.625")
        // Java has no negative zero (Python prints "-0.0"); Android parity wins.
        XCTAssertEqual((d("0") * d("-3.5")).description, "0.0")
    }

    func testDivisionMatchesPythonDecimalContext() {
        let cases: [(String, String, String)] = [
            ("3", "1", "3"), ("1", "8", "0.125"), ("1", "3", "0.3333333333333333333333333333"), ("2", "3", "0.6666666666666666666666666667"),
            ("4.0", "2", "2.0"), ("10", "4", "2.5"), ("1", "1.0", "1"), ("100", "1", "100"), ("1.00", "4", "0.25"),
            ("5", "2", "2.5"), ("-7", "2", "-3.5"), ("0", "5", "0"), ("1", "7", "0.1428571428571428571428571429"),
        ]
        for (a, b, expected) in cases { XCTAssertEqual(d(a).divided(by: d(b))?.description, expected, "\(a)/\(b)") }
        XCTAssertNil(d("1").divided(by: d("0")))
    }
}

/// Mirrors the Android `IbkrTextTest` (and so tests/test_parser.py and test_import_integrity.py).
final class IbkrTextTests: XCTestCase {
    func testNormalizeDate() {
        let cases = [
            "20250102": "2025-01-02", "01/02/2025": "2025-01-02", "2025-01-02, 15:00:00": "2025-01-02",
            "13/02/2025": "2025-02-13", "02-Jan-25": "2025-01-02", "02-jan-70": "1970-01-02", "2024-02-29": "2024-02-29",
        ]
        for (input, expected) in cases { XCTAssertEqual(IbkrText.normalizeDate(input), expected, input) }
        XCTAssertNil(IbkrText.normalizeDate(""))
        XCTAssertNil(IbkrText.normalizeDate("2025-02-30"))
        XCTAssertNil(IbkrText.normalizeDate("2023-02-29"))
        XCTAssertNil(IbkrText.normalizeDate("not-a-date"))
    }

    func testExtractTicker() {
        XCTAssertEqual(IbkrText.extractTicker("AGR(US05351W1036) Cash Dividend", symbolColumn: "", quantity: .zero), "AGR")
        XCTAssertEqual(IbkrText.extractTicker("MGA (CA5592224011) Cash Dividend", symbolColumn: "", quantity: .zero), "MGA")
        XCTAssertEqual(IbkrText.extractTicker("Unknown Description", symbolColumn: "AAPL", quantity: .zero), "AAPL")
        XCTAssertEqual(IbkrText.extractTicker("TSLA Cash Div", symbolColumn: "", quantity: .zero), "TSLA")
        XCTAssertEqual(IbkrText.extractTicker("lowercase words", symbolColumn: "", quantity: .zero), "UNKNOWN")
        XCTAssertEqual(
            IbkrText.extractTicker("MRK(US58933Y1055) Spinoff 1 for 10 (OGN, ORGANON & CO-W/I, US68622V1061)", symbolColumn: "MRK", quantity: d("1")),
            "OGN"
        )
    }

    func testExtractIsin() {
        XCTAssertEqual(IbkrText.extractIsin("OKE(US6826801036) Cash Dividend USD 0.99 per Share"), "US6826801036")
        XCTAssertEqual(IbkrText.extractIsin("MGA (CA5592224011) Cash Dividend"), "CA5592224011")
        XCTAssertEqual(IbkrText.extractIsin("Cash dividend without security identifier"), "")
        XCTAssertEqual(
            IbkrText.extractIsin("MRK(US58933Y1055) Spinoff 1 for 10 (OGN, ORGANON & CO-W/I, US68622V1061)", ticker: "OGN"),
            "US68622V1061"
        )
    }

    func testParseDecimal() {
        XCTAssertEqual(IbkrText.parseDecimalOrNull("1,000.50"), d("1000.50"))
        XCTAssertEqual(IbkrText.parseDecimalOrNull("\"1,234.56\""), d("1234.56"))
        XCTAssertEqual(IbkrText.parseDecimalOrNull("-500"), d("-500"))
        XCTAssertEqual(IbkrText.parseDecimalOrNull(""), .zero)
        XCTAssertNil(IbkrText.parseDecimalOrNull("abc"))
    }

    func testClassifyTradeType() {
        XCTAssertEqual(IbkrText.classifyTradeType("ACATS Transfer", quantity: d("10")), "TRANSFER")
        XCTAssertEqual(IbkrText.classifyTradeType("Internal Transfer", quantity: d("10")), "TRANSFER")
        XCTAssertEqual(IbkrText.classifyTradeType("Buy Order", quantity: d("10")), "BUY")
        XCTAssertEqual(IbkrText.classifyTradeType("Sell Order", quantity: d("-5")), "SELL")
        XCTAssertEqual(IbkrText.classifyTradeType("Random Text", quantity: .zero), "UNKNOWN")
    }

    func testSplitRatioMatchesPythonDecimalDivision() {
        XCTAssertEqual(IbkrText.extractSplitRatio("WMT Split 3 for 1")?.description, "3")
        XCTAssertEqual(IbkrText.extractSplitRatio("GE Split 1 for 8")?.description, "0.125")
        XCTAssertEqual(IbkrText.extractSplitRatio("X Split 1 for 3")?.description, "0.3333333333333333333333333333")
        XCTAssertNil(IbkrText.extractSplitRatio("No split here"))
        XCTAssertNil(IbkrText.extractSplitRatio("X Split 1 for 0"))
    }

    func testCsvReaderMatchesPythonDialect() throws {
        let rows = try CsvReader.read("a,\"b,c\",\"say \"\"hi\"\"\"\n\nx,\"multi\nline\",\nlast")
        XCTAssertEqual(rows[0].fields, ["a", "b,c", "say \"hi\""])
        XCTAssertEqual(rows[1].fields, [])
        XCTAssertEqual(rows[2].fields, ["x", "multi\nline", ""])
        XCTAssertEqual(rows[2].line, 3)
        XCTAssertEqual(rows[3].fields, ["last"])
        XCTAssertEqual(rows[3].line, 5)
    }
}

/// Mirrors the Android `FlexQueryParserTest`.
final class FlexQueryParserTests: XCTestCase {
    private let tradesHeader = "Trades,Header,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Comm/Fee,Description"

    func testValidFixturesMatchPythonReferenceRecords() throws {
        for name in [Fixtures.validBasic, Fixtures.validFollowup] {
            let expected = Fixtures.expectedJson(name) as! NSDictionary
            let actual = referenceJson(try parseSuccess(Fixtures.flexQuery(name), sourceFile: name)) as NSDictionary
            XCTAssertEqual(actual, expected, name)
        }
    }

    func testListingExchangeComesFromInstrumentInformation() throws {
        let report = try parseSuccess(Fixtures.flexQuery(Fixtures.validBasic))
        XCTAssertEqual(report.trades.first { $0.identity.ticker == "AAPL" }?.identity.listingExchange, "NASDAQ")
        XCTAssertEqual(report.corporateActions.map(\.identity.listingExchange), ["IBIS"])
        XCTAssertEqual(report.dividends.map(\.identity.listingExchange), ["NASDAQ"])
    }

    func testEmptyFileIsRejected() {
        XCTAssertEqual(parseFailure(Fixtures.flexQuery(Fixtures.empty)), .emptyFile)
    }

    func testNonIbkrCsvIsUnsupported() {
        XCTAssertEqual(parseFailure(Fixtures.flexQuery(Fixtures.unsupportedFormat)), .unsupportedFormat)
    }

    func testMalformedRowRejectsTheWholeReport() {
        XCTAssertEqual(parseFailure(Fixtures.flexQuery(Fixtures.malformedTrades)), .malformed(.invalidDate, line: 5, section: "Trades"))
    }

    func testImportErrorsNeverContainReportContents() {
        let description = String(describing: parseFailure(Fixtures.flexQuery(Fixtures.malformedTrades)))
        for secret in ["MSFT", "AAPL", "not-a-date", "150.00", "U00000001"] { XCTAssertFalse(description.contains(secret), description) }
    }

    func testInvalidNumberIsRejected() {
        XCTAssertEqual(parseFailure(csv(tradesHeader, "Trades,Data,Stocks,USD,XYZ,2024-01-02,ten,10,0,")), .malformed(.invalidNumber, line: 2, section: "Trades"))
    }

    func testShortRowIsRejected() {
        XCTAssertEqual(parseFailure(csv(tradesHeader, "Trades,Data,Stocks,USD,XYZ,2024-01-02,1")), .malformed(.shortRow, line: 2, section: "Trades"))
    }

    func testHeaderWithoutRequiredColumnIsRejected() {
        let error = parseFailure(csv("Trades,Header,Asset Category,Currency,Symbol,Date/Time", "Trades,Data,Stocks,USD,XYZ,2024-01-02"))
        XCTAssertEqual(error, .malformed(.missingColumns, line: 1, section: "Trades"))
    }

    func testInvalidUtf8IsRejected() {
        XCTAssertEqual(parseFailure(csv(tradesHeader) + Data([0xC3, 0x28])), .malformed(.notUtf8, line: 1))
    }

    func testUnterminatedQuoteIsRejected() {
        XCTAssertEqual(parseFailure(csv(tradesHeader, "Trades,Data,Stocks,USD,XYZ,\"2024-01-02,1,10,0,")), .malformed(.unterminatedQuote, line: 2))
    }

    func testByteOrderMarkAndCrLfLineEndingsAreAccepted() throws {
        let text = "\u{FEFF}\(tradesHeader)\r\nTrades,Data,Stocks,USD,XYZ,2024-01-02,1,10,0,\r\n"
        XCTAssertEqual(try parseSuccess(Data(text.utf8)).trades.map(\.identity.ticker), ["XYZ"])
    }

    func testNarrowInstrumentSectionSuppliesMissingIdentity() throws {
        // Port of test_narrow_financial_instrument_section_supplies_missing_identity.
        let report = try parseSuccess(csv(
            tradesHeader,
            "Trades,Data,Stocks,USD,XYZ,2024-01-02,1,10,0,",
            "Financial Instrument Information,Header,Symbol,Security ID,Conid,Description",
            "Financial Instrument Information,Data,XYZ,US0378331005,12345,TEST CORPORATION"
        ))
        XCTAssertEqual(report.trades.map(\.identity), [InstrumentIdentity(ticker: "XYZ", isin: "US0378331005", conid: "12345", instrumentDescription: "TEST CORPORATION")])
    }

    func testDuplicateSymbolIdentityUsesCurrencyContext() throws {
        // Port of test_duplicate_symbol_identity_uses_currency_context_from_statement.
        let report = try parseSuccess(csv(
            tradesHeader,
            "Trades,Data,Stocks,USD,SBER,2022-01-14,5,13.335,-0.525,",
            "Corporate Actions,Header,Asset Category,Currency,Report Date,Description,Quantity",
            "Corporate Actions,Data,Stocks,USD,2022-05-24,\"SBER(US80585Y3080) Tendered to US80585Y3CNV (SBER, SBERBANK ADR, US80585Y3080)\",-5",
            "Corporate Actions,Data,Stocks,RUB,2022-05-24,\"SBER.CNV4(563839405) Merged WITH SBER (SBER, SBERBANK COMMON, RU0009029540)\",20",
            "Financial Instrument Information,Header,Symbol,Security ID,Conid,Description",
            "Financial Instrument Information,Data,SBER,US80585Y3080,90581067,SBERBANK ADR",
            "Financial Instrument Information,Data,SBER,RU0009029540,360308912,SBERBANK COMMON"
        ))
        let buy = try XCTUnwrap(report.trades.first)
        let tender = try XCTUnwrap(report.corporateActions.first { $0.quantity.signum < 0 })
        let addition = try XCTUnwrap(report.corporateActions.first { $0.quantity.signum > 0 })

        XCTAssertEqual(buy.identity.isin, "US80585Y3080")
        XCTAssertEqual(tender.identity.isin, "US80585Y3080")
        XCTAssertEqual(buy.identity.conid, "90581067")
        XCTAssertEqual(addition.identity.isin, "RU0009029540")
        XCTAssertEqual(addition.currency, "RUB")
    }

    private func parseSuccess(_ bytes: Data, sourceFile: String = "test.csv") throws -> FlexReport {
        try FlexQueryParser.parse(bytes, sourceFile: sourceFile).get()
    }

    private func parseFailure(_ bytes: Data) -> FlexImportError? {
        if case .failure(let error) = FlexQueryParser.parse(bytes, sourceFile: "test.csv") { return error }
        XCTFail("expected failure")
        return nil
    }

    private func csv(_ lines: String...) -> Data { Data((lines.joined(separator: "\n") + "\n").utf8) }

    private func referenceJson(_ report: FlexReport) -> [String: Any] {
        func identity(_ i: InstrumentIdentity) -> [String: Any] {
            ["ticker": i.ticker, "isin": i.isin, "conid": i.conid, "instrument_description": i.instrumentDescription]
        }
        func cash(_ c: CashRecord) -> [String: Any] {
            identity(c.identity).merging(["currency": c.currency, "date": c.date, "amount": "\(c.amount)", "source_file": c.sourceFile]) { $1 }
        }
        return [
            "trades": report.trades.map { t in
                identity(t.identity).merging([
                    "currency": t.currency, "date": t.date, "qty": "\(t.quantity)", "price": "\(t.price)", "commission": "\(t.commission)",
                    "type": t.type, "source": t.source, "source_file": t.sourceFile,
                ]) { $1 }
            },
            "dividends": report.dividends.map(cash),
            "taxes": report.taxes.map(cash),
            "corp_actions": report.corporateActions.map { c in
                identity(c.identity).merging([
                    "currency": c.currency, "date": c.date, "qty": "\(c.quantity)", "price": "0", "commission": "0", "type": c.type,
                    "ratio": c.ratio.map { "\($0)" as Any } ?? NSNull(), "source": c.source, "source_file": c.sourceFile,
                ]) { $1 }
            },
        ]
    }
}

private struct NotReady: Error {
    let plan: ImportPlanResult
}

/// Mirrors the Android `ImportPlanTest`.
final class ImportPlanTests: XCTestCase {
    private func parse(_ name: String) throws -> FlexReport { try FlexQueryParser.parse(Fixtures.flexQuery(name), sourceFile: name).get() }

    private func ready(_ report: FlexReport) throws -> (transactions: [StoredTransaction], duplicates: Int) {
        let plan = ImportPlan.build(report)
        guard case .ready(let transactions, let duplicates) = plan else { throw NotReady(plan: plan) }
        return (transactions, duplicates)
    }

    func testSourceKeysMatchPythonReference() throws {
        for name in [Fixtures.validBasic, Fixtures.validFollowup] {
            XCTAssertEqual(try ready(parse(name)).transactions.map(\.sourceKey), Fixtures.expectedSourceKeys(name), name)
        }
    }

    func testStoredValuesMirrorDesktopTransactions() throws {
        let transactions = try ready(parse(Fixtures.validBasic)).transactions
        let sell = try XCTUnwrap(transactions.first { $0.eventType == "SELL" && $0.ticker == "AAPL" })
        XCTAssertEqual(sell.amount, "-1400.00")
        XCTAssertEqual(sell.fee, "-1.00")
        XCTAssertEqual(transactions.filter { $0.eventType == "SPLIT" }.map(\.splitRatio), ["2"])

        let dividend = try XCTUnwrap(transactions.first { $0.eventType == "DIVIDEND" })
        XCTAssertEqual(dividend.description, "Dividend")
        XCTAssertEqual(dividend.amount, "3.60")
        XCTAssertEqual(transactions.filter { $0.eventType == "TAX" }.map(\.description), ["Tax"])
    }

    func testAccountIdIsReadForPseudonymization() throws {
        XCTAssertEqual(try parse(Fixtures.validBasic).accountId, "U00000001")
    }

    func testIdenticalRecordsInOneReportAreSkipped() throws {
        let report = try parse(Fixtures.validBasic)
        var doubled = report
        doubled.trades.append(report.trades[0])

        let plan = try ready(doubled)
        XCTAssertEqual(plan.duplicates, 1)
        XCTAssertEqual(plan.transactions, try ready(report).transactions)
    }

    func testInvalidRecordRejectsTheBatch() throws {
        let report = try parse(Fixtures.validBasic)
        var noCurrency = report
        var broken = report.trades[0]
        broken.currency = ""
        broken.date = "2023-12-01"
        noCurrency.trades.append(broken)
        XCTAssertEqual(ImportPlan.build(noCurrency), .invalid(.missingDateTickerOrCurrency))

        var unknown = report
        var odd = report.trades[0]
        odd.type = "UNKNOWN"
        odd.quantity = d("1")
        unknown.trades.append(odd)
        XCTAssertEqual(ImportPlan.build(unknown), .invalid(.unknownEventType))
    }
}
