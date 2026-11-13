import SwiftUI
import TwitchShared

/// The big card at the top of Home: the picture across the page with the stream's name, channel and audience over a gradient.
struct HeroStreamCard: View {
    let stream: LiveStream
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            RemoteImage(url: URL(string: stream.thumbnailUrl))
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay(alignment: .bottomLeading) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            LiveBadge()
                            ViewersPill(count: stream.viewers)
                        }
                        Text(stream.title).font(.headline).lineLimit(2).multilineTextAlignment(.leading)
                        HStack(spacing: 8) {
                            Avatar(url: stream.avatarUrl, name: stream.name, size: 24)
                            Text("\(stream.name) · \(stream.categoryName)").font(.footnote).lineLimit(1)
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.78)], startPoint: .top, endPoint: .bottom))
                }
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
        .accessibilityAddTraits(.isButton)
    }
}

/// Viewers over a picture: a dark pill that stays readable on any frame.
struct ViewersPill: View {
    let count: Int32

    var body: some View {
        Label(formatViewers(count), systemImage: "eye.fill")
            .font(.caption2.weight(.semibold)).foregroundStyle(.white)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(.black.opacity(0.62), in: Capsule())
    }
}

/// A stream in a horizontal rail: the picture with its audience, two lines of title, the channel.
struct StreamTile: View {
    let stream: LiveStream
    var width: CGFloat? = 236
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                RemoteImage(url: URL(string: stream.thumbnailUrl))
                    .aspectRatio(16 / 9, contentMode: .fit)
                    .overlay(alignment: .bottomLeading) { ViewersPill(count: stream.viewers).padding(6) }
                    .overlay(alignment: .topLeading) { LiveBadge().padding(6) }
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
                Text(stream.title).font(.subheadline.weight(.semibold)).lineLimit(2).multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                Text(stream.name).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            .frame(width: width, alignment: .leading).frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
        .accessibilityAddTraits(.isButton)
    }
}

/// A full-width card: picture with its audience, then the channel's avatar, the title on two lines and "channel · category".
struct StreamCardWide: View {
    let stream: LiveStream
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                RemoteImage(url: URL(string: stream.thumbnailUrl))
                    .aspectRatio(16 / 9, contentMode: .fit)
                    .overlay(alignment: .bottomLeading) { ViewersPill(count: stream.viewers).padding(8) }
                    .overlay(alignment: .topLeading) { LiveBadge().padding(8) }
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
                HStack(alignment: .top, spacing: 10) {
                    Avatar(url: stream.avatarUrl, name: stream.name, size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stream.title).font(.subheadline.weight(.semibold)).lineLimit(2).multilineTextAlignment(.leading)
                        Text("\(stream.name) · \(stream.categoryName)").font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
        .accessibilityAddTraits(.isButton)
    }
}

/// Who is live among the channels you follow: avatars with the live ring and the name under, one tap to watch.
struct LiveAvatarRail: View {
    let streams: [LiveStream]
    var onTap: (LiveStream) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 14) {
                ForEach(streams, id: \.channelId) { stream in
                    Button { onTap(stream) } label: {
                        VStack(spacing: 6) {
                            Avatar(url: stream.avatarUrl, name: stream.name, size: 58)
                                .padding(3).overlay(Circle().stroke(Theme.live, lineWidth: 2.5))
                            Text(stream.name).font(.caption).lineLimit(1).frame(width: 72)
                        }
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(Text("\(stream.name), \(stream.title)"))
                }
            }
            .padding(.vertical, 2)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }
}

/// The top of a category page: the box art, the name, the audience of everything live in it, its most used tags and a button to keep it.
struct CategoryHeader: View {
    let name: String
    let artUrl: String?
    let streams: [LiveStream]
    let favorite: Bool
    var onToggleFavorite: () -> Void

    private var viewers: Int32 { streams.reduce(0) { $0 + $1.viewers } }

    private var tags: [String] {
        var counts: [String: Int] = [:]
        for tag in streams.flatMap({ $0.tags }) { counts[tag, default: 0] += 1 }
        return counts.sorted { $0.value == $1.value ? $0.key < $1.key : $0.value > $1.value }.prefix(4).map(\.key)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Group {
                if let artUrl, !artUrl.isEmpty {
                    RemoteImage(url: URL(string: artUrl)).aspectRatio(3 / 4, contentMode: .fill)
                } else {
                    Theme.surface.overlay(Image(systemName: "gamecontroller.fill").font(.title).foregroundStyle(Theme.textSecondary))
                }
            }
            .frame(width: 84, height: 112)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(Theme.hairline, lineWidth: 0.5))
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text(name).font(.title2.weight(.bold)).lineLimit(2)
                Text(String(format: String(localized: "category_summary"), formatViewers(viewers), streams.count)).font(.subheadline).foregroundStyle(Theme.textSecondary)
                if !tags.isEmpty {
                    FlowLayout(spacing: 6, lineSpacing: 6) {
                        ForEach(tags, id: \.self) { tag in
                            Text(tag).font(.caption2.weight(.medium)).foregroundStyle(Theme.textSecondary).fixedSize()
                                .padding(.horizontal, 8).padding(.vertical, 3).background(Theme.surface, in: Capsule())
                        }
                    }
                }
                Button(action: onToggleFavorite) {
                    Label(favorite ? "category_following" : "category_follow", systemImage: favorite ? "heart.fill" : "heart")
                        .font(.footnote.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 7)
                }
                .glassCapsule(interactive: true).buttonStyle(.plain)
            }
            Spacer(minLength: 0)
        }
        .padding(.bottom, 4)
    }
}

/// The top of a channel page: a backdrop made of the avatar, the avatar over it with the live ring, the name and login, the live state, an honest
/// link to follow on twitch.tv (the viewer token cannot follow) and what the page holds.
struct ChannelHeader: View {
    let channel: ChannelRef
    let stream: LiveStream?
    let isFollowed: Bool
    let replays: Int
    let clips: Int
    var onOpenTwitch: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ZStack(alignment: .bottomLeading) {
                RemoteImage(url: channel.avatarUrl.flatMap { URL(string: $0) })
                    .scaledToFill().blur(radius: 28).saturation(1.4)
                    .frame(height: 112).clipped()
                    .overlay(LinearGradient(colors: [.clear, Theme.background.opacity(0.9)], startPoint: .top, endPoint: .bottom))
                    .accessibilityHidden(true)
                HStack(alignment: .bottom, spacing: 14) {
                    Avatar(url: channel.avatarUrl, name: channel.name, size: 84)
                        .overlay(Circle().stroke(stream != nil ? Theme.live : Theme.background, lineWidth: 3).padding(-4))
                        .padding(4)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(channel.name).font(.title2.weight(.bold)).lineLimit(1)
                        Text("@\(channel.login)").font(.footnote).foregroundStyle(Theme.textSecondary)
                    }
                    .padding(.bottom, 6)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 4).offset(y: 34)
            }
            .padding(.bottom, 34)
            FlowLayout(spacing: 8, lineSpacing: 8) {
                if let stream {
                    Label(String(format: String(localized: "channel_live_now"), formatViewers(stream.viewers)), systemImage: "dot.radiowaves.left.and.right")
                        .font(.caption.weight(.semibold)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(Theme.live, in: Capsule())
                } else {
                    Label("phone_channel_offline", systemImage: "moon.zzz").font(.caption.weight(.medium)).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(Theme.surface, in: Capsule())
                }
                if isFollowed {
                    Label("channel_followed_badge", systemImage: "heart.fill").font(.caption.weight(.medium)).foregroundStyle(Theme.accentText).fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(Theme.surface, in: Capsule())
                }
                if replays > 0 {
                    Label("\(replays) replays", systemImage: "play.rectangle").font(.caption.weight(.medium)).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(Theme.surface, in: Capsule())
                }
                if clips > 0 {
                    Label("\(clips) clips", systemImage: "scissors").font(.caption.weight(.medium)).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(Theme.surface, in: Capsule())
                }
            }
            Button(action: onOpenTwitch) {
                Label("channel_open_twitch", systemImage: "arrow.up.right").font(.footnote.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
            }
            .glassCapsule(interactive: true).buttonStyle(.plain)
            .accessibilityHint(Text("phone_follow_opens_twitch"))
        }
    }
}

/// Streams as a grid of cards, two across on a phone (more on wider screens).
struct StreamGrid: View {
    let streams: [LiveStream]
    var onTap: (LiveStream) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 12, alignment: .top)], alignment: .leading, spacing: 16) {
            ForEach(streams, id: \.channelId) { stream in StreamTile(stream: stream, width: nil) { onTap(stream) } }
        }
    }
}
