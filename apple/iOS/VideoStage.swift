import AVFoundation
import SwiftUI
import TwitchShared

/// The picture with its control layer. The scrim is full-bleed, edge to edge; only the controls themselves respect the safe areas and the cutout.
struct VideoStage: View {
    @Environment(AppModel.self) private var app
    let playback: PlaybackModel
    let fullscreen: Bool
    /// Docked as the strip (the position, not the drag: it flips once, when the sheet lands).
    let docked: Bool
    let progress: SheetProgress
    @Binding var controlsVisible: Bool
    let insets: EdgeInsets
    let size: CGSize
    let expandedHeight: CGFloat
    @Binding var replyTo: ChatLine?
    var onTap: () -> Void

    @State private var sleepChoice: Int?

    var body: some View {
        let controller = playback.controller
        let chatMode = playback.ui?.chatMode ?? .compact
        ZStack {
            VideoSurface(controller: controller)
            // Until the first picture arrives (the address is still being resolved, the stream is loading, it failed) the stream's thumbnail stands in for
            // the black picture; in the docked strip a spinner or a warning says which.
            if !controller.hasPicture, !controller.audioOnly {
                if let thumbnail = app.session.target?.thumbnailUrl, !thumbnail.isEmpty { RemoteImage(url: URL(string: thumbnail)).opacity(0.55) }
                if docked {
                    if controller.failed || playback.offline { Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.white.opacity(0.9)) } else { ProgressView().tint(.white) }
                }
            }
            if !docked {
                Group {
                if fullscreen, chatMode == .overlay, playback.isLive {
                    HStack { Spacer(minLength: 0); chatOverlay.frame(width: size.width * 0.34).padding(.trailing, insets.trailing).padding(.vertical, 24) }
                }
                if !fullscreen, chatMode == .overlay, playback.isLive {
                    VStack { Spacer(); chatOverlay.frame(height: 110).padding(.horizontal, 8).padding(.bottom, 44) }
                }
                status(controller)
                controls(controller, chatMode: chatMode)
                    .opacity(controlsVisible || !controller.isPlaying ? 1 : 0)
                    .allowsHitTesting(controlsVisible || !controller.isPlaying)
                }
                .sheetFade(progress, .out)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if !docked { onTap() } }
        .clipped()
    }

    private var chatOverlay: some View {
        ChatList(lines: playback.chat, settings: playback.chatSettings, overVideo: true) { replyTo = $0 }
            .equatable()
            .mask(LinearGradient(colors: [.clear, .black, .black], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.18)))
            .allowsHitTesting(false)
    }

    @ViewBuilder private func status(_ controller: PlayerController) -> some View {
        if playback.offline {
            overlayMessage("phone_channel_offline", retry: nil)
        } else if controller.failed {
            overlayMessage("state_error", retry: { playback.retry() })
        } else if controller.isBuffering || (!controller.hasPicture && !controller.audioOnly) {
            ProgressView().tint(.white).controlSize(.large)
        } else if controller.audioOnly {
            VStack(spacing: 10) {
                Avatar(url: app.session.playback.ui?.current?.avatarUrl, name: app.session.target?.channelName ?? "", size: 96)
                Image(systemName: "headphones").foregroundStyle(.white)
            }
        }
    }

    private func overlayMessage(_ key: LocalizedStringKey, retry: (() -> Void)?) -> some View {
        VStack(spacing: 10) {
            Text(key).font(.headline).foregroundStyle(.white)
            if let retry { Button("state_retry", action: retry).buttonStyle(.borderedProminent).tint(Theme.accentDeep) }
        }
    }

    private func controls(_ controller: PlayerController, chatMode: ChatMode) -> some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.55), .clear, .clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom)
            VStack {
                topBar(controller, chatMode: chatMode)
                Spacer()
                bottomBar(controller)
            }
            .padding(.leading, fullscreen ? max(insets.leading, 12) : 8).padding(.trailing, fullscreen ? max(insets.trailing, 12) : 8)
            .padding(.top, fullscreen ? max(insets.top, 8) : insets.top + 2).padding(.bottom, fullscreen ? max(insets.bottom, 8) : 6)
            center(controller)
        }
        .foregroundStyle(.white)
    }

    private func topBar(_ controller: PlayerController, chatMode: ChatMode) -> some View {
        HStack(spacing: 6) {
            roundButton("chevron.down", label: "phone_player_minimise") { fullscreen ? exitFullscreen() : app.session.minimise() }
            Text(playback.ui?.current?.title ?? app.session.target?.title ?? "").font(.subheadline.weight(.semibold)).lineLimit(1)
            Spacer(minLength: 0)
            if controller.pipPossible { roundButton("pip.enter", label: "phone_player_pip") { controller.startPictureInPicture() } }
            Menu {
                qualityMenu(controller)
                Button { playback.setAudioOnly(!controller.audioOnly) } label: { Label("quality_audio_only", systemImage: controller.audioOnly ? "checkmark" : "headphones") }
                Menu("phone_player_sleep_timer") {
                    ForEach([15, 30, 45, 60, 90], id: \.self) { minutes in Button(plural("phone_player_sleep_timer_minutes", minutes)) { controller.setSleepTimer(minutes: minutes) } }
                    if controller.sleepEndsAt != nil { Button("phone_player_sleep_timer_cancel", role: .destructive) { controller.setSleepTimer(minutes: nil) } }
                }
                if playback.isLive {
                    Menu("phone_player_chat_toggle") {
                        Button("chat_hidden") { playback.setChatMode(.hidden) }
                        Button("chat_compact") { playback.setChatMode(.compact) }
                        Button("chat_overlay") { playback.setChatMode(.overlay) }
                    }
                }
                if !controller.live {
                    Menu("phone_player_speed") {
                        ForEach([0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { rate in Button("\(rate, specifier: "%g")×") { controller.setRate(Float(rate)) } }
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle").font(.title3).frame(width: 40, height: 40).glass(in: Circle(), interactive: true)
            }
            .accessibilityLabel(Text("player_controls"))
        }
    }

    @ViewBuilder private func qualityMenu(_ controller: PlayerController) -> some View {
        if controller.qualities.count > 1 {
            Menu("player_quality") {
                ForEach(controller.qualities.filter { !$0.audioOnly }) { quality in
                    Button { playback.setPreferredQuality(quality.id) } label: {
                        Label(quality.id == "auto" ? String(localized: "quality_auto") : quality.name, systemImage: controller.qualityId == quality.id ? "checkmark" : "")
                    }
                }
            }
        }
    }

    private func center(_ controller: PlayerController) -> some View {
        HStack(spacing: 36) {
            if !controller.live { roundButton("gobackward.15", label: "phone_player_back10", big: true) { controller.skip(by: -15) } }
            Button { controller.togglePlay() } label: {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill").font(.title).frame(width: 68, height: 68).glass(in: Circle(), interactive: true)
            }
            .buttonStyle(.plain).accessibilityLabel(Text(controller.isPlaying ? "player_pause" : "player_play"))
            if !controller.live { roundButton("goforward.15", label: "phone_player_forward", big: true) { controller.skip(by: 15) } }
        }
        .opacity(controller.isBuffering ? 0 : 1)
    }

    private func bottomBar(_ controller: PlayerController) -> some View {
        VStack(spacing: 4) {
            if !controller.live, controller.duration > 0 {
                Slider(value: Binding(get: { controller.position }, set: { controller.seek(to: $0) }), in: 0...max(1, controller.duration)).tint(Theme.accent)
                HStack { Text(formatDuration(seconds: Int(controller.position))); Spacer(); Text(formatDuration(seconds: Int(controller.duration))) }.font(.caption.monospacedDigit())
            }
            HStack {
                if controller.live { HStack(spacing: 6) { LiveBadge(); if let up = uptime(since: playback.ui?.current?.startedAtMs ?? 0) { Text(up).font(.caption.monospacedDigit()) } } }
                if let end = controller.sleepEndsAt { Label { Text(end, style: .timer).monospacedDigit() } icon: { Image(systemName: "moon.zzz.fill") }.font(.caption).accessibilityLabel(Text("phone_player_sleep_timer_active")) }
                Spacer()
                roundButton("speaker.slash.fill", label: "player_muted", selected: controller.muted) { controller.muted.toggle() }
                roundButton(fullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right", label: fullscreen ? "player_exit_fullscreen" : "player_fullscreen") { fullscreen ? exitFullscreen() : Orientation.request(.landscape) }
            }
        }
    }

    private func exitFullscreen() { Orientation.request(.portrait); Task { try? await Task.sleep(for: .seconds(0.6)); Orientation.request(.all) } }

    private func roundButton(_ symbol: String, label: LocalizedStringKey, big: Bool = false, selected: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(big ? .title2 : .body.weight(.semibold)).frame(width: big ? 56 : 40, height: big ? 56 : 40)
                .glass(in: Circle(), interactive: true, tint: selected ? Theme.accentDeep : nil)
        }
        .buttonStyle(.plain).accessibilityLabel(Text(label))
    }
}
