import Foundation

/**
 * On-device parser for IBKR Activity Flex Query CSV reports, ported from the Android
 * `FlexQueryParser`.
 *
 * Valid reports produce the same records as the Python reference `parse_csv`. Unlike the
 * reference, which skips or partially imports broken input, any malformed row in a parsed
 * section rejects the whole report so an import is all-or-nothing (mobile-report-upload spec).
 */
enum FlexQueryParser {
    private static let trades = "Trades"
    private static let corporateActions = "Corporate Actions"
    private static let dividends = "Dividends"
    private static let withholdingTax = "Withholding Tax"
    private static let instruments = "Financial Instrument Information"
    private static let accountInformation = "Account Information"

    /// A Header row for one of these sections identifies an IBKR statement.
    private static let recognizedSections: Set = ["Statement", accountInformation, trades, corporateActions, dividends, withholdingTax, instruments]

    private static let stockAssets: Set = ["Stocks", "Equity"]
    private static let corporateActionTypes: Set = ["SPLIT", "STOCK_DIV", "MERGER", "SPINOFF"]

    static func parse(_ bytes: Data, sourceFile: String) -> Result<FlexReport, FlexImportError> {
        do {
            return .success(try parseOrThrow(bytes, sourceFile: sourceFile))
        } catch let error as FlexImportError {
            return .failure(error)
        } catch {
            preconditionFailure("unexpected parser error")
        }
    }

    private struct InstrumentInfo {
        let isin: String
        let conid: String
        let description: String
        let exchange: String
    }

    private struct Section {
        let name: String
        let line: Int
        let columns: [String: Int]

        func column(_ names: String...) -> Int? { names.lazy.compactMap { columns[$0] }.first }

        func required(_ names: String...) throws -> Int {
            guard let index = names.lazy.compactMap({ columns[$0] }).first else {
                throw FlexImportError.malformed(.missingColumns, line: line, section: name)
            }
            return index
        }
    }

    private static func parseOrThrow(_ bytes: Data, sourceFile: String) throws -> FlexReport {
        if bytes.isEmpty { throw FlexImportError.emptyFile }
        guard var text = String(data: bytes, encoding: .utf8) else { throw FlexImportError.malformed(.notUtf8, line: 1) }
        if text.hasPrefix("\u{FEFF}") { text.removeFirst() }
        let rows: [CsvRow]
        do {
            rows = try CsvReader.read(text)
        } catch let error as UnterminatedQuoteError {
            throw FlexImportError.malformed(.unterminatedQuote, line: error.line)
        }
        guard rows.contains(where: { $0.fields.count >= 2 && $0.fields[1] == "Header" && recognizedSections.contains($0.fields[0]) }) else {
            throw FlexImportError.unsupportedFormat
        }

        var report = FlexReport(trades: [], dividends: [], taxes: [], corporateActions: [], accountId: nil)
        var instrumentInfo: [String: [InstrumentInfo]] = [:]
        var sections: [String: Section] = [:]

        for row in rows {
            let fields = row.fields
            if fields.count < 2 { continue }
            let name = fields[0]
            if fields[1] == "Header" {
                var columns: [String: Int] = [:]
                for (index, value) in fields.enumerated() { columns[value.trimmingCharacters(in: .whitespacesAndNewlines)] = index }
                sections[name] = Section(name: name, line: row.line, columns: columns)
                continue
            }
            guard fields[1] == "Data", let section = sections[name] else { continue }
            let reader = RowReader(section: section, row: row)
            switch name {
            case trades:
                if let trade = try parseTrade(reader, sourceFile: sourceFile) { report.trades.append(trade) }
            case corporateActions:
                if let action = try parseCorporateAction(reader, sourceFile: sourceFile) { report.corporateActions.append(action) }
            case dividends:
                if let cash = try parseCash(reader, sourceFile: sourceFile, amountColumns: ["Amount", "Gross Rate", "Gross Amount"],
                                            dateColumns: ["Date", "PayDate"], descriptionRequired: true) {
                    report.dividends.append(cash)
                }
            case withholdingTax:
                if let cash = try parseCash(reader, sourceFile: sourceFile, amountColumns: ["Amount"], dateColumns: ["Date"], descriptionRequired: false) {
                    report.taxes.append(cash)
                }
            case instruments:
                if let (symbol, info) = try parseInstrument(reader) { instrumentInfo[symbol, default: []].append(info) }
            case accountInformation:
                if let account = try parseAccountId(reader) { report.accountId = account }
            default:
                break
            }
        }
        return resolveIdentities(report, instrumentInfo)
    }

    /// Reads fields of one Data row, failing the import on short rows or bad values.
    private struct RowReader {
        let section: Section
        let row: CsvRow

        func text(_ index: Int) throws -> String {
            guard index < row.fields.count else { throw FlexImportError.malformed(.shortRow, line: row.line, section: section.name) }
            return row.fields[index]
        }

        func optionalText(_ index: Int?) throws -> String { try index.map(text) ?? "" }

        func date(_ index: Int) throws -> String {
            guard let date = IbkrText.normalizeDate(try text(index)) else {
                throw FlexImportError.malformed(.invalidDate, line: row.line, section: section.name)
            }
            return date
        }

        func decimal(_ index: Int) throws -> ExactDecimal {
            guard let value = IbkrText.parseDecimalOrNull(try text(index)) else {
                throw FlexImportError.malformed(.invalidNumber, line: row.line, section: section.name)
            }
            return value
        }
    }

    private static func isStock(_ reader: RowReader, _ column: Int?) throws -> Bool {
        guard let column else { return true }
        return stockAssets.contains(try reader.text(column))
    }

    private static func trimmed(_ text: String) -> String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    private static func parseTrade(_ reader: RowReader, sourceFile: String) throws -> TradeRecord? {
        let section = reader.section
        let date = try section.required("Date/Time", "Date", "TradeDate")
        let quantity = try section.required("Quantity")
        let price = try section.required("T. Price", "TradePrice", "Price")
        let currency = try section.required("Currency")
        let symbol = section.column("Symbol", "Ticker")
        let commission = section.column("Comm/Fee", "IBCommission", "Commission")
        let description = section.column("Description")

        if !(try isStock(reader, section.column("Asset Category", "Asset Class"))) { return nil }
        let descriptionText = try reader.optionalText(description)
        if descriptionText.contains("Total") { return nil }

        let tradeDate = try reader.date(date)
        let qty = try reader.decimal(quantity)
        if qty.signum == 0 { return nil }

        let ticker = IbkrText.extractTicker(descriptionText, symbolColumn: try reader.optionalText(symbol), quantity: qty)
        return TradeRecord(
            identity: InstrumentIdentity(ticker: ticker, isin: IbkrText.extractIsin(descriptionText, ticker: ticker)),
            currency: try reader.text(currency),
            date: tradeDate,
            quantity: qty,
            price: try reader.decimal(price),
            commission: try commission.map(reader.decimal) ?? .zero,
            type: IbkrText.classifyTradeType(descriptionText, quantity: qty),
            source: descriptionText.isEmpty ? "IBKR Trade" : descriptionText,
            sourceFile: sourceFile
        )
    }

    private static func parseCorporateAction(_ reader: RowReader, sourceFile: String) throws -> CorporateActionRecord? {
        let section = reader.section
        let date = try section.required("Date/Time", "Report Date")
        let description = try section.required("Description")
        let quantity = try section.required("Quantity")
        let currency = section.column("Currency")
        let symbol = section.column("Symbol", "Ticker")

        if !(try isStock(reader, section.column("Asset Category"))) { return nil }
        let descriptionText = try reader.text(description)
        if descriptionText.contains("Total") { return nil }

        let actionDate = try reader.date(date)
        let qty = try reader.decimal(quantity)
        let ratio = IbkrText.extractSplitRatio(descriptionText)
        let type = ratio != nil ? "SPLIT" : IbkrText.classifyCorporateAction(descriptionText, quantity: qty)
        if !corporateActionTypes.contains(type) { return nil }

        let ticker = IbkrText.extractTicker(descriptionText, symbolColumn: try reader.optionalText(symbol), quantity: qty)
        return CorporateActionRecord(
            identity: InstrumentIdentity(ticker: ticker, isin: IbkrText.extractIsin(descriptionText, ticker: ticker)),
            currency: try currency.map(reader.text) ?? "USD",
            date: actionDate,
            quantity: qty,
            type: type,
            ratio: ratio,
            source: descriptionText,
            sourceFile: sourceFile
        )
    }

    private static func parseCash(
        _ reader: RowReader,
        sourceFile: String,
        amountColumns: [String],
        dateColumns: [String],
        descriptionRequired: Bool
    ) throws -> CashRecord? {
        let section = reader.section
        let first = { (names: [String]) in names.lazy.compactMap { section.columns[$0] }.first }
        guard let date = first(dateColumns), let amount = first(amountColumns), let currency = section.column("Currency") else {
            throw FlexImportError.malformed(.missingColumns, line: section.line, section: section.name)
        }
        let description = descriptionRequired ? try section.required("Description", "Label") : section.column("Description", "Label")

        let descriptionText = try reader.optionalText(description)
        let currencyText = try reader.text(currency)
        // IBKR summary rows ("Total", "Total in USD", ...) sit in the currency column.
        if descriptionText.contains("Total") || currencyText.hasPrefix("Total") { return nil }

        let ticker = IbkrText.extractTicker(descriptionText, symbolColumn: "", quantity: .zero)
        return CashRecord(
            identity: InstrumentIdentity(ticker: ticker, isin: IbkrText.extractIsin(descriptionText, ticker: ticker)),
            currency: currencyText,
            date: try reader.date(date),
            amount: try reader.decimal(amount),
            sourceFile: sourceFile
        )
    }

    private static func parseInstrument(_ reader: RowReader) throws -> (String, InstrumentInfo)? {
        let section = reader.section
        let symbol = trimmed(try reader.text(try section.required("Symbol", "Ticker"))).uppercased()
        if symbol.isEmpty { return nil }
        let securityId = trimmed(try reader.optionalText(section.column("Security ID")))
        return (symbol, InstrumentInfo(
            isin: IbkrText.extractIsin("\(symbol)(\(securityId))"),
            conid: trimmed(try reader.optionalText(section.column("Conid", "ConID"))),
            description: trimmed(try reader.optionalText(section.column("Description"))),
            exchange: trimmed(try reader.optionalText(section.column("Listing Exch")))
        ))
    }

    private static func parseAccountId(_ reader: RowReader) throws -> String? {
        guard let name = reader.section.column("Field Name"), let value = reader.section.column("Field Value") else { return nil }
        if trimmed(try reader.text(name)) != "Account" { return nil }
        let account = trimmed(try reader.text(value))
        return account.isEmpty ? nil : account
    }

    /// Port of the identity back-fill at the end of Python `parse_csv`.
    private static func resolveIdentities(_ report: FlexReport, _ instruments: [String: [InstrumentInfo]]) -> FlexReport {
        struct Key: Hashable {
            let ticker: String
            let currency: String
        }
        func key(_ ticker: String, _ currency: String) -> Key { Key(ticker: trimmed(ticker).uppercased(), currency: trimmed(currency).uppercased()) }

        let all = report.trades.map { ($0.identity, $0.currency) } + report.dividends.map { ($0.identity, $0.currency) }
            + report.taxes.map { ($0.identity, $0.currency) } + report.corporateActions.map { ($0.identity, $0.currency) }
        var observed: [Key: Set<String>] = [:]
        for (identity, currency) in all {
            let k = key(identity.ticker, currency)
            if !k.ticker.isEmpty && !k.currency.isEmpty && !identity.isin.isEmpty { observed[k, default: []].insert(identity.isin) }
        }

        func resolve(_ identity: InstrumentIdentity, _ currency: String) -> InstrumentIdentity {
            let candidates = instruments[trimmed(identity.ticker).uppercased()] ?? []
            var isin = identity.isin
            var instrument: InstrumentInfo?
            if !isin.isEmpty {
                instrument = candidates.first { $0.isin == isin }
            } else {
                let seen = observed[key(identity.ticker, currency)] ?? []
                if seen.count == 1 {
                    isin = seen.first!
                    instrument = candidates.first { $0.isin == isin }
                } else if seen.isEmpty && candidates.count == 1 {
                    instrument = candidates[0]
                    isin = candidates[0].isin
                }
            }
            var resolved = identity
            resolved.isin = isin
            if resolved.conid.isEmpty { resolved.conid = instrument?.conid ?? "" }
            if resolved.instrumentDescription.isEmpty { resolved.instrumentDescription = instrument?.description ?? "" }
            if resolved.listingExchange.isEmpty { resolved.listingExchange = instrument?.exchange ?? "" }
            return resolved
        }

        var resolved = report
        for i in resolved.trades.indices { resolved.trades[i].identity = resolve(resolved.trades[i].identity, resolved.trades[i].currency) }
        for i in resolved.dividends.indices { resolved.dividends[i].identity = resolve(resolved.dividends[i].identity, resolved.dividends[i].currency) }
        for i in resolved.taxes.indices { resolved.taxes[i].identity = resolve(resolved.taxes[i].identity, resolved.taxes[i].currency) }
        for i in resolved.corporateActions.indices {
            resolved.corporateActions[i].identity = resolve(resolved.corporateActions[i].identity, resolved.corporateActions[i].currency)
        }
        return resolved
    }
}
