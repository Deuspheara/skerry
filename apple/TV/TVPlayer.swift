import AVKit
import SwiftUI
import TwitchShared

/// The player: the system `AVPlayerViewController` (transport bar, scrubbing, the Siri Remote's select / play-pause / swipe gestures, Menu to leave) with
/// the app's menu items in the transport bar (chat, quality, audio only, sleep timer) and the chat as a read-only panel over the right of the picture.
struct TVPlayerScreen: View {
    @Environment(AppModel.self) private var app
    @State private var chatVisible = true

    var body: some View {
        let session = app.session
        let playback = session.playback
        let controller = playback.controller
        ZStack(alignment: .trailing) {
            TVPlayerView(
                controller: controller, isLive: playback.isLive, chatVisible: $chatVisible,
                qualities: controller.qualities, qualityId: controller.qualityId, audioOnly: controller.audioOnly,
                title: playback.ui?.current?.title ?? session.target?.title ?? "", subtitle: session.target?.channelName ?? "",
                selectQuality: { playback.setPreferredQuality($0) }, setAudioOnly: { playback.setAudioOnly($0) },
            )
            .ignoresSafeArea()
            if controller.audioOnly { audioOnly(playback, session) }
            if controller.failed { message("state_error", retry: { playback.retry() }) } else if playback.offline { message("phone_channel_offline", retry: nil) }
            if chatVisible, playback.isLive, (playback.ui?.chatMode ?? .compact) != .hidden {
                // Over the picture, on any frame: a dark scrim behind the column, a dark backing and a shadow behind every line, and the nick colours
                // the shared mapper already made readable on a dark background (the Kotlin `NickContrast` logic).
                ChatList(lines: playback.chat, settings: playback.chatSettings, overVideo: true, onReply: nil)
                    .frame(width: 760)
                    .padding(.vertical, 60).padding(.horizontal, 40)
                    .background(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.55), location: 0.25), .init(color: .black.opacity(0.85), location: 1)], startPoint: .leading, endPoint: .trailing))
                    .allowsHitTesting(false)
                    // VoiceOver reads the chat as one live-updating element whose value is the latest messages (the visual list cannot be focused on tvOS).
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text("tile_chat"))
                    .accessibilityValue(Text(playback.chat.suffix(5).map(\.spokenText).joined(separator: ". ")))
                    .accessibilityAddTraits(.updatesFrequently)
                    .accessibilityRespondsToUserInteraction(true)
                    .environment(\.colorScheme, .dark)
            }
        }
        .background(Color.black.ignoresSafeArea())
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
    @Binding var chatVisible: Bool
    let qualities: [QualityOption]
    let qualityId: String
    let audioOnly: Bool
    let title: String
    let subtitle: String
    let selectQuality: (String) -> Void
    let setAudioOnly: (Bool) -> Void

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
            items.append(UIAction(title: String(localized: "tile_chat"), image: UIImage(systemName: "bubble.left.and.bubble.right"), state: chatVisible ? .on : .off) { _ in
                chatVisible.toggle()
            })
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
}
