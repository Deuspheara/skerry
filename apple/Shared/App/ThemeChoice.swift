import SwiftUI

/// The appearance the viewer picked in Settings (system by default).
enum ThemeChoice: String {
    case system, light, dark
    static let key = "themeChoice"
    static var current: ThemeChoice { ThemeChoice(rawValue: UserDefaults.standard.string(forKey: key) ?? "") ?? .system }
    var scheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
