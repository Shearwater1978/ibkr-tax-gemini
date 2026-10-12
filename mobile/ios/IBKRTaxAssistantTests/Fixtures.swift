import Foundation

/// Loads shared synthetic fixtures from mobile/fixtures (copied into the test bundle as a folder).
enum Fixtures {
    static let validBasic = "valid_basic.csv"
    static let validFollowup = "valid_followup.csv"
    static let unsupportedFormat = "unsupported_format.csv"
    static let malformedTrades = "malformed_trades.csv"
    static let empty = "empty.csv"

    static func flexQuery(_ name: String) -> Data { read("flex-query/\(name)") }

    /// Records the Python reference parser produces for a valid fixture.
    static func expectedJson(_ fixtureName: String) -> Any {
        try! JSONSerialization.jsonObject(with: read("flex-query/expected/\(stem(fixtureName)).json"))
    }

    /// Source keys Python `_source_key` produces for a valid fixture, in import order.
    static func expectedSourceKeys(_ fixtureName: String) -> [String] {
        try! JSONSerialization.jsonObject(with: read("flex-query/expected/source_keys/\(stem(fixtureName)).json")) as! [String]
    }

    private static func stem(_ name: String) -> String { (name as NSString).deletingPathExtension }

    private static func read(_ path: String) -> Data {
        let url = Bundle(for: Marker.self).resourceURL!.appendingPathComponent("fixtures").appendingPathComponent(path)
        guard let data = try? Data(contentsOf: url) else { preconditionFailure("Missing test fixture: \(path)") }
        return data
    }

    private final class Marker {}
}
