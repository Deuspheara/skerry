import SwiftUI
import TwitchShared

/// A banner shown when a refresh failed and what is on screen is a stored or older copy.
struct StaleBanner: View {
    let loadedAtMs: Int64
    var retry: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.arrow.triangle.2.circlepath")
            Text(loadedAtMs > 0 && Date().timeIntervalSince1970 * 1000 - Double(loadedAtMs) > 180_000
                ? String(format: String(localized: "phone_stale_ago"), relativeTime(fromMs: loadedAtMs))
                : String(localized: "phone_stale_failed"))
                .font(.footnote).lineLimit(2)
            Spacer(minLength: 0)
            Button("state_retry", action: retry).font(.footnote.weight(.semibold))
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .glass(in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }
}

struct FilterChip: View {
    let title: LocalizedStringKey
    let selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
                .foregroundStyle(selected ? Color.white : Color.primary)
                .background(selected ? AnyShapeStyle(Theme.accentDeep) : AnyShapeStyle(Theme.surface), in: Capsule())
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

/// One line with an ellipsis; if it does not fit it scrolls slowly (never with Reduce Motion).
struct MarqueeText: View {
    let text: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var textWidth: CGFloat = 0
    @State private var containerWidth: CGFloat = 0
    @State private var offset: CGFloat = 0

    var body: some View {
        let overflow = textWidth - containerWidth
        Text(text).lineLimit(1).fixedSize().hidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Text(text).lineLimit(1).fixedSize()
                        .background(GeometryReader { Color.clear.preference(key: WidthKey.self, value: $0.size.width) })
                        .offset(x: offset)
                        .frame(width: proxy.size.width, alignment: .leading)
                        .onAppear { containerWidth = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, width in containerWidth = width }
                }
                .clipped()
            }
            .onPreferenceChange(WidthKey.self) { textWidth = $0 }
            .task(id: overflow) {
                offset = 0
                guard overflow > 1, !reduceMotion else { return }
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(1.2))
                    withAnimation(.linear(duration: Double(overflow) / 28)) { offset = -overflow }
                    try? await Task.sleep(for: .seconds(Double(overflow) / 28 + 1.2))
                    withAnimation(.easeOut(duration: 0.4)) { offset = 0 }
                    try? await Task.sleep(for: .seconds(0.6))
                }
            }
            .accessibilityLabel(text)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct WidthKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
