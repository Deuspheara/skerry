import SwiftUI

/// The app's palette (the same purple and near-black as the Android phone app) and the one place that knows about Liquid Glass.
enum Theme {
    static let accent = Color(red: 0xA9 / 255, green: 0x70 / 255, blue: 0xFF / 255)
    static let accentDeep = Color(red: 0x77 / 255, green: 0x2C / 255, blue: 0xE8 / 255)
    static let live = Color(red: 0xEB / 255, green: 0x04 / 255, blue: 0x00 / 255)
    static let background = Color(light: Color(red: 0xFA / 255, green: 0xF9 / 255, blue: 0xFC / 255), dark: Color(red: 0x0E / 255, green: 0x0E / 255, blue: 0x10 / 255))
    static let surface = Color(light: Color(red: 0xEE / 255, green: 0xEC / 255, blue: 0xF3 / 255), dark: Color(red: 0x1F / 255, green: 0x1F / 255, blue: 0x23 / 255))
    static let surfaceHigh = Color(light: Color(red: 0xE2 / 255, green: 0xE0 / 255, blue: 0xE9 / 255), dark: Color(red: 0x30 / 255, green: 0x30 / 255, blue: 0x32 / 255))
    static let textSecondary = Color(light: Color(red: 0x4A / 255, green: 0x4A / 255, blue: 0x53 / 255), dark: Color(red: 0xC8 / 255, green: 0xC8 / 255, blue: 0xD0 / 255))
    static let hairline = Color(light: Color.black.opacity(0.08), dark: Color.white.opacity(0.10))
}

extension Color {
    /// A colour that follows the interface style (the app supports system, light and dark).
    init(light: Color, dark: Color) {
        #if canImport(UIKit)
        self = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light) })
        #elseif canImport(AppKit)
        self = Color(NSColor(name: nil) { $0.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? NSColor(dark) : NSColor(light) })
        #endif
    }
}

// MARK: - Liquid Glass

/// Liquid Glass on iOS/macOS/tvOS 26 and later, a material below. Every other file asks for `glass(...)`, never for `#available`.
extension View {
    @ViewBuilder
    func glass<S: Shape>(in shape: S, interactive: Bool = false, tint: Color? = nil) -> some View {
        if #available(iOS 26, macOS 26, tvOS 26, *) {
            self.glassEffect(Self.glass(interactive: interactive, tint: tint), in: shape)
        } else {
            self.background(.ultraThinMaterial, in: shape)
                .overlay(shape.stroke(Theme.hairline, lineWidth: 0.5))
        }
    }

    @ViewBuilder
    func glassCapsule(interactive: Bool = false, tint: Color? = nil) -> some View {
        glass(in: Capsule(), interactive: interactive, tint: tint)
    }

    @available(iOS 26, macOS 26, tvOS 26, *)
    private static func glass(interactive: Bool, tint: Color?) -> Glass {
        var glass = Glass.regular
        if let tint { glass = glass.tint(tint) }
        if interactive { glass = glass.interactive() }
        return glass
    }
}

/// Groups glass elements so they blend and morph together on 26; a plain container before.
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder var content: () -> Content

    var body: some View {
        if #available(iOS 26, macOS 26, tvOS 26, *) {
            GlassEffectContainer(spacing: spacing, content: content)
        } else {
            content()
        }
    }
}

/// A press that scales the content down a little: the iOS counterpart of the Android card press.
struct PressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}
