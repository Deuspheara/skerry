import SwiftUI
import TwitchShared

/// What sits under the picture of a replay or a clip: the title in full, the kind, date, views and length, a share button and more videos of the channel.
struct MediaPage: View {
    @Environment(AppModel.self) private var app
    let target: PlaybackTarget
    let info: MediaInfo?
    @State private var channel: ChannelModel?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(target.title).font(.headline).fixedSize(horizontal: false, vertical: true)
                chips
                HStack(spacing: 10) {
                    if let url = shareURL {
                        ShareLink(item: url) { Label("media_share", systemImage: "square.and.arrow.up").font(.subheadline.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 9) }
                            .glassCapsule(interactive: true)
                            .buttonStyle(.plain)
                    }
                }
                related
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
        .task(id: target.channelLogin) {
            channel = target.channelLogin.isEmpty ? nil : ChannelModel(graph: app.graph, login: target.channelLogin)
        }
    }

    private var kind: LocalizedStringKey {
        if case .clip = target { "media_kind_clip" } else { "media_kind_replay" }
    }

    private var shareURL: URL? {
        switch target {
        case .replay(let id, _, _, _, _): URL(string: "https://www.twitch.tv/videos/\(id)")
        case .clip(let slug, _, _, _, _): URL(string: "https://clips.twitch.tv/\(slug)")
        case .live: nil
        }
    }

    private var chips: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            chip(kind, icon: { if case .clip = target { "scissors" } else { "play.rectangle.fill" } }(), tint: true)
            if let info {
                if info.dateMs > 0 { chip(Text(Date(timeIntervalSince1970: Double(info.dateMs) / 1000), format: .dateTime.day().month(.abbreviated).year()), icon: "calendar") }
                if info.views > 0 { chip(Text(String(format: String(localized: "replay_views"), formatViewers(Int32(info.views)))), icon: "eye") }
                if info.durationSeconds > 0 { chip(Text(formatDuration(seconds: info.durationSeconds)), icon: "clock") }
            }
        }
    }

    private func chip(_ text: LocalizedStringKey, icon: String, tint: Bool = false) -> some View { chip(Text(text), icon: icon, tint: tint) }

    private func chip(_ text: Text, icon: String, tint: Bool = false) -> some View {
        Label { text } icon: { Image(systemName: icon) }
            .font(.caption.weight(.medium)).fixedSize()
            .foregroundStyle(tint ? Color.white : Theme.textSecondary)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(tint ? Theme.accentDeep : Theme.surface, in: Capsule())
    }

    @ViewBuilder private var related: some View {
        if let state = channel?.state, case .ready(let details, _, _) = LoadState(state.details, as: ChannelDetails.self) {
            let currentId: String? = if case .replay(let id, _, _, _, _) = target { id } else { nil }
            let others = (details.replays as? [Replay] ?? []).filter { $0.id != currentId }.prefix(8)
            if !others.isEmpty {
                Text(String(format: String(localized: "media_more_from"), target.channelName)).font(.subheadline.weight(.bold)).padding(.top, 6)
                ForEach(Array(others), id: \.id) { replay in
                    ReplayRow(replay: replay, channel: details.channel, progress: state.progress[replay.id]) {
                        app.session.open(.replay(id: replay.id, title: replay.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: replay.thumbnailUrl),
                                         info: MediaInfo(views: Int(replay.views), durationSeconds: Int(replay.durationSeconds), dateMs: replay.publishedAtMs))
                    }
                }
            }
        }
    }
}
