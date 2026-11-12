import SwiftUI
import TwitchShared

/// The ten-foot sizes: a card is 400 pt wide (about six per screen with the safe area), rows start at the title-safe edge.
enum TVMetrics {
    static let cardWidth: CGFloat = 400
    static let posterWidth: CGFloat = 220
    static let edge: CGFloat = 80
    static let rowSpacing: CGFloat = 40
}

/// A horizontal row with a title. Each row is its own focus section, so moving down lands on the nearest card of the next row.
struct TVRow<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(title).font(.title3.weight(.bold)).padding(.horizontal, TVMetrics.edge).accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: TVMetrics.rowSpacing) { content() }
                    .padding(.horizontal, TVMetrics.edge).padding(.vertical, 26)
            }
            .scrollClipDisabled()
            .scrollIndicators(.hidden)
        }
        .focusSection()
    }
}

struct TVLiveBadge: View {
    var body: some View {
        Text("live").font(.system(size: 20, weight: .heavy)).textCase(.uppercase)
            .padding(.horizontal, 10).padding(.vertical, 3)
            .background(Theme.live, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            .foregroundStyle(.white)
    }
}

/// A live stream: 16:9 picture with the LIVE badge and viewers, the streamer's avatar, one-line title, name and category. The system card style gives the
/// focus lift, the highlight and the parallax feel.
struct TVStreamCard: View {
    let stream: LiveStream
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 14) {
                ZStack(alignment: .topLeading) {
                    Thumbnail(url: stream.thumbnailUrl)
                    HStack(spacing: 8) {
                        TVLiveBadge()
                        Text(formatViewers(stream.viewers)).font(.callout.weight(.semibold)).foregroundStyle(.white)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 6).background(.black.opacity(0.55), in: Capsule()).padding(12)
                }
                HStack(alignment: .top, spacing: 14) {
                    Avatar(url: stream.avatarUrl, name: stream.name, size: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(stream.title).font(.callout.weight(.semibold)).lineLimit(1)
                        Text(stream.name).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                        Text(stream.categoryName).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 6).padding(.bottom, 8)
            }
            .frame(width: TVMetrics.cardWidth)
        }
        .buttonStyle(.card)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.name), \(stream.title), \(stream.categoryName), \(String(format: String(localized: "phone_search_viewers"), formatViewers(stream.viewers)))")
    }
}

/// A category poster (3:4) with its name on one line.
struct TVCategoryCard: View {
    let category: TwitchShared.Category
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                RemoteImage(url: URL(string: category.artUrl))
                    .aspectRatio(3 / 4, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                Text(category.name).font(.callout.weight(.semibold)).lineLimit(1).padding(.horizontal, 6).padding(.bottom, 8)
            }
            .frame(width: TVMetrics.posterWidth)
        }
        .buttonStyle(.card)
        .accessibilityLabel(category.name)
    }
}

/// A channel avatar with its name, for offline channels and search results.
struct TVChannelCard: View {
    let channel: ChannelRef
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 12) {
                Avatar(url: channel.avatarUrl, name: channel.name, size: 140)
                    .overlay(Circle().stroke(Theme.live, lineWidth: channel.isLive ? 4 : 0).padding(-6))
                    .opacity(channel.isLive ? 1 : 0.7)
                Text(channel.name).font(.callout.weight(.semibold)).lineLimit(1)
                if channel.isLive {
                    HStack(spacing: 8) { TVLiveBadge(); Text(formatViewers(channel.viewers)).font(.footnote) }
                } else {
                    Text(channel.lastSeenLiveMs > 0 ? relativeTime(fromMs: channel.lastSeenLiveMs) : String(localized: "phone_channel_offline"))
                        .font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .padding(20).frame(width: 240)
        }
        .buttonStyle(.card)
        .accessibilityLabel(channel.name)
    }
}

/// A recorded video (replay or clip): picture, progress when partly watched, title.
struct TVVideoCard: View {
    let thumbnailUrl: String
    let title: String
    var subtitle: String?
    var progress: Double?
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                ZStack(alignment: .bottom) {
                    Thumbnail(url: thumbnailUrl)
                    if let progress { ProgressView(value: progress).tint(Theme.accent).padding(12) }
                }
                Text(title).font(.callout.weight(.semibold)).lineLimit(1).padding(.horizontal, 6)
                if let subtitle { Text(subtitle).font(.footnote).foregroundStyle(.secondary).lineLimit(1).padding(.horizontal, 6) }
            }
            .padding(.bottom, 8).frame(width: 340)
        }
        .buttonStyle(.card)
    }
}

/// A filter chip you can focus: a capsule button, filled when selected.
struct TVChip: View {
    let title: LocalizedStringKey
    let selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.callout.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 4)
        }
        .buttonStyle(.bordered)
        .tint(selected ? Theme.accentDeep : nil)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Placeholder cards the first time a row loads.
struct TVSkeletonRow: View {
    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: TVMetrics.rowSpacing) {
                ForEach(0..<5, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: 14) {
                        RoundedRectangle(cornerRadius: 12).fill(Theme.surfaceHigh).frame(width: TVMetrics.cardWidth, height: TVMetrics.cardWidth * 9 / 16)
                        RoundedRectangle(cornerRadius: 6).fill(Theme.surfaceHigh).frame(width: 300, height: 20)
                        RoundedRectangle(cornerRadius: 6).fill(Theme.surfaceHigh).frame(width: 180, height: 16)
                    }
                }
            }
            .padding(.horizontal, TVMetrics.edge)
        }
        .scrollDisabled(true).accessibilityHidden(true)
    }
}
