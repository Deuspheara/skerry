import AVFoundation
import SwiftUI
import TwitchShared

/// The player, above the tab navigation. One layout for every position: the video view never changes parent, only its frame, so expanding,
/// minimising (a YouTube-style drag to the strip above the tab bar), rotating to fullscreen and Picture in Picture share one layer.
struct PlayerSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drag: CGFloat = 0
    @State private var dragging = false
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var replyTo: ChatLine?
    @State private var keyboard = KeyboardObserver()

    private let miniHeight: CGFloat = 64
    private let tabBarHeight: CGFloat = 72

    var body: some View {
        let session = app.session
        let playback = session.playback
        GeometryReader { outer in
            // The sheet is laid out in full-screen coordinates (it covers the status bar and the home indicator): the outer reader still reports the
            // real safe areas, the keyboard is subtracted so the composer stays above it.
            let insets = outer.safeAreaInsets
            let keyboard = keyboard.height
            let size = CGSize(width: outer.size.width + insets.leading + insets.trailing, height: outer.size.height + insets.top + insets.bottom - (keyboard > 0 ? max(0, keyboard - insets.bottom) : 0))
            let landscape = size.width > size.height
            let bottomChrome = (app.paths[app.tab] ?? []).isEmpty ? tabBarHeight : 0
            let collapsedY = outer.size.height + insets.top - bottomChrome - miniHeight
            let travel = max(1, collapsedY)
            let base: CGFloat = session.position == .collapsed ? 1 : 0
            let p = min(1, max(0, base + drag / travel))
            let fullscreen = landscape && p == 0
            let videoWidth = lerp(size.width, 112, p)
            let expandedVideoHeight = fullscreen ? size.height : size.width * 9 / 16 + insets.top
            let videoHeight = lerp(expandedVideoHeight, miniHeight, p)
            let sheetY = lerp(0, collapsedY, p)
            let sheetHeight = lerp(size.height, miniHeight, p)

            ZStack(alignment: .topLeading) {
                Rectangle().fill(p > 0.01 ? AnyShapeStyle(Theme.surface) : AnyShapeStyle(Theme.background))
                    .opacity(fullscreen ? 0 : 1)
                if !fullscreen { details(playback, session, p: p, top: videoHeight, bottom: insets.bottom) }
                VideoStage(playback: playback, fullscreen: fullscreen, controlsVisible: $controlsVisible, p: p, insets: insets, size: size, expandedHeight: expandedVideoHeight, replyTo: $replyTo, onTap: toggleControls)
                    .frame(width: videoWidth, height: videoHeight)
                    .background(Color.black)
                    .gesture(dragGesture(travel: travel), including: fullscreen ? .subviews : .all)
                if p > 0.02 { strip(playback, session).opacity(Double(min(1, p * 1.4))).padding(.leading, 112).frame(height: miniHeight) }
            }
            .frame(width: size.width, height: sheetHeight, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 14 * p, style: .continuous))
            .shadow(color: .black.opacity(0.25 * p), radius: 12, y: 2)
            .offset(y: sheetY)
            .padding(.horizontal, 8 * p).frame(width: size.width)
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .position(x: size.width / 2 - insets.leading, y: size.height / 2 - insets.top)
            .animation(dragging || reduceMotion ? nil : .spring(response: 0.38, dampingFraction: 0.86), value: session.position)
            .onChange(of: landscape) { _, now in if now { controlsVisible = true; scheduleHide() } }
            .onChange(of: playback.controller.isPlaying) { _, _ in scheduleHide() }
        }
        .onAppear {
            scheduleHide()
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-fullscreen") { Orientation.request(.landscape) }
            if ProcessInfo.processInfo.arguments.contains("-minimised") { app.session.minimise() }
            #endif
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(app.session.position == .expanded ? .isModal : [])
    }

    // MARK: Details under the picture

    @ViewBuilder private func details(_ playback: PlaybackModel, _ session: PlayerSession, p: CGFloat, top: CGFloat, bottom: CGFloat) -> some View {
        VStack(spacing: 0) {
            streamerRow(playback, session)
            if (playback.ui?.chatMode ?? .compact) != .hidden, playback.isLive {
                Divider().opacity(0.4)
                ChatList(lines: playback.chat, settings: playback.chatSettings) { replyTo = $0 }
                    .background(Color.black.opacity(0.0))
                ChatComposer(playback: playback, replyTo: $replyTo)
            } else {
                Spacer()
            }
        }
        .padding(.bottom, bottom)
        .background(Color(red: 0.055, green: 0.055, blue: 0.063))
        .environment(\.colorScheme, .dark)
        .padding(.top, top)
        .opacity(Double(max(0, 1 - p * 3)))
        .allowsHitTesting(p < 0.2)
    }

    private func streamerRow(_ playback: PlaybackModel, _ session: PlayerSession) -> some View {
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
        .gesture(dragGesture(travel: 600))
    }

    // MARK: Mini strip

    private func strip(_ playback: PlaybackModel, _ session: PlayerSession) -> some View {
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
        .gesture(dragGesture(travel: 400))
    }

    // MARK: Gestures

    private func dragGesture(travel: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                dragging = true
                drag = value.translation.height
            }
            .onEnded { value in
                let session = app.session
                let projected = value.predictedEndTranslation.height
                dragging = false
                let collapsed = session.position == .collapsed
                withAnimation(reduceMotion ? nil : .spring(response: 0.38, dampingFraction: 0.86)) {
                    if collapsed {
                        // Up expands; a long, deliberate pull down closes.
                        if value.translation.height < -40 || projected < -200 { session.expand() } else if value.translation.height > 70 && projected > 220 { session.close() }
                    } else if value.translation.height > 80 || projected > 260 {
                        session.minimise()
                    }
                    drag = 0
                }
            }
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
