import SwiftUI

/// A viewer's nickname colour is already adjusted to read on a dark chat. On a light surface a pastel can be almost invisible, so it is moved towards
/// black, as little as needed, until it reaches the contrast the text needs (the same rule as the Android phone's `readableOn`).
enum NickContrast {
    struct RGB: Equatable {
        var r: Double
        var g: Double
        var b: Double

        /// WCAG relative luminance.
        var luminance: Double {
            func channel(_ value: Double) -> Double { value <= 0.03928 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4) }
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
        }

        func mixed(with other: RGB, _ t: Double) -> RGB {
            RGB(r: r + (other.r - r) * t, g: g + (other.g - g) * t, b: b + (other.b - b) * t)
        }

        static let black = RGB(r: 0, g: 0, b: 0)
        static let white = RGB(r: 1, g: 1, b: 1)
    }

    static let minRatio = 4.5
    private static let steps = 20

    static func contrastRatio(_ a: RGB, _ b: RGB) -> Double {
        (max(a.luminance, b.luminance) + 0.05) / (min(a.luminance, b.luminance) + 0.05)
    }

    static func readable(on background: RGB, _ color: RGB, minRatio: Double = minRatio) -> RGB {
        if contrastRatio(color, background) >= minRatio { return color }
        let target: RGB = background.luminance > 0.5 ? .black : .white
        for step in 1...steps {
            let candidate = color.mixed(with: target, Double(step) / Double(steps))
            if contrastRatio(candidate, background) >= minRatio { return candidate }
        }
        return target
    }

    /// The colour of a nickname on the app's own chat background in the given appearance (the light one is `Theme.background`'s).
    static func nick(_ argb: Int64, light: Bool) -> Color {
        let color = RGB(r: Double((argb >> 16) & 0xFF) / 255, g: Double((argb >> 8) & 0xFF) / 255, b: Double(argb & 0xFF) / 255)
        guard light else { return Color(red: color.r, green: color.g, blue: color.b) }
        let page = RGB(r: 0xFA / 255, g: 0xF9 / 255, b: 0xFC / 255)
        let adjusted = readable(on: page, color)
        return Color(red: adjusted.r, green: adjusted.g, blue: adjusted.b)
    }
}
