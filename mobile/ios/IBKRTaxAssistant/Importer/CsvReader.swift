/// A parsed CSV record and the 1-based line it starts on.
struct CsvRow: Equatable {
    let line: Int
    let fields: [String]
}

struct UnterminatedQuoteError: Error {
    let line: Int
}

/**
 * Minimal reader matching Python's default `csv.reader` dialect over a file opened in text
 * mode: universal newlines, comma delimiter, `"` quoting with `""` escapes, quoted fields may
 * span lines, and a blank line yields an empty record. Port of the Android `CsvReader`; it works
 * on Unicode scalars because Swift treats "\r\n" as one character.
 */
enum CsvReader {
    static func read(_ input: String) throws -> [CsvRow] {
        let text = Array(input.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").unicodeScalars)
        var rows: [CsvRow] = []
        var fields: [String] = []
        var field = String.UnicodeScalarView()
        var inQuotes = false
        var atFieldStart = true
        var rowStarted = false
        var line = 1
        var rowLine = 1

        var i = 0
        while i < text.count {
            let c = text[i]
            if inQuotes {
                if c == "\"" && i + 1 < text.count && text[i + 1] == "\"" {
                    field.append("\"")
                    i += 1
                } else if c == "\"" {
                    inQuotes = false
                } else {
                    if c == "\n" { line += 1 }
                    field.append(c)
                }
            } else {
                switch c {
                case "\"":
                    if atFieldStart { inQuotes = true } else { field.append(c) }
                    atFieldStart = false
                    rowStarted = true
                case ",":
                    fields.append(String(field))
                    field = String.UnicodeScalarView()
                    atFieldStart = true
                    rowStarted = true
                case "\n":
                    if rowStarted { fields.append(String(field)) }
                    rows.append(CsvRow(line: rowLine, fields: fields))
                    fields = []
                    field = String.UnicodeScalarView()
                    atFieldStart = true
                    rowStarted = false
                    line += 1
                    rowLine = line
                default:
                    field.append(c)
                    atFieldStart = false
                    rowStarted = true
                }
            }
            i += 1
        }
        if inQuotes { throw UnterminatedQuoteError(line: rowLine) }
        if rowStarted {
            fields.append(String(field))
            rows.append(CsvRow(line: rowLine, fields: fields))
        }
        return rows
    }
}
