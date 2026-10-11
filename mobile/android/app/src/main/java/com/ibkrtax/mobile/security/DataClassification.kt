package com.ibkrtax.mobile.security

/** The four classes of the data-anonymization spec ("Data classification"). */
enum class DataClass {
    /** Names a person or account directly (account number, name, tax ID). Never stored or shown in full. */
    DIRECT_IDENTIFIER,

    /** Could single a person out in combination (holdings, dates, pseudonyms, masked values). */
    QUASI_IDENTIFIER,

    /** Amounts, quantities and prices of one person's portfolio. */
    FINANCIAL_VALUE,

    /** Technical data with no link to a person on its own. */
    NON_PERSONAL,
}

data class Classified(val dataClass: DataClass, val handling: String)

/**
 * Classification of every field the Android app takes from a broker report or stores
 * (mobile-encrypted-drive-backup task 7.1). `DataClassificationTest` fails when a stored
 * column has no entry here or a direct identifier would be stored. Everything stored is in
 * the SQLCipher database; financial values never leave the device except in encrypted backups.
 */
object DataClassification {
    private val technical = Classified(DataClass.NON_PERSONAL, "technical")
    private val instrument = Classified(DataClass.QUASI_IDENTIFIER, "instrument held; shown in the app, sent as a ticker symbol for prices only")
    private val money = Classified(DataClass.FINANCIAL_VALUE, "stored encrypted; shown only in the app")

    /** Fields read from a report that are not stored as they are. */
    val reportFields: Map<String, Classified> = mapOf(
        "FlexReport.accountId" to Classified(DataClass.DIRECT_IDENTIFIER, "stored only as an HMAC pseudonym and a last-four mask"),
        "report file name" to Classified(DataClass.DIRECT_IDENTIFIER, "IBKR puts the account number in it; never stored, shown masked during import"),
    )

    /** Every stored column, as "table.column". */
    val columns: Map<String, Classified> = mapOf(
        "schema_info.key" to technical,
        "schema_info.value" to technical,

        "reports.id" to technical,
        "reports.fingerprint" to Classified(DataClass.QUASI_IDENTIFIER, "keyed HMAC of the report content; detects duplicates on this device only"),
        "reports.account_pseudonym" to Classified(DataClass.QUASI_IDENTIFIER, "HMAC-SHA256 with a device key; not reversible without it"),
        "reports.account_masked" to Classified(DataClass.QUASI_IDENTIFIER, "last four characters only; the only account form shown"),
        "reports.imported_at" to technical,
        "reports.inserted_count" to technical,
        "reports.skipped_count" to technical,
        "reports.backup_file" to Classified(DataClass.NON_PERSONAL, "random file name"),
        "reports.backup_status" to technical,

        "transactions.id" to technical,
        "transactions.report_id" to technical,
        "transactions.source_key" to Classified(DataClass.QUASI_IDENTIFIER, "derived from the record's own fields; used to avoid duplicates"),
        "transactions.date" to Classified(DataClass.QUASI_IDENTIFIER, "trade or payment date"),
        "transactions.event_type" to technical,
        "transactions.ticker" to instrument,
        "transactions.quantity" to money,
        "transactions.price" to money,
        "transactions.currency" to technical,
        "transactions.amount" to money,
        "transactions.fee" to money,
        "transactions.description" to Classified(DataClass.QUASI_IDENTIFIER, "broker text about the instrument or payment; shown only in the app"),
        "transactions.split_ratio" to technical,
        "transactions.isin" to instrument,
        "transactions.conid" to instrument,
        "transactions.instrument_description" to instrument,
        "transactions.listing_exchange" to instrument,

        "report_records.report_id" to technical,
        "report_records.source_key" to Classified(DataClass.QUASI_IDENTIFIER, "same as transactions.source_key"),

        "app_settings.key" to technical,
        "app_settings.value" to Classified(
            DataClass.QUASI_IDENTIFIER,
            "the Finnhub key (a credential: sent only to Finnhub, never shown in full or backed up) and the backup folder URI",
        ),

        "price_cache.symbol" to instrument,
        "price_cache.price" to Classified(DataClass.NON_PERSONAL, "public market price"),
        "price_cache.currency" to technical,
        "price_cache.quote_time" to technical,
        "price_cache.retrieved_at" to technical,
        "price_cache.previous_close" to Classified(DataClass.NON_PERSONAL, "public market price"),

        "fx_rates.code" to technical,
        "fx_rates.mid" to Classified(DataClass.NON_PERSONAL, "public exchange rate"),
        "fx_rates.effective_date" to technical,
    )
}
