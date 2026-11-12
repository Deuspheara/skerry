import Foundation

/// "14.8K viewers" style counts, the same breaks as the Android `formatViewers`.
func formatViewers(_ count: Int32) -> String {
    let n = Double(count)
    switch count {
    case ..<1_000: return "\(count)"
    case ..<10_000: return trim(n / 1_000) + "K"
    case ..<1_000_000: return String(format: "%.0fK", n / 1_000)
    default: return trim(n / 1_000_000) + "M"
    }
}

private func trim(_ value: Double) -> String {
    let text = String(format: "%.1f", value)
    return text.hasSuffix(".0") ? String(text.dropLast(2)) : text
}

/// "2:05:18" from seconds.
func formatDuration(seconds: Int) -> String {
    let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
}

/// Uptime from a start instant in epoch milliseconds, "1:32:07".
func uptime(since startedAtMs: Int64, now: Date = Date()) -> String? {
    guard startedAtMs > 0 else { return nil }
    return formatDuration(seconds: max(0, Int(now.timeIntervalSince1970 - Double(startedAtMs) / 1000)))
}

/// "5 min ago" in the user's language.
func relativeTime(fromMs ms: Int64, now: Date = Date()) -> String {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .short
    return formatter.localizedString(for: Date(timeIntervalSince1970: Double(ms) / 1000), relativeTo: now)
}
