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
    var width: CGFloat = 236
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
            .frame(width: width, alignment: .leading)
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
