/// The four classes of the data-anonymization spec ("Data classification").
enum DataClass {
    /// Names a person or account directly (account number, name, tax ID). Never stored or shown in full.
    case directIdentifier

    /// Could single a person out in combination (holdings, dates, pseudonyms, masked values).
    case quasiIdentifier

    /// Amounts, quantities and prices of one person's portfolio.
    case financialValue

    /// Technical data with no link to a person on its own.
    case nonPersonal
}

struct Classified: Equatable {
    let dataClass: DataClass
    let handling: String
}

/**
 * Classification of every field the iOS app takes from a broker report or stores, identical to
 * the Android `DataClassification`. `DataClassificationTests` fails when a stored column has no
 * entry here or a direct identifier would be stored. Everything stored is in the SQLCipher
 * database; financial values never leave the device except in encrypted backups.
 */
enum DataClassification {
    private static let technical = Classified(dataClass: .nonPersonal, handling: "technical")
    private static let instrument = Classified(dataClass: .quasiIdentifier, handling: "instrument held; shown in the app, sent as a ticker symbol for prices only")
    private static let money = Classified(dataClass: .financialValue, handling: "stored encrypted; shown only in the app")

    /// Fields read from a report that are not stored as they are.
    static let reportFields: [String: Classified] = [
        "FlexReport.accountId": Classified(dataClass: .directIdentifier, handling: "stored only as an HMAC pseudonym and a last-four mask"),
        "report file name": Classified(dataClass: .directIdentifier, handling: "IBKR puts the account number in it; never stored, shown masked during import"),
    ]

    /// Every stored column, as "table.column".
    static let columns: [String: Classified] = [
        "schema_info.key": technical,
        "schema_info.value": technical,

        "reports.id": technical,
        "reports.fingerprint": Classified(dataClass: .quasiIdentifier, handling: "keyed HMAC of the report content; detects duplicates on this device only"),
        "reports.account_pseudonym": Classified(dataClass: .quasiIdentifier, handling: "HMAC-SHA256 with a device key; not reversible without it"),
        "reports.account_masked": Classified(dataClass: .quasiIdentifier, handling: "last four characters only; the only account form shown"),
        "reports.imported_at": technical,
        "reports.inserted_count": technical,
        "reports.skipped_count": technical,
        "reports.backup_file": Classified(dataClass: .nonPersonal, handling: "random file name"),
        "reports.backup_status": technical,

        "transactions.id": technical,
        "transactions.report_id": technical,
        "transactions.source_key": Classified(dataClass: .quasiIdentifier, handling: "derived from the record's own fields; used to avoid duplicates"),
        "transactions.date": Classified(dataClass: .quasiIdentifier, handling: "trade or payment date"),
        "transactions.event_type": technical,
        "transactions.ticker": instrument,
        "transactions.quantity": money,
        "transactions.price": money,
        "transactions.currency": technical,
        "transactions.amount": money,
        "transactions.fee": money,
        "transactions.description": Classified(dataClass: .quasiIdentifier, handling: "broker text about the instrument or payment; shown only in the app"),
        "transactions.split_ratio": technical,
        "transactions.isin": instrument,
        "transactions.conid": instrument,
        "transactions.instrument_description": instrument,
        "transactions.listing_exchange": instrument,

        "report_records.report_id": technical,
        "report_records.source_key": Classified(dataClass: .quasiIdentifier, handling: "same as transactions.source_key"),

        "app_settings.key": technical,
        "app_settings.value": Classified(dataClass: .quasiIdentifier, handling: "the Finnhub key (a credential: sent only to Finnhub, never shown in full or backed up) and the backup folder URI"),

        "price_cache.symbol": instrument,
        "price_cache.price": Classified(dataClass: .nonPersonal, handling: "public market price"),
        "price_cache.currency": technical,
        "price_cache.quote_time": technical,
        "price_cache.retrieved_at": technical,
        "price_cache.previous_close": Classified(dataClass: .nonPersonal, handling: "public market price"),

        "fx_rates.code": technical,
        "fx_rates.mid": Classified(dataClass: .nonPersonal, handling: "public exchange rate"),
        "fx_rates.effective_date": technical,
    ]
}
