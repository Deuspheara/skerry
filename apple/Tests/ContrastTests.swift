import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// Measured WCAG ratios of the palette's text and ground pairings: 4.5:1 for text under 18 pt.
final class ContrastTests: XCTestCase {
    private func rgb(_ hex: Int) -> NickContrast.RGB {
        NickContrast.RGB(r: Double((hex >> 16) & 0xFF) / 255, g: Double((hex >> 8) & 0xFF) / 255, b: Double(hex & 0xFF) / 255)
    }

    private func ratio(_ a: Int, _ b: Int) -> Double { NickContrast.contrastRatio(rgb(a), rgb(b)) }

    func testTextPairingsReachFourPointFive() {
        typealias H = Theme.Hex
        let pairs: [(String, Int, Int)] = [
            ("accent text on dark ground", H.accent, H.darkBackground),
            ("accent text on dark surface", H.accent, H.darkSurface),
            ("deep accent text on light ground", H.accentDeep, H.lightBackground),
            ("deep accent text on light surface", H.accentDeep, H.lightSurface),
            ("white on the deep accent (buttons)", 0xFFFFFF, H.accentDeep),
            ("secondary text on light ground", H.lightTextSecondary, H.lightBackground),
            ("secondary text on light surface", H.lightTextSecondary, H.lightSurface),
            ("secondary text on dark ground", H.darkTextSecondary, H.darkBackground),
            ("secondary text on dark surface", H.darkTextSecondary, H.darkSurface),
        ]
        for (name, foreground, background) in pairs {
            XCTAssertGreaterThanOrEqual(ratio(foreground, background), 4.5, name)
        }
    }

    func testTheLightBrandPurpleIsNotUsedForTextOnLight() {
        // The reason `Theme.accentText` exists: the lighter purple is about 3.1:1 on the light ground.
        XCTAssertLessThan(ratio(Theme.Hex.accent, Theme.Hex.lightBackground), 4.5)
    }
}
