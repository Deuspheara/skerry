import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

final class NickContrastTests: XCTestCase {
    private let page = NickContrast.RGB(r: 0xFA / 255, g: 0xF9 / 255, b: 0xFC / 255)

    func testBlackOnWhiteIsTwentyOne() {
        XCTAssertEqual(NickContrast.contrastRatio(.black, .white), 21, accuracy: 0.001)
    }

    func testReadableColourIsReturnedUntouched() {
        let navy = NickContrast.RGB(r: 0.05, g: 0.1, b: 0.4)
        XCTAssertEqual(NickContrast.readable(on: page, navy), navy)
    }

    func testPastelOnLightIsDarkenedToTheMinimumRatio() {
        let pastel = NickContrast.RGB(r: 1, g: 0.95, b: 0.6)
        let fixed = NickContrast.readable(on: page, pastel)
        XCTAssertGreaterThanOrEqual(NickContrast.contrastRatio(fixed, page), NickContrast.minRatio)
        XCTAssertLessThan(fixed.luminance, pastel.luminance)
    }

    func testDarkColourOnDarkIsLightened() {
        let dark = NickContrast.RGB(r: 0.05, g: 0.05, b: 0.1)
        let background = NickContrast.RGB(r: 0.055, g: 0.055, b: 0.063)
        let fixed = NickContrast.readable(on: background, dark)
        XCTAssertGreaterThanOrEqual(NickContrast.contrastRatio(fixed, background), NickContrast.minRatio)
        XCTAssertGreaterThan(fixed.luminance, dark.luminance)
    }
}
