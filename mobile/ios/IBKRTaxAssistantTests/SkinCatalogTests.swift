import XCTest
@testable import IBKRTaxAssistant

/// Mirrors the Android `SkinCatalogTest` and the broker part of `BrokerAndFolderTest`.
final class SkinCatalogTests: XCTestCase {
    func testEverySkinSaysWhereEveryFeatureLives() {
        // mobile-skins "Keep feature parity": a skin may hide features behind taps, never drop them.
        for skin in SkinCatalog.skins {
            let missing = Feature.allCases.filter { (skin.features[$0] ?? "").isEmpty }
            XCTAssertEqual(missing, [], "\(skin.id) misses \(missing)")
        }
    }

    func testCatalogHasUniqueIdsAndTheAgreedDefaults() {
        XCTAssertEqual(Set(SkinCatalog.skins.map(\.id)).count, SkinCatalog.skins.count)
        XCTAssertEqual(SkinCatalog.default.id, .classic)
        // Android preselects Calm; until it is ported here, the only skin is preselected.
        XCTAssertEqual(SkinCatalog.firstLaunch.id, SkinCatalog.skins.contains { $0.id == .calm } ? .calm : .classic)
    }

    func testStoredNamesResolveAndUnknownOnesFallBackToClassic() {
        XCTAssertEqual(SkinCatalog.resolve("CLASSIC").id, .classic)
        XCTAssertEqual(SkinCatalog.resolve("classic").id, .classic) // falls back; names are stored exactly
        XCTAssertEqual(SkinCatalog.resolve("NEON").id, .classic)
        XCTAssertEqual(SkinCatalog.resolve(nil).id, .classic)
        XCTAssertEqual(SkinId.allCases.map(\.rawValue), ["CLASSIC", "CALM", "BENTO", "FOCUS"], "same stored names as Android")
    }

    func testStoredBrokerNamesResolveExactly() {
        XCTAssertEqual(BrokerId.resolve("INTERACTIVE_BROKERS"), .interactiveBrokers)
        XCTAssertNil(BrokerId.resolve(nil)) // not chosen yet
        XCTAssertNil(BrokerId.resolve("DEGIRO")) // not supported (yet)
    }
}
