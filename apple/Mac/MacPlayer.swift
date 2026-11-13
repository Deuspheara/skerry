import AVKit
import SwiftUI
import TwitchShared

/// The picture: an `AVPlayerView`, which brings the system transport bar (play, scrub for replays, volume), Picture in Picture, AirPlay and its own
/// full-screen button. The app's `PlayerController` owns the `AVPlayer` (and Now Playing); the view only displays it.
struct MacVideoSurface: NSViewRepresentable {
    let controller: PlayerController
    var controls: AVPlayerViewControlsStyle = .inline

    func makeNSView(context: Context) -> AVPlayerView {
        let view = AVPlayerView()
        view.player = controller.player
        view.controlsStyle = controls
        view.showsFullScreenToggleButton = controls != .none
        view.allowsPictureInPicturePlayback = true
        // The controller publishes Now Playing itself (title, live flag); the view must not overwrite it.
        view.updatesNowPlayingInfoCenter = false
        view.videoGravity = .resizeAspect
        return view
    }

    func updateNSView(_ view: AVPlayerView, context: Context) {
        view.controlsStyle = controls
        view.showsFullScreenToggleButton = controls != .none
    }
}

/// The player above the page: the expanded pane (picture, info and a resizable chat column) or, once minimised, a floating mini player.
struct MacPlayerLayer: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Group {
            switch app.session.position {
            case .expanded: MacPlayerPane().transition(.opacity)
            case .collapsed: MacMiniPlayer().padding(20).transition(.scale(scale: 0.9, anchor: .bottomTrailing).combined(with: .opacity))
            }
        }
        .animation(app.motion, value: app.session.position)
    }
}

struct MacPlayerPane: View {
    @Environment(AppModel.self) private var app
    @Environment(MacModel.self) private var mac
    @State private var replyTo: ChatLine?

    var body: some View {
        let session = app.session
        let playback = session.playback
        let chatMode = playback.ui?.chatMode ?? .compact
        HSplitView {
            VStack(spacing: 0) {
                header(playback, session)
                ZStack {
                    Color.black
                    MacVideoSurface(controller: playback.controller)
                    status(playback)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                infoBar(playback, session)
            }
            .frame(minWidth: 460)
            if mac.showChat, playback.isLive, chatMode != .hidden {
                MacChatColumn(playback: playback, replyTo: $replyTo)
                    .frame(minWidth: 260, idealWidth: 340, maxWidth: 560)
            }
        }
        .background(Theme.background)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }

    private func header(_ playback: PlaybackModel, _ session: PlayerSession) -> some View {
        HStack(spacing: 10) {
            Button { session.minimise() } label: { Image(systemName: "chevron.down").font(.body.weight(.semibold)).frame(width: 28, height: 28) }
                .buttonStyle(.borderless)
                .help(Text("phone_player_minimise"))
                .accessibilityLabel(Text("phone_player_minimise"))
            Text(playback.ui?.current?.title ?? session.target?.title ?? "").font(.headline).lineLimit(1)
            Spacer(minLength: 0)
            PlayerOptionsMenu(playback: playback)
            Button { session.close() } label: { Image(systemName: "xmark").font(.body.weight(.semibold)).frame(width: 28, height: 28) }
                .buttonStyle(.borderless)
                .help(Text("mac_close_player"))
                .accessibilityLabel(Text("mac_close_player"))
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
    }

    @ViewBuilder private func status(_ playback: PlaybackModel) -> some View {
        let controller = playback.controller
        if playback.offline {
            overlayMessage("phone_channel_offline", retry: nil)
        } else if controller.failed {
            overlayMessage("state_error", retry: { playback.retry() })
        } else if controller.isBuffering || (!controller.hasPicture && !controller.audioOnly) {
            ProgressView().controlSize(.large).tint(.white)
        } else if controller.audioOnly {
            VStack(spacing: 10) {
                Avatar(url: playback.ui?.current?.avatarUrl, name: app.session.target?.channelName ?? "", size: 110)
                Image(systemName: "headphones").foregroundStyle(.white).font(.title2)
            }
        }
    }

    private func overlayMessage(_ key: LocalizedStringKey, retry: (() -> Void)?) -> some View {
        VStack(spacing: 10) {
            Text(key).font(.headline).foregroundStyle(.white)
            if let retry { Button("state_retry", action: retry).buttonStyle(.borderedProminent).tint(Theme.accentDeep) }
        }
    }

    private func infoBar(_ playback: PlaybackModel, _ session: PlayerSession) -> some View {
        let target = session.target
        let stream = playback.ui?.current
        return HStack(spacing: 12) {
            Avatar(url: stream?.avatarUrl ?? { if case .live(_, _, _, let url, _) = target { url } else { nil } }(), name: target?.channelName ?? "", size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(stream?.title ?? target?.title ?? "").font(.subheadline.weight(.semibold)).lineLimit(1)
                HStack(spacing: 6) {
                    Text(target?.channelName ?? "").font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    if let stream {
                        Text("·").foregroundStyle(Theme.textSecondary)
                        Text(stream.categoryName).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                    }
                }
            }
            Spacer(minLength: 0)
            if let stream {
                VStack(alignment: .trailing, spacing: 2) {
                    HStack(spacing: 4) { LiveBadge(); Text(formatViewers(stream.viewers)).font(.caption.weight(.semibold)) }
                    if let up = uptime(since: stream.startedAtMs) { Text(up).font(.caption2.monospacedDigit()).foregroundStyle(Theme.textSecondary) }
                }
            }
            Button { app.openChannel(target?.channelLogin ?? "") } label: { Label("mac_open_channel", systemImage: "person.crop.rectangle") }
                .help(Text(String(format: String(localized: "phone_player_open_channel"), target?.channelName ?? "")))
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Theme.surface)
    }
}

/// The player's options: quality, audio only, sleep timer, speed (replays), the chat column and the channel page.
struct PlayerOptionsMenu: View {
    @Environment(AppModel.self) private var app
    @Environment(MacModel.self) private var mac
    let playback: PlaybackModel

    var body: some View {
        let controller = playback.controller
        Menu {
            if controller.qualities.count > 1 {
                Menu("player_quality") {
                    ForEach(controller.qualities.filter { !$0.audioOnly }) { quality in
                        Button { playback.setPreferredQuality(quality.id) } label: {
                            Label(quality.id == "auto" ? String(localized: "quality_auto") : quality.name, systemImage: controller.qualityId == quality.id ? "checkmark" : "")
                        }
                    }
                }
            }
            Button { playback.setAudioOnly(!controller.audioOnly) } label: { Label("quality_audio_only", systemImage: controller.audioOnly ? "checkmark" : "headphones") }
            Menu("phone_player_sleep_timer") {
                ForEach([15, 30, 45, 60, 90], id: \.self) { minutes in Button(plural("phone_player_sleep_timer_minutes", minutes)) { controller.setSleepTimer(minutes: minutes) } }
                if controller.sleepEndsAt != nil { Button("phone_player_sleep_timer_cancel", role: .destructive) { controller.setSleepTimer(minutes: nil) } }
            }
            if !controller.live {
                Menu("phone_player_speed") {
                    ForEach([0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { rate in Button("\(rate, specifier: "%g")×") { controller.setRate(Float(rate)) } }
                }
            }
            if playback.isLive {
                Divider()
                Toggle("phone_player_chat_toggle", isOn: Binding(get: { mac.showChat }, set: { mac.showChat = $0 }))
            }
        } label: {
            Image(systemName: "ellipsis.circle").font(.title3)
        }
        .menuStyle(.borderlessButton).fixedSize()
        .help(Text("player_controls"))
        .accessibilityLabel(Text("player_controls"))
    }
}

/// The chat beside the picture: the messages (newest at the bottom) and the input with its emote popover, on the dark chat surface.
struct MacChatColumn: View {
    let playback: PlaybackModel
    @Binding var replyTo: ChatLine?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("tile_chat").font(.headline)
                Spacer()
                if let room = playback.room, room.any { Image(systemName: "lock.fill").foregroundStyle(Theme.textSecondary) }
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            Divider().opacity(0.4)
            ChatList(lines: playback.chat, settings: playback.chatSettings) { replyTo = $0 }
            ChatComposer(playback: playback, replyTo: $replyTo)
        }
        .background(Color(red: 0.055, green: 0.055, blue: 0.063))
        .environment(\.colorScheme, .dark)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("tile_chat"))
    }
}

/// The minimised player: a small floating picture with controls on hover (play or pause, expand, close). Clicking the picture expands it.
struct MacMiniPlayer: View {
    @Environment(AppModel.self) private var app
    @State private var hovering = false

    var body: some View {
        let session = app.session
        let controller = session.playback.controller
        ZStack {
            MacVideoSurface(controller: controller, controls: .none)
            Color.clear.contentShape(Rectangle()).onTapGesture { session.expand() }
            if hovering {
                VStack {
                    HStack {
                        Spacer()
                        miniButton("xmark", "mac_close_player") { session.close() }
                    }
                    Spacer()
                    HStack(spacing: 10) {
                        miniButton(controller.isPlaying ? "pause.fill" : "play.fill", controller.isPlaying ? "player_pause" : "player_play") { controller.togglePlay() }
                        Spacer()
                        miniButton("arrow.up.left.and.arrow.down.right", "mac_expand_player") { session.expand() }
                    }
                }
                .padding(8)
                .background(LinearGradient(colors: [.black.opacity(0.5), .clear, .black.opacity(0.55)], startPoint: .top, endPoint: .bottom))
                .transition(.opacity)
            }
        }
        .frame(width: 340, height: 192)
        .overlay(alignment: .bottom) {
            // Always-visible chrome: what is playing, so the picture is never an unlabelled black rectangle.
            VStack(alignment: .leading, spacing: 1) {
                Text(session.target?.title ?? "").font(.callout.weight(.semibold)).lineLimit(1)
                Text(session.target?.channelName ?? "").font(.caption).opacity(0.8).lineLimit(1)
            }
            .foregroundStyle(.white).shadow(color: .black.opacity(0.8), radius: 2)
            .padding(.horizontal, 10).padding(.top, 20).padding(.bottom, 8).frame(maxWidth: .infinity, alignment: .leading)
            .background(LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom))
            .opacity(hovering ? 0 : 1).allowsHitTesting(false)
        }
        .background(Color.black)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Theme.hairline, lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 14, y: 4)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.15), value: hovering)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(session.target?.title ?? ""))
    }

    private func miniButton(_ symbol: String, _ label: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.body.weight(.semibold)).foregroundStyle(.white).frame(width: 30, height: 30).background(.black.opacity(0.45), in: Circle())
        }
        .buttonStyle(.plain)
        .help(Text(label))
        .accessibilityLabel(Text(label))
    }
}
