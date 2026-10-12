/// NBP rates in the encrypted database; a new table replaces the previous one as a whole. Port of the Android `FxStore`.
struct FxStore: FxRateCache {
    let database: EncryptedDatabase

    func get() throws -> FxRates? {
        var date: String?
        var mids: [String: ExactDecimal] = [:]
        for row in try database.query("SELECT code, mid, effective_date FROM fx_rates") {
            guard case .text(let code)? = row[0], case .text(let mid)? = row[1], case .text(let effective)? = row[2],
                  let value = ExactDecimal(mid) else { continue }
            mids[code] = value
            date = effective
        }
        return date.map { FxRates(effectiveDate: $0, plnPerUnit: mids) }
    }

    func put(_ rates: FxRates) throws {
        try database.transaction {
            try database.execute("DELETE FROM fx_rates")
            for (code, mid) in rates.plnPerUnit {
                try database.execute(
                    "INSERT OR REPLACE INTO fx_rates (code, mid, effective_date) VALUES (?, ?, ?)",
                    [.text(code), .text("\(mid)"), .text(rates.effectiveDate)]
                )
            }
        }
    }
}
