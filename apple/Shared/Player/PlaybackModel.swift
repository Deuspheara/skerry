import Foundation
import Observation
import SwiftUI
import TwitchShared

/// What the player sheet shows and plays: the AVPlayer controller plus, for a live channel, the shared `PlayerPresenter` (chat, emotes, room modes, stream info).
/// One instance lives for the whole app: opening another target replaces the previous one (and disconnects its chat).
@MainActor
@Observable
final class PlaybackModel {
    let controller = PlayerController()

    private(set) var ui: PlayerUiState?
    private(set) var chat: [ChatLine] = []
    private(set) var picker: EmotePickerState?
    private(set) var room: RoomState?
    private(set) var chatSettings: ChatSettings?
    private(set) var offline = false
    private(set) var sendProblem: LocalizedStringKey?
    /// The quality the viewer last picked before audio-only (what "audio-only off" returns to).
    private var previousQuality = QualityOption.auto.id

    private let graph: TwitchGraph
    private var lifetime = Lifetime()
    private var presenter: PlayerPresenter?
    private var loadTask: Task<Void, Never>?
    private var progressTask: Task<Void, Never>?
    private var replayId: String?

    init(graph: TwitchGraph) { self.graph = graph }

    var isLive: Bool { presenter != nil }

    func start(_ target: PlaybackTarget, autoplay: Bool = true) {
        end()
        lifetime = Lifetime()
        switch target {
        case .live(let login, let name, let title, _, _): startLive(login: login, name: name, title: title, autoplay: autoplay)
        case .replay(let id, let title, _, let channelName, _): startReplay(id: id, title: title, channelName: channelName, autoplay: autoplay)
        case .clip(let slug, let title, _, let channelName, _): startClip(slug: slug, title: title, channelName: channelName, autoplay: autoplay)
        }
    }

    func end() {
        saveProgress()
        loadTask?.cancel(); progressTask?.cancel()
        presenter?.onEvent(event: PlayerEventDetach.shared)
        lifetime.cancel()
        presenter = nil
        controller.stop()
        ui = nil; chat = []; picker = nil; room = nil; offline = false; replayId = nil; sendProblem = nil
    }

    // MARK: Live

    private func startLive(login: String, name: String, title: String, autoplay: Bool) {
        let presenter = graph.player(login: login, scope: lifetime.scope)
        self.presenter = presenter
        lifetime.keep(observe(presenter.state as SkieSwiftStateFlow<PlayerUiState>) { [weak self] in self?.ui = $0 })
        lifetime.keep(observe(presenter.chat as SkieSwiftStateFlow<[ChatLine]>) { [weak self] in self?.chat = $0 })
        lifetime.keep(observe(presenter.emotePicker as SkieSwiftStateFlow<EmotePickerState>) { [weak self] in self?.picker = $0 })
        lifetime.keep(observe(presenter.room as SkieSwiftStateFlow<RoomState>) { [weak self] in self?.room = $0 })
        lifetime.keep(observe(presenter.chatSettings as SkieSwiftStateFlow<ChatSettings>) { [weak self] in self?.chatSettings = $0 })
        lifetime.keep(Task { @MainActor [weak self] in
            for await result in presenter.sent as SkieSwiftSharedFlow<SendResult> { self?.handle(sent: result) }
        })
        lifetime.keep(Task { @MainActor [weak self] in
            for await command in presenter.commands as SkieSwiftSharedFlow<PlayerCommand> { self?.handle(command: command) }
        })
        presenter.onEvent(event: PlayerEventAttach(login: login))
        offline = false
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let stream = try await self.graph.playback.live(channel: login)
                let variants = (stream.variants as [StreamVariant]).map(QualityOption.init)
                self.controller.load(masterUrl: stream.masterUrl, variants: variants, live: true, title: title, subtitle: name, autoplay: autoplay)
            } catch {
                self.offline = true
            }
        }
    }

    private func handle(sent result: SendResult) {
        switch onEnum(of: result) {
        case .sent: sendProblem = nil
        case .needsSignIn: sendProblem = "chat_needs_signin"
        case .failed, .dropped: sendProblem = "chat_send_failed"
        }
    }

    private func handle(command: PlayerCommand) {
        switch onEnum(of: command) {
        case .play: controller.play()
        case .pause: controller.pause()
        case .toggleMute: controller.muted.toggle()
        }
    }

    func send(_ text: String, replyTo: String?) {
        presenter?.onEvent(event: PlayerEventSendChat(text: text, replyTo: replyTo))
    }

    func pickedEmote(_ name: String) { presenter?.onEvent(event: PlayerEventPickedEmote(name: name)) }

    func updateChat(_ transform: @escaping (ChatSettings) -> ChatSettings) { presenter?.onEvent(event: PlayerEventUpdateChatSettings(transform: transform)) }

    func setChatMode(_ mode: ChatMode) { presenter?.onEvent(event: PlayerEventSetChatMode(mode: mode)) }

    func setPreferredQuality(_ id: String) {
        if id != previousQuality, !(controller.qualities.first { $0.id == id }?.audioOnly ?? false) { previousQuality = id }
        controller.select(quality: id)
        presenter?.onEvent(event: PlayerEventSetPreferredQuality(id: id))
    }

    func setAudioOnly(_ on: Bool) { controller.setAudioOnly(on, previous: previousQuality) }

    func retry() {
        if offline, let login = ui?.login, !login.isEmpty { start(.live(login: login, name: login, title: login, avatarUrl: nil, thumbnailUrl: "")) } else { controller.retry() }
    }

    // MARK: Replays and clips

    private func startReplay(id: String, title: String, channelName: String, autoplay: Bool) {
        replayId = id
        offline = false
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let vod = try await self.graph.playback.replay(vodId: id)
                let saved = try await self.graph.replayProgress.position(vodId: id)
                let variants = (vod.variants as [StreamVariant]).map(QualityOption.init)
                self.controller.load(masterUrl: vod.masterUrl, variants: variants, live: false, title: title, subtitle: channelName, startAt: Double(saved) / 1000, autoplay: autoplay)
                self.progressTask = Task { @MainActor [weak self] in
                    while !Task.isCancelled {
                        try? await Task.sleep(for: .seconds(10))
                        self?.saveProgress()
                    }
                }
            } catch {
                self.controller.markFailed()
            }
        }
    }

    private func startClip(slug: String, title: String, channelName: String, autoplay: Bool) {
        offline = false
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let sources = try await self.graph.playback.clip(slug: slug)
                guard let best = (sources as [ClipSource]).max(by: { $0.height < $1.height }) else { throw CancellationError() }
                self.controller.load(masterUrl: best.url, variants: [], live: false, title: title, subtitle: channelName, autoplay: autoplay)
            } catch {
                self.controller.markFailed()
            }
        }
    }

    /// Where the viewer is in the replay, written so "continue watching" can resume it.
    func saveProgress() {
        guard let id = replayId, controller.duration > 0 else { return }
        let position = Int64(controller.position * 1000), duration = Int64(controller.duration * 1000)
        Task { try? await graph.replayProgress.save(vodId: id, positionMs: position, durationMs: duration) }
    }
}
