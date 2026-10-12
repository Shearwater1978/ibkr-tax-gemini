import XCTest
@testable import IBKRTaxAssistant

private final class FakeAuthenticator: DeviceOwnerAuthenticator {
    var available = true
    var succeeds = true
    private(set) var prompts = 0

    func canAuthenticate() -> Bool { available }

    func authenticate(reason: String) async -> Bool {
        prompts += 1
        return succeeds
    }
}

/// Same rule as the Android `AppLockPolicyTest`, plus the lock flow the Android `MainActivity` implements.
@MainActor
final class AppLockTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_791_547_200)
    private let policy = AppLockPolicy()

    func testStaysUnlockedWhileTheAppHasNotLeftTheForeground() {
        XCTAssertFalse(policy.shouldLock(backgroundedAt: nil, now: now))
    }

    func testShortAppSwitchesDoNotLock() {
        XCTAssertFalse(policy.shouldLock(backgroundedAt: now.addingTimeInterval(-299), now: now))
    }

    func testLocksAfterFiveMinutesInTheBackground() {
        XCTAssertTrue(policy.shouldLock(backgroundedAt: now.addingTimeInterval(-300), now: now))
        XCTAssertTrue(policy.shouldLock(backgroundedAt: now.addingTimeInterval(-3 * 3600), now: now))
    }

    func testStartsLockedAndPromptsOnce() async {
        let authenticator = FakeAuthenticator()
        let lock = AppLock(authenticator: authenticator)
        XCTAssertEqual(lock.state, .locked)

        await lock.didBecomeActive(now: now)
        XCTAssertEqual(lock.state, .unlocked)
        XCTAssertEqual(authenticator.prompts, 1)
    }

    func testCancelledPromptIsNotRepeatedAutomatically() async {
        let authenticator = FakeAuthenticator()
        authenticator.succeeds = false
        let lock = AppLock(authenticator: authenticator)

        await lock.didBecomeActive(now: now)
        await lock.didBecomeActive(now: now)
        XCTAssertEqual(lock.state, .locked)
        XCTAssertEqual(authenticator.prompts, 1)

        authenticator.succeeds = true
        await lock.authenticate()
        XCTAssertEqual(lock.state, .unlocked)
    }

    func testRelocksAfterTheTimeoutInTheBackgroundOnly() async {
        let authenticator = FakeAuthenticator()
        let lock = AppLock(authenticator: authenticator)
        await lock.didBecomeActive(now: now)

        lock.didEnterBackground(now: now)
        await lock.didBecomeActive(now: now.addingTimeInterval(299))
        XCTAssertEqual(authenticator.prompts, 1)

        lock.didEnterBackground(now: now.addingTimeInterval(400))
        authenticator.succeeds = false
        await lock.didBecomeActive(now: now.addingTimeInterval(700))
        XCTAssertEqual(lock.state, .locked)
        XCTAssertEqual(authenticator.prompts, 2)
    }

    func testDeviceWithoutPasscodeOffersToContinue() async {
        let authenticator = FakeAuthenticator()
        authenticator.available = false
        let lock = AppLock(authenticator: authenticator)

        await lock.didBecomeActive(now: now)
        XCTAssertEqual(lock.state, .noScreenLock)
        XCTAssertEqual(authenticator.prompts, 0)

        lock.continueWithoutLock()
        XCTAssertEqual(lock.state, .unlocked)
    }
}

final class TempFilesTests: XCTestCase {
    func testClearEmptiesFoldersButKeepsThem() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        try FileManager.default.createDirectory(at: folder.appendingPathComponent("nested"), withIntermediateDirectories: true)
        try Data("synthetic".utf8).write(to: folder.appendingPathComponent("leftover.csv"))

        TempFiles.clear([folder])
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: folder.path), [])
    }
}

/**
 * The app writes nothing to the system log, so reports, identifiers, amounts and keys cannot end
 * up there (mobile-encrypted-drive-backup task 6.3), as the Android `NoLoggingTest` checks.
 */
final class NoLoggingTests: XCTestCase {
    private let forbidden = [#"\bprint\("#, #"\bdebugPrint\("#, #"\bdump\("#, #"\bNSLog\("#, #"\bos_log\("#, #"\bLogger\("#, #"\bimport os\b"#]
        .map { try! NSRegularExpression(pattern: $0) }

    func testAppCodeDoesNotWriteToTheSystemLog() throws {
        let sources = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("IBKRTaxAssistant")
        let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil)?
            .compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" } ?? []
        XCTAssertFalse(files.isEmpty, "sources not found at \(sources.path)")

        var findings: [String] = []
        for file in files {
            for (index, line) in try String(contentsOf: file, encoding: .utf8).components(separatedBy: "\n").enumerated() {
                let code = line.components(separatedBy: "//")[0]
                let range = NSRange(code.startIndex..., in: code)
                if forbidden.contains(where: { $0.firstMatch(in: code, range: range) != nil }) {
                    findings.append("\(file.lastPathComponent):\(index + 1): \(line.trimmingCharacters(in: .whitespaces))")
                }
            }
        }
        XCTAssertEqual(findings, [], "logging calls found")
    }
}
