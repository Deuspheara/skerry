import AppKit
import SwiftUI
import TwitchShared

/// The press and hover feel of the Mac cards: a small lift under the pointer, a press that dips, nothing when Reduce Motion is on.
struct MacCardStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var hovering: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(reduceMotion ? 1 : (configuration.isPressed ? 0.98 : (hovering ? 1.015 : 1)))
            .animation(reduceMotion ? nil : .spring(response: 0.25, dampingFraction: 0.8), value: hovering)
            .animation(reduceMotion ? nil : .spring(response: 0.2, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

let streamGridColumns = [GridItem(.adaptive(minimum: 250, maximum: 340), spacing: 18, alignment: .top)]
let categoryGridColumns = [GridItem(.adaptive(minimum: 136, maximum: 180), spacing: 18, alignment: .top)]

/// A live stream as a card: a 16:9 picture with the LIVE badge and viewers, the streamer's avatar, one-line title, name and category.
/// Hover shows a play glyph; the context menu opens the stream, the channel page, twitch.tv, or copies the link.
struct StreamCard: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    let stream: LiveStream
    @State private var hovering = false

    var body: some View {
        Button { app.open(stream) } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .topLeading) {
                    Thumbnail(url: stream.thumbnailUrl)
                    HStack(spacing: 6) {
                        LiveBadge()
                        Text(formatViewers(stream.viewers)).font(.caption.weight(.semibold)).foregroundStyle(.white)
                    }
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(.black.opacity(0.55), in: Capsule())
                    .padding(8)
                }
                .overlay {
                    Image(systemName: "play.fill").font(.title).foregroundStyle(.white).frame(width: 58, height: 58)
                        .background(.black.opacity(0.5), in: Circle())
                        .opacity(hovering ? 1 : 0)
                }
                .animation(.easeOut(duration: 0.15), value: hovering)
                HStack(alignment: .top, spacing: 10) {
                    Avatar(url: stream.avatarUrl, name: stream.name, size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stream.title).font(.subheadline.weight(.semibold)).lineLimit(1).truncationMode(.tail)
                        Text(stream.name).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                        Text(stream.categoryName).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(MacCardStyle(hovering: hovering))
        .onHover { hovering = $0 }
        .contextMenu {
            Button("mac_open_stream") { app.open(stream) }
            Button("mac_open_channel") { app.push(.channel(login: stream.login)) }
            Divider()
            Button("mac_open_twitch") { if let url = URL(string: "https://www.twitch.tv/\(stream.login)") { openURL(url) } }
            Button("mac_copy_link") {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString("https://www.twitch.tv/\(stream.login)", forType: .string)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("stream-\(stream.login)")
    }
}

/// A category poster (3:4) with its name on one line.
struct CategoryTile: View {
    @Environment(AppModel.self) private var app
    let category: TwitchShared.Category
    @State private var hovering = false

    var body: some View {
        Button { app.push(.category(id: category.id, name: category.name)) } label: {
            VStack(alignment: .leading, spacing: 8) {
                RemoteImage(url: URL(string: category.artUrl))
                    .aspectRatio(3 / 4, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(hovering ? Theme.accent : Theme.hairline, lineWidth: hovering ? 2 : 0.5))
                Text(category.name).font(.subheadline.weight(.semibold)).lineLimit(1).truncationMode(.tail)
            }
        }
        .buttonStyle(MacCardStyle(hovering: hovering))
        .onHover { hovering = $0 }
        .accessibilityLabel(category.name)
    }
}

/// The scrolling page every section uses: a centred column with room for a wide window, and a stale banner when a refresh failed.
struct MacPage<Content: View>: View {
    var banner: (loadedAtMs: Int64, retry: () -> Void)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if let banner { StaleBanner(loadedAtMs: banner.loadedAtMs, retry: banner.retry) }
                content()
            }
            .padding(24)
            .frame(maxWidth: 1600, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.automatic)
    }
}

/// Placeholder cards the very first time a list loads.
struct MacSkeletonGrid: View {
    var body: some View {
        ScrollView {
            LazyVGrid(columns: streamGridColumns, spacing: 22) {
                ForEach(0..<9, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: 10) {
                        RoundedRectangle(cornerRadius: 10).fill(Theme.surfaceHigh).aspectRatio(16 / 9, contentMode: .fit)
                        RoundedRectangle(cornerRadius: 4).fill(Theme.surfaceHigh).frame(height: 14)
                        RoundedRectangle(cornerRadius: 4).fill(Theme.surfaceHigh).frame(width: 120, height: 12)
                    }
                }
            }
            .padding(24)
        }
        .accessibilityHidden(true)
    }
}
