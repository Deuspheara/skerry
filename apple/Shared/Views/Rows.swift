import SwiftUI
import TwitchShared

/// Rows shared by the iOS, Mac and Apple TV apps (the TV and the Mac lay them out in their own lists).

struct OfflineRow: View {
    let channel: ChannelRef
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Avatar(url: channel.avatarUrl, name: channel.name, size: 44).opacity(0.65)
                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(channel.lastSeenLiveMs > 0
                        ? String(format: String(localized: "phone_following_last_live"), relativeTime(fromMs: channel.lastSeenLiveMs))
                        : String(localized: "phone_channel_offline"))
                        .font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

struct ContinueWatchingRow: View {
    @Environment(AppModel.self) private var app
    let items: [ContinueWatchingItem]

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 12) {
                ForEach(items, id: \.replay.replay.id) { item in
                    Button { app.openReplay(item) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            ZStack(alignment: .bottom) {
                                Thumbnail(url: item.replay.replay.thumbnailUrl)
                                ProgressView(value: Double(item.progress.positionMs), total: max(1, Double(item.replay.replay.durationSeconds) * 1000))
                                    .tint(Theme.accent).padding(8)
                            }
                            .frame(width: 168)
                            Text(item.replay.replay.title).font(.footnote.weight(.semibold)).lineLimit(1).frame(width: 168, alignment: .leading)
                            Text(item.replay.channelName).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
                        }
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }
}


/// A channel result: avatar with a red ring when live, the name, and the viewers beside a LIVE badge. A live channel opens the player, an offline one the channel page.
struct ChannelResultRow: View {
    @Environment(AppModel.self) private var app
    let channel: ChannelRef

    var body: some View {
        Button {
            if channel.isLive {
                app.session.open(.live(login: channel.login, name: channel.name, title: channel.name, avatarUrl: channel.avatarUrl, thumbnailUrl: ""))
            } else {
                app.push(.channel(login: channel.login))
            }
        } label: {
            HStack(spacing: 12) {
                Avatar(url: channel.avatarUrl, name: channel.name, size: 46)
                    .overlay(Circle().stroke(Theme.live, lineWidth: channel.isLive ? 2 : 0).padding(-3))
                    .padding(3)
                VStack(alignment: .leading, spacing: 3) {
                    Text(channel.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    if channel.isLive {
                        HStack(spacing: 6) {
                            LiveBadge()
                            Text(String(format: String(localized: "phone_search_viewers"), formatViewers(channel.viewers))).font(.caption).foregroundStyle(Theme.textSecondary)
                        }
                    } else {
                        Text("phone_channel_offline").font(.footnote).foregroundStyle(Theme.textSecondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

struct ReplayRow: View {
    let replay: Replay
    let channel: ChannelRef
    let progress: VodProgress?
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack(alignment: .bottom) {
                    Thumbnail(url: replay.thumbnailUrl)
                    if let progress, replay.durationSeconds > 0 {
                        ProgressView(value: min(1, Double(progress.positionMs) / (Double(replay.durationSeconds) * 1000))).tint(Theme.accent).padding(6)
                    }
                }
                .frame(width: 148)
                VStack(alignment: .leading, spacing: 4) {
                    Text(replay.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(replay.duration).font(.footnote).foregroundStyle(Theme.textSecondary)
                    Text(String(format: String(localized: "replay_views"), formatViewers(replay.views))).font(.caption).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}
