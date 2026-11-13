import AVKit
import SwiftUI
import TwitchShared

/// The player: the system `AVPlayerViewController` (transport bar, scrubbing, the Siri Remote's select / play-pause / swipe gestures, Menu to leave) with
/// the app's menu items in the transport bar (chat, quality, audio only, sleep timer) and the chat as a read-only panel over the right of the picture.
struct TVPlayerScreen: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        let session = app.session
        let playback = session.playback
        let controller = playback.controller
        let mode = playback.isLive ? (playback.ui?.chatMode ?? .compact) : .hidden
        GeometryReader { screen in
            let column = mode == .compact ? TVChatMetrics.columnWidth(screenWidth: screen.size.width) : 0
            ZStack(alignment: .topLeading) {
                // The picture is resized once, when the chat opens or closes (the way the Android TV does it); only the chat slides.
                TVPlayerView(
                    controller: controller, isLive: playback.isLive, chatMode: mode, chatSettings: playback.chatSettings,
                    qualities: controller.qualities, qualityId: controller.qualityId, audioOnly: controller.audioOnly,
                    title: playback.ui?.current?.title ?? session.target?.title ?? "", subtitle: session.target?.channelName ?? "",
                    selectQuality: { playback.setPreferredQuality($0) }, setAudioOnly: { playback.setAudioOnly($0) },
                    setChatMode: { playback.setChatMode($0) }, updateChat: { playback.updateChat($0) },
                )
                .frame(width: screen.size.width - column, height: screen.size.height)
                .animation(nil, value: column)
                if controller.audioOnly { audioOnly(playback, session).frame(width: screen.size.width - column, height: screen.size.height) }
                if controller.failed { message("state_error", retry: { playback.retry() }).frame(width: screen.size.width - column, height: screen.size.height) } else if playback.offline { message("phone_channel_offline", retry: nil).frame(width: screen.size.width - column, height: screen.size.height) }
                if mode != .hidden {
                    TVChatColumn(playback: playback, mode: mode, screen: screen.size)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: mode == .overlay ? .bottomTrailing : .topTrailing)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
            .animation(.smooth(duration: 0.35), value: mode)
        }
        .ignoresSafeArea()
        .background(Color.black.ignoresSafeArea())
        #if DEBUG
        .task {
            // Screenshots: `-chat-mode hidden|compact|overlay`.
            try? await Task.sleep(for: .seconds(3))
            switch LaunchMode.argument("-chat-mode") {
            case "hidden": playback.setChatMode(.hidden)
            case "compact": playback.setChatMode(.compact)
            case "overlay": playback.setChatMode(.overlay)
            default: break
            }
        }
        #endif
    }

    private func audioOnly(_ playback: PlaybackModel, _ session: PlayerSession) -> some View {
        VStack(spacing: 20) {
            Avatar(url: playback.ui?.current?.avatarUrl, name: session.target?.channelName ?? "", size: 220)
            Image(systemName: "headphones").font(.largeTitle).foregroundStyle(.white)
        }
        .allowsHitTesting(false)
    }

    private func message(_ key: LocalizedStringKey, retry: (() -> Void)?) -> some View {
        VStack(spacing: 24) {
            Text(key).font(.title2.weight(.semibold)).foregroundStyle(.white)
            if let retry { Button("state_retry", action: retry) }
        }
    }
}

private struct TVPlayerView: UIViewControllerRepresentable {
    let controller: PlayerController
    let isLive: Bool
    let chatMode: ChatMode
    let chatSettings: ChatSettings?
    let qualities: [QualityOption]
    let qualityId: String
    let audioOnly: Bool
    let title: String
    let subtitle: String
    let selectQuality: (String) -> Void
    let setAudioOnly: (Bool) -> Void
    let setChatMode: (ChatMode) -> Void
    let updateChat: (@escaping (ChatSettings) -> ChatSettings) -> Void

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = controller.player
        // Now Playing is filled by the system from the item's external metadata (title and channel, set by `PlayerController`).
        vc.showsPlaybackControls = true
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {
        var items: [UIMenuElement] = []
        if isLive {
            // Where the chat sits: hidden, the column next to the picture, or over the picture (the Android TV's three modes).
            let modes: [(ChatMode, String)] = [(.hidden, "chat_hidden"), (.compact, "chat_compact"), (.overlay, "chat_overlay")]
            items.append(UIMenu(title: String(localized: "tile_chat"), image: UIImage(systemName: "bubble.left.and.bubble.right"), children: modes.map { mode, key in
                UIAction(title: String(localized: String.LocalizationValue(key)), state: chatMode == mode ? .on : .off) { _ in setChatMode(mode) }
            }))
            if let settings = chatSettings { items.append(chatOptions(settings)) }
        }
        if qualities.count > 1 {
            let children = qualities.filter { !$0.audioOnly }.map { quality in
                UIAction(title: quality.id == "auto" ? String(localized: "quality_auto") : quality.name, state: qualityId == quality.id ? .on : .off) { _ in selectQuality(quality.id) }
            }
            items.append(UIMenu(title: String(localized: "player_quality"), image: UIImage(systemName: "slider.horizontal.3"), children: children))
        }
        items.append(UIAction(title: String(localized: "quality_audio_only"), image: UIImage(systemName: "headphones"), state: audioOnly ? .on : .off) { _ in setAudioOnly(!audioOnly) })
        let sleep = [15, 30, 45, 60, 90].map { minutes in UIAction(title: plural("phone_player_sleep_timer_minutes", minutes)) { _ in controller.setSleepTimer(minutes: minutes) } }
            + (controller.sleepEndsAt != nil ? [UIAction(title: String(localized: "phone_player_sleep_timer_cancel"), attributes: .destructive) { _ in controller.setSleepTimer(minutes: nil) }] : [])
        items.append(UIMenu(title: String(localized: "phone_player_sleep_timer"), image: UIImage(systemName: "moon.zzz"), children: sleep))
        vc.transportBarCustomMenuItems = items
    }

    /// The Android TV's chat options (text size, spacing, background, badges, time, animated emotes) as one transport-bar menu.
    private func chatOptions(_ settings: ChatSettings) -> UIMenu {
        func change(_ transform: @escaping (ChatSettings) -> ChatSettings) -> UIActionHandler { { _ in updateChat(transform) } }
        func copy(_ c: ChatSettings, size: ChatTextSize? = nil, density: ChatDensity? = nil, opacity: Int32? = nil, badges: Bool? = nil, time: Bool? = nil, animated: Bool? = nil) -> ChatSettings {
            c.doCopy(textSize: size ?? c.textSize, density: density ?? c.density, backgroundOpacity: opacity ?? c.backgroundOpacity, showBadges: badges ?? c.showBadges,
                     showTimestamps: time ?? c.showTimestamps, animatedEmotes: animated ?? c.animatedEmotes, animatedLimit: c.animatedLimit)
        }
        let sizes: [(ChatTextSize, String)] = [(.small, "opt_small"), (.medium, "opt_medium"), (.large, "opt_large")]
        let densities: [(ChatDensity, String)] = [(.compact, "opt_tight"), (.standard, "opt_normal"), (.relaxed, "opt_loose")]
        let size = UIMenu(title: String(localized: "chat_opt_text"), children: sizes.map { value, key in
            UIAction(title: String(localized: String.LocalizationValue(key)), state: settings.textSize == value ? .on : .off, handler: change { copy($0, size: value) })
        })
        let spacing = UIMenu(title: String(localized: "chat_opt_spacing"), children: densities.map { value, key in
            UIAction(title: String(localized: String.LocalizationValue(key)), state: settings.density == value ? .on : .off, handler: change { copy($0, density: value) })
        })
        let background = UIMenu(title: String(localized: "chat_opt_background"), children: [40, 60, 80, 100].map { percent in
            UIAction(title: "\(percent) %", state: settings.backgroundOpacity == Int32(percent) ? .on : .off, handler: change { copy($0, opacity: Int32(percent)) })
        })
        let toggles = [
            UIAction(title: String(localized: "chat_opt_badges"), state: settings.showBadges ? .on : .off, handler: change { copy($0, badges: !settings.showBadges) }),
            UIAction(title: String(localized: "chat_opt_time"), state: settings.showTimestamps ? .on : .off, handler: change { copy($0, time: !settings.showTimestamps) }),
            UIAction(title: String(localized: "chat_opt_animated"), state: settings.animatedEmotes ? .on : .off, handler: change { copy($0, animated: !settings.animatedEmotes) }),
        ]
        return UIMenu(title: String(localized: "player_chat_options"), image: UIImage(systemName: "slider.horizontal.below.rectangle"), children: [size, spacing, background] + toggles)
    }
}
