import AVFoundation
import SwiftUI
import TwitchShared

/// The player, above the tab navigation. One layout for every position: the video view never changes parent, only its frame, so expanding,
/// minimising (a YouTube-style drag to the strip above the tab bar), rotating to fullscreen and Picture in Picture share one layer.
struct PlayerSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The one value the drag and the settle drive; no body here reads it (see `SheetProgress`).
    @State private var progress = SheetProgress()
    @State private var driver = SpringDriver()
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var replyTo: ChatLine?
    @State private var keyboard = KeyboardObserver()

    private let miniHeight = SheetGeometry.miniHeight

    var body: some View {
        let session = app.session
        let playback = session.playback
        GeometryReader { outer in
            // The reader ignores the keyboard (below), so `outer` is the screen minus the real safe areas whether or not the keyboard is up. The sheet
            // is laid out in full-screen coordinates (it covers the status bar and the home indicator); the keyboard only shortens the expanded sheet.
            let insets = outer.safeAreaInsets
            let size = CGSize(width: outer.size.width + insets.leading + insets.trailing, height: outer.size.height + insets.top + insets.bottom)
            let tabBarVisible = (app.paths[app.tab] ?? []).isEmpty
            let base = SheetGeometry(container: size, safeTop: insets.top, safeBottom: insets.bottom, keyboardHeight: keyboard.height, tabBarVisible: tabBarVisible)
            let landscape = base.landscape
            let docked = session.position == .collapsed
            // Fullscreen is the expanded landscape position; it is decided by the position, never by the drag value.
            let fullscreen = landscape && !docked
            let expandedVideoHeight = base.at(0).expandedVideoHeight

            ZStack(alignment: .topLeading) {
                if !fullscreen {
                    details(playback, session, top: expandedVideoHeight, bottom: insets.bottom, base: base)
                        .frame(width: size.width, height: base.expandedHeight, alignment: .topLeading)
                        .sheetFade(progress, .out)
                }
                VideoStage(playback: playback, fullscreen: fullscreen, docked: docked, progress: progress, controlsVisible: $controlsVisible, insets: insets, size: size, expandedHeight: expandedVideoHeight, replyTo: $replyTo, onTap: toggleControls)
                    .modifier(VideoFrame(progress: progress, base: base))
                    .background(Color.black)
                    .gesture(dragGesture(travel: base.collapsedY), including: fullscreen ? .subviews : .all)
                strip(playback, session, travel: base.collapsedY)
                    .padding(.leading, SheetGeometry.miniVideoWidth).frame(height: miniHeight)
                    .sheetFade(progress, .in)
            }
            .modifier(SheetFrame(progress: progress, base: base))
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .position(x: size.width / 2 - insets.leading, y: size.height / 2 - insets.top)
            .onChange(of: session.position) { _, position in
                if position == .collapsed { Keyboard.dismiss() }
                // A position changed by something other than the drag (the chevron, a new stream, the strip's tap): settle there. The drag already did.
                let target: CGFloat = position == .collapsed ? 1 : 0
                if driver.target != target || (!driver.isRunning && abs(progress.value - target) > 0.001) {
                    driver.animate(progress, to: target, velocity: 0, reduceMotion: reduceMotion)
                }
            }
            .onChange(of: landscape) { _, now in if now { controlsVisible = true; scheduleHide() } }
            .onChange(of: playback.controller.isPlaying) { _, _ in scheduleHide() }
        }
        .ignoresSafeArea(.keyboard)
        .onAppear {
            scheduleHide()
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-drag-benchmark") {
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(8))
                    let travel: CGFloat = 600
                    DragBenchmark.run(label: "drag", travel: travel, begin: { beginDrag() }, changed: { changeDrag($0, travel: travel) }, ended: { translation, velocity in endDrag(translation: translation, velocity: velocity, travel: travel) })
                }
            }
            if ProcessInfo.processInfo.arguments.contains("-fullscreen") { Orientation.request(.landscape) }
            if ProcessInfo.processInfo.arguments.contains("-minimised") { app.session.minimise() }
            if let delay = LaunchMode.argument("-minimise-after").flatMap(Double.init) {
                Task { try? await Task.sleep(for: .seconds(delay)); app.session.minimise() }
            }
            #endif
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(app.session.position == .expanded ? .isModal : [])
    }

    // MARK: Details under the picture

    @ViewBuilder private func details(_ playback: PlaybackModel, _ session: PlayerSession, top: CGFloat, bottom: CGFloat, base: SheetGeometry) -> some View {
        VStack(spacing: 0) {
            streamerRow(playback, session, travel: base.collapsedY)
            if (playback.ui?.chatMode ?? .compact) != .hidden, playback.isLive {
                Divider().opacity(0.4)
                SheetChat(playback: playback, progress: progress, replyTo: $replyTo)
                ChatComposer(playback: playback, replyTo: $replyTo)
            } else {
                Spacer()
            }
        }
        .padding(.bottom, bottom)
        .background(Color(red: 0.055, green: 0.055, blue: 0.063))
        .environment(\.colorScheme, .dark)
        .padding(.top, top)
    }

    private func streamerRow(_ playback: PlaybackModel, _ session: PlayerSession, travel: CGFloat) -> some View {
        let target = session.target
        let stream = playback.ui?.current
        return Button { app.openChannel(target?.channelLogin ?? "") } label: {
            HStack(spacing: 12) {
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
                Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary).flipsForRightToLeftLayoutDirection(true)
            }
            .padding(.horizontal, 14).padding(.vertical, 10).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(String(format: String(localized: "phone_player_open_channel"), target?.channelName ?? "")))
        .gesture(dragGesture(travel: travel))
    }

    // MARK: Mini strip

    private func strip(_ playback: PlaybackModel, _ session: PlayerSession, travel: CGFloat) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(playback.ui?.current?.title ?? session.target?.title ?? "").font(.footnote.weight(.semibold)).lineLimit(1)
                Text(session.target?.channelName ?? "").font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            Spacer(minLength: 0)
            Button { playback.controller.togglePlay() } label: {
                Image(systemName: playback.controller.isPlaying ? "pause.fill" : "play.fill").font(.title3).frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text(playback.controller.isPlaying ? "player_pause" : "player_play"))
            Button { session.close() } label: { Image(systemName: "xmark").font(.body.weight(.semibold)).frame(width: 44, height: 44) }
                .accessibilityLabel(Text("phone_player_close"))
        }
        .buttonStyle(.plain)
        .padding(.leading, 12).padding(.trailing, 4)
        .contentShape(Rectangle())
        .onTapGesture { session.expand() }
        .accessibilityAction(named: Text("phone_player_expand")) { session.expand() }
        .gesture(dragGesture(travel: travel))
    }

    // MARK: Gestures

    /// A pan that follows the finger 1:1 inside the two rest positions and with rubber-band resistance beyond them. Every sample writes one value, `progress.value`.
    private func dragGesture(travel: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                if !progress.isDragging { beginDrag() }
                changeDrag(value.translation.height, travel: travel)
            }
            .onEnded { value in endDrag(translation: value.translation.height, velocity: value.velocity.height, travel: travel) }
    }

    /// A new touch catches the sheet wherever it is, in flight included, and carries on from that value.
    private func beginDrag() {
        driver.stop()
        progress.isDragging = true
        progress.dragBase = progress.value
    }

    private func changeDrag(_ translation: CGFloat, travel: CGFloat) {
        progress.value = SheetGeometry.rubberBanded(progress.dragBase + translation / max(1, travel))
    }

    /// Settles from the release point with the finger's velocity. From the expanded sheet a pull of more than half the way (counting a short inertia) docks it; from the
    /// strip a pull up expands it, and a long deliberate pull down, thrown downwards, sends it off the bottom and closes it.
    private func endDrag(translation: CGFloat, velocity: CGFloat, travel: CGFloat) {
        progress.isDragging = false
        let session = app.session
        let v = velocity / max(1, travel)
        let p = progress.value
        let startedDocked = progress.dragBase > 0.5
        let projected = p + v * 0.15
        if startedDocked, projected >= 0.5, (p - 1) * travel > 70, velocity > 220 {
            driver.animate(progress, to: 1.3, velocity: v, reduceMotion: reduceMotion) { session.close() }
            return
        }
        let target: CGFloat = projected > 0.5 ? 1 : 0
        // The position (which rebuilds the sheet's content for the new state) is committed when the sheet lands, not while it is moving.
        driver.animate(progress, to: target, velocity: v, reduceMotion: reduceMotion) { if target == 1 { session.minimise() } else { session.expand() } }
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) { controlsVisible.toggle() }
        scheduleHide()
    }

    private func scheduleHide() {
        hideTask?.cancel()
        guard controlsVisible, app.session.playback.controller.isPlaying else { return }
        hideTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.25)) { controlsVisible = false }
        }
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }
}

/// The chat under the picture. It is its own view so that the two moments a finger touches or leaves the sheet (and every new message) re-evaluate this view,
/// not the sheet's body: the list is frozen while dragging and is never rebuilt for the drag frames themselves.
private struct SheetChat: View {
    let playback: PlaybackModel
    let progress: SheetProgress
    @Binding var replyTo: ChatLine?

    var body: some View {
        ChatList(lines: playback.chat, settings: playback.chatSettings, frozen: progress.isDragging) { replyTo = $0 }
            .equatable()
    }
}
