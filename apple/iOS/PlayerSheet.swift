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
    @State private var metrics = ScreenMetrics()
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var replyTo: ChatLine?
    @State private var keyboard = KeyboardObserver()

    private let miniHeight = SheetGeometry.miniHeight

    /// The screen without the keyboard, and its real safe areas; measured with `onGeometryChange`, which reports only when they change (never per frame).
    struct ScreenMetrics: Equatable {
        var size = CGSize.zero
        var insets = EdgeInsets()
    }

    var body: some View {
        ZStack {
            if metrics.size != .zero { sheet(app.session, app.session.playback) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // The reader ignores the keyboard, so `metrics` is the screen minus the real safe areas whether or not the keyboard is up. The sheet is laid out in
        // full-screen coordinates (it covers the status bar and the home indicator); the keyboard only shortens the expanded sheet.
        .ignoresSafeArea(.keyboard)
        .onGeometryChange(for: ScreenMetrics.self) { ScreenMetrics(size: $0.size, insets: $0.safeAreaInsets) } action: { metrics = $0 }
        .onAppear {
            scheduleHide()
            #if DEBUG
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

    @ViewBuilder private func sheet(_ session: PlayerSession, _ playback: PlaybackModel) -> some View {
        let insets = metrics.insets
        let size = CGSize(width: metrics.size.width + insets.leading + insets.trailing, height: metrics.size.height + insets.top + insets.bottom)
        let tabBarVisible = (app.paths[app.tab] ?? []).isEmpty
        let base = SheetGeometry(container: size, safeTop: insets.top, safeBottom: insets.bottom, keyboardHeight: keyboard.height, tabBarVisible: tabBarVisible)
        let landscape = base.landscape
        let docked = session.position == .collapsed
        // Fullscreen is the expanded landscape position; it is decided by the position, never by the drag value.
        let fullscreen = landscape && !docked
        // The controls respect the status bar only in fullscreen: in portrait the picture's own view sits below the (black) status bar area.
        let controlInsets = fullscreen ? insets : EdgeInsets(top: 0, leading: insets.leading, bottom: 0, trailing: insets.trailing)

        ZStack(alignment: .topLeading) {
            if !fullscreen {
                details(playback, session, top: base.expandedVideoHeight, bottom: insets.bottom, base: base)
                    .frame(width: size.width, height: base.expandedHeight, alignment: .topLeading)
                    .sheetCover(progress, color: Color(red: 0.055, green: 0.055, blue: 0.063))
            }
            PictureTransform(progress: progress, base: base) {
                VideoStage(playback: playback, fullscreen: fullscreen, docked: docked, progress: progress, controlsVisible: $controlsVisible, insets: controlInsets, size: base.pictureBase, replyTo: $replyTo, onTap: toggleControls)
            }
            .gesture(dragGesture(travel: base.collapsedY), including: fullscreen ? .subviews : .all)
            strip(playback, session, travel: base.collapsedY)
                .padding(.leading, SheetGeometry.miniVideoWidth).frame(height: miniHeight)
                .sheetFade(progress, .in)
        }
        .modifier(SheetFrame(progress: progress, base: base))
        // Glass anywhere in the sheet (the chat input's) is flat while the sheet moves.
        .environment(\.flatChrome, progress.isDragging)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .position(x: size.width / 2 - insets.leading, y: size.height / 2 - insets.top)
        .onChange(of: session.position) { _, position in
            if position == .collapsed { Keyboard.dismiss() }
            // A position changed by something other than the drag (the chevron, a new stream, the strip's tap): settle there. The drag already did.
            let target: CGFloat = position == .collapsed ? 1 : 0
            if abs(progress.value - target) > 0.001 { settle(to: target, velocity: 0) {} }
        }
        .onChange(of: landscape) { _, now in if now { controlsVisible = true; scheduleHide() } }
        .onChange(of: playback.controller.isPlaying) { _, _ in scheduleHide() }
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

    /// A pan that follows the finger 1:1 inside the two rest positions and with rubber-band resistance beyond them. Every sample writes one value, `progress.value`,
    /// with animations disabled for that write, so a spring still running from a previous gesture is replaced by the finger.
    private func dragGesture(travel: CGFloat) -> some Gesture {
        // The GLOBAL space, never the default local one: the view under the finger moves (and scales) with the sheet, so a translation measured in its own space
        // loses what the view has already moved and the value oscillates from one sample to the next (the zigzag seen in screen recordings).
        DragGesture(minimumDistance: 6, coordinateSpace: .global)
            .onChanged { value in
                if !progress.isDragging { beginDrag() }
                changeDrag(value.translation.height, travel: travel)
            }
            .onEnded { value in endDrag(translation: value.translation.height, velocity: value.velocity.height, travel: travel) }
    }

    /// A new touch catches the sheet wherever it is, in flight included (the value SwiftUI is showing, not the target), and carries on from that.
    private func beginDrag() {
        #if DEBUG
        FrameMonitor.shared.start("drag")
        #endif
        let from = progress.presented
        immediately { progress.value = from }
        progress.dragBase = from
        progress.settleID &+= 1   // a spring still running must not land (or commit a position) under the finger
        setMoving(true)
    }

    private func changeDrag(_ translation: CGFloat, travel: CGFloat) {
        let value = SheetGeometry.rubberBanded(progress.dragBase + translation / max(1, travel))
        progress.presented = value
        immediately { progress.value = value }
    }

    /// Settles from the release point with the finger's velocity. From the expanded sheet a pull of more than half the way (counting a short inertia) docks it; from the
    /// strip a pull up expands it, and a long deliberate pull down, thrown downwards, sends it off the bottom and closes it.
    private func endDrag(translation: CGFloat, velocity: CGFloat, travel: CGFloat) {
        // The sheet keeps counting as "moving" (flat glass, chat held) until it has landed: switching them back at the release made the release frame the costliest.
        #if DEBUG
        FrameMonitor.shared.next("settle")
        #endif
        let session = app.session
        let v = velocity / max(1, travel)
        let p = progress.value
        let startedDocked = progress.dragBase > 0.5
        let projected = p + v * 0.15
        if startedDocked, projected >= 0.5, (p - 1) * travel > 70, velocity > 220 {
            settle(to: 1.3, velocity: v) { session.close() }
            return
        }
        let target: CGFloat = projected > 0.5 ? 1 : 0
        // The position (which rebuilds the sheet's content for the new state) is committed when the sheet lands, not while it is moving.
        settle(to: target, velocity: v) {
            if target == 1 { session.minimise() } else { session.expand() }
        }
    }

    /// A spring on the one value, in SwiftUI's own animation system (the modifiers' `animatableData` are interpolated per frame, no view body is re-run), launched with the
    /// finger's velocity (as a share of the distance left, which is how `initialVelocity` is expressed).
    private func settle(to target: CGFloat, velocity: CGFloat, completion: @escaping () -> Void) {
        progress.settleID &+= 1
        let id = progress.settleID
        if !progress.isDragging { setMoving(true) }
        let distance = target - progress.value
        let relative = abs(distance) > 0.001 ? velocity / distance : 0
        let animation: Animation? = reduceMotion ? nil : .interpolatingSpring(mass: 1, stiffness: 420, damping: 37, initialVelocity: relative)
        withAnimation(animation, completionCriteria: .logicallyComplete) {
            progress.value = target
        } completion: {
            // A spring that was replaced (a new touch, another settle) reports completion too: only the latest one lands.
            guard id == progress.settleID else { return }
            #if DEBUG
            FrameMonitor.shared.next("landing")
            #endif
            progress.presented = target
            // The landing's work (the position the stage is rebuilt for, the glass coming back, the chat catching up) waits a moment, until the sheet has visibly stopped: done in
            // the last frames of the spring it made them late. A new touch in that moment cancels it (the settle id moves on).
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(260))
                guard id == progress.settleID else { return }
                setMoving(false)
                completion()
            }
        }
    }

    /// Moving: a finger is dragging or the sheet is settling. The tab bar's glass is flat and the chat is held meanwhile.
    private func setMoving(_ moving: Bool) {
        #if DEBUG
        // The landing's measurement ends a moment after the sheet has stopped.
        if !moving { Task { @MainActor in try? await Task.sleep(for: .milliseconds(200)); FrameMonitor.shared.stop() } }
        #endif
        progress.isDragging = moving
        app.session.isDragging = moving
        app.session.playback.holdChat = moving
    }

    /// A write that is never animated (the finger's own samples).
    private func immediately(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
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
