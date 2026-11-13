import SwiftUI
import TwitchShared

/// A stream as a compact row: a 16:9 thumbnail beside the text, so six or seven streams fit on a phone screen. Titles are one line with an
/// ellipsis, so rows are level without reserving a second line.
struct StreamRow: View {
    let stream: LiveStream
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 12) {
                Thumbnail(url: stream.thumbnailUrl, badge: stream.viewers)
                    .frame(width: 148)
                VStack(alignment: .leading, spacing: 4) {
                    Text(stream.title).font(.subheadline.weight(.semibold)).lineLimit(1).truncationMode(.tail)
                    HStack(spacing: 6) {
                        Avatar(url: stream.avatarUrl, name: stream.name, size: 20)
                        Text(stream.name).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    }
                    Text(stream.categoryName).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    Spacer(minLength: 0)
                    HStack(spacing: 6) {
                        LiveBadge()
                        Text(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))
                            .font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minHeight: 148 * 9 / 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
        .accessibilityAddTraits(.isButton)
    }
}

struct Thumbnail: View {
    let url: String
    var badge: Int32?

    var body: some View {
        RemoteImage(url: URL(string: url))
            .aspectRatio(16 / 9, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
    }
}

struct LiveBadge: View {
    var body: some View {
        Text("live").font(.caption2.weight(.heavy)).textCase(.uppercase)
            .padding(.horizontal, 6).padding(.vertical, 2).fixedSize()
            .background(Theme.live, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
            .foregroundStyle(.white)
    }
}

/// A category poster (3:4) with its name on one line.
struct CategoryCard: View {
    let category: TwitchShared.Category
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                RemoteImage(url: URL(string: category.artUrl))
                    .aspectRatio(3 / 4, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
                Text(category.name).font(.footnote.weight(.semibold)).lineLimit(2).truncationMode(.tail)
            }
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(category.name)
    }
}

struct SectionTitle: View {
    let key: LocalizedStringKey

    var body: some View {
        Text(key).font(.title3.weight(.bold)).frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.isHeader)
    }
}

/// A message with an optional action: empty and error states.
/// The one empty / error state: a symbol, a title, one line and an action. Errors (`.failed`) say what to do; empty results (`.noResults`) say what to try.
struct MessageState: View {
    let title: LocalizedStringKey
    var message: LocalizedStringKey?
    var action: (title: LocalizedStringKey, run: () -> Void)?
    var icon: String?
    var secondary: (title: LocalizedStringKey, run: () -> Void)?

    /// Something went wrong loading: the symbol, the advice and a Retry that stands out.
    static func failed(retry: @escaping () -> Void) -> MessageState {
        MessageState(title: "state_error", message: "phone_error_body", action: (title: "state_retry", run: retry), icon: "wifi.exclamationmark")
    }

    /// A search with nothing to show: the hint, and a way out.
    static func noResults(clear: (() -> Void)? = nil) -> MessageState {
        MessageState(title: "state_no_result", message: "state_no_result_hint", action: clear.map { (title: "search_clear", run: $0) }, icon: "magnifyingglass")
    }

    var body: some View {
        VStack(spacing: 10) {
            if let icon {
                Image(systemName: icon).font(.system(size: 34, weight: .regular)).foregroundStyle(Theme.textSecondary).padding(.bottom, 4).accessibilityHidden(true)
            }
            Text(title).font(.headline).multilineTextAlignment(.center)
            if let message { Text(message).font(.subheadline).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center) }
            if let action {
                Button(action: action.run) { Text(action.title).font(.subheadline.weight(.semibold)).padding(.horizontal, 18).padding(.vertical, 9) }
                    .glassCapsule(interactive: true)
                    .buttonStyle(.plain)
                    .padding(.top, 6)
            }
            if let secondary {
                Button(action: secondary.run) { Text(secondary.title).font(.footnote.weight(.semibold)) }.buttonStyle(.plain).foregroundStyle(Theme.accentText)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Placeholder rows shown the very first time a list loads (nothing stored yet to show).
struct StreamRowSkeleton: View {
    @State private var pulse = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 10).fill(Theme.surfaceHigh).frame(width: 148, height: 148 * 9 / 16)
            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4).fill(Theme.surfaceHigh).frame(height: 14)
                RoundedRectangle(cornerRadius: 4).fill(Theme.surfaceHigh).frame(width: 90, height: 12)
                RoundedRectangle(cornerRadius: 4).fill(Theme.surfaceHigh).frame(width: 60, height: 12)
            }
        }
        .opacity(pulse ? 0.55 : 1)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
        .accessibilityHidden(true)
    }
}
