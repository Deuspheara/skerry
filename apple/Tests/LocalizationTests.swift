import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// Every localisation key the Swift code uses must exist in the catalog (generated from the Android resources), and the plural strings must resolve.
final class LocalizationTests: XCTestCase {
    /// snake_case words that are identifiers, not keys.
    private let notKeys: Set<String> = ["demo_stream", "audio_only"]

    private var sources: URL { URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent() }

    private func english() throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"), "the app has no English localisation")
        return try XCTUnwrap(Bundle(path: path))
    }

    func testEveryKeyUsedInTheSwiftSourcesIsInTheCatalog() throws {
        let english = try english()
        let regex = try NSRegularExpression(pattern: #""([a-z][a-z0-9]*(?:_[a-z0-9]+)+)""#)
        var used = Set<String>()
        for folder in ["Shared", "iOS", "Mac", "TV"] {
            let enumerator = FileManager.default.enumerator(at: sources.appendingPathComponent(folder), includingPropertiesForKeys: nil)
            while let url = enumerator?.nextObject() as? URL {
                guard url.pathExtension == "swift" else { continue }
                let text = try String(contentsOf: url, encoding: .utf8)
                for match in regex.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
                    used.insert(String(text[Range(match.range(at: 1), in: text)!]))
                }
            }
        }
        XCTAssertGreaterThan(used.count, 80, "the scan found no keys: the source path is wrong (\(sources.path))")
        let missing = used.subtracting(notKeys).filter { english.localizedString(forKey: $0, value: "∅", table: nil) == "∅" }.sorted()
        XCTAssertEqual(missing, [], "keys used in Swift but missing from Localizable.xcstrings")
    }

    func testTheSleepTimerPluralPicksTheRightForm() throws {
        let english = try english()
        XCTAssertEqual(plural("phone_player_sleep_timer_minutes", 1, bundle: english), "1 minute")
        XCTAssertEqual(plural("phone_player_sleep_timer_minutes", 45, bundle: english), "45 minutes")
        XCTAssertEqual(plural("chat_new_messages", 1, bundle: english), "1 new message")
        XCTAssertEqual(plural("chat_new_messages", 3, bundle: english), "3 new messages")
    }

    func testFrenchAndArabicExistForTheAppleOnlyAndPhoneOnlyStrings() throws {
        for language in ["fr", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), "no \(language) localisation")
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["apple_licenses_body", "phone_search_hint", "phone_player_chat_hint", "phone_settings_playback"] {
                XCTAssertNotEqual(bundle.localizedString(forKey: key, value: "∅", table: nil), "∅", "\(key) is not translated to \(language)")
            }
        }
    }
}
