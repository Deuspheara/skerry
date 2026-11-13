import Foundation
import Observation
import SwiftUI
import TwitchShared

/// Why a replay, a clip or a stream could not start, as the player says it (the Android strings): a removed replay, a subscriber-only one, a refused or unavailable one,
/// a failure of the network. It comes from the Kotlin exception the resolver threw (they cross the bridge as `NSError`s: the Swift side must never let one end the app).
enum PlaybackProblem: Equatable {
    case gone, subscribersOnly, unavailable, network, offline

    var title: LocalizedStringKey {
        switch self {
        case .gone: "vod_gone"
        case .subscribersOnly: "vod_subs"
        case .unavailable, .network: "vod_failed"
        case .offline: "phone_channel_offline"
        }
    }

    var line: LocalizedStringKey? {
        switch self {
        case .gone: "vod_gone_line"
        case .subscribersOnly: "vod_subs_line"
        case .unavailable: "vod_failed_line"
        case .network: "vod_failed_net_line"
        case .offline: nil
        }
    }

    /// The Kotlin exception inside the `NSError` of a throwing suspend call decides; anything else is a network-type failure.
    static func from(_ error: Error) -> PlaybackProblem {
        let thrown = (error as NSError).userInfo["KotlinException"]
        if let access = thrown as? VodAccessException {
            switch access.failure {
            case .notFound: return .gone
            case .subscriberOnly: return .subscribersOnly
            default: return .unavailable
            }
        }
        if thrown is ChannelOfflineException { return .offline }
        return .network
    }
}

/// What the player sheet shows and plays: the AVPlayer controller plus, for a live channel, the shared `PlayerPresenter` (chat, emotes, room modes, stream info).
/// One instance lives for the whole app: opening another target replaces the previous one (and disconnects its chat).
@MainActor
@Observable
final class PlaybackModel {
    let controller = PlayerController()

    private(set) var ui: PlayerUiState?
    private(set) var chat: [ChatLine] = []
    /// While a finger drags the player sheet the chat is held: incoming batches are kept aside (only the latest matters, it is the whole list) and published when
    /// the finger lifts, so no view that shows the chat is invalidated per message during the gesture.
    var holdChat = false {
        didSet {
            if !holdChat, let held = heldChat {
                heldChat = nil
                chat = held
            }
        }
    }
    @ObservationIgnored private var heldChat: [ChatLine]?
    @ObservationIgnored private var current: PlaybackTarget?
    private(set) var picker: EmotePickerState?
    private(set) var room: RoomState?
    private(set) var chatSettings: ChatSettings?
    private(set) var offline = false
    /// Set when starting failed: the sheet says why (`PlaybackProblem`) with a retry.
    private(set) var problem: PlaybackProblem?
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
        current = target
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
        ui = nil; chat = []; picker = nil; room = nil; offline = false; problem = nil; replayId = nil; sendProblem = nil
    }

    // MARK: Live

    private func startLive(login: String, name: String, title: String, autoplay: Bool) {
        let presenter = graph.player(login: login, scope: lifetime.scope)
        self.presenter = presenter
        lifetime.keep(observe(presenter.state as SkieSwiftStateFlow<PlayerUiState>) { [weak self] in self?.ui = $0 })
        lifetime.keep(observe(presenter.chat as SkieSwiftStateFlow<[ChatLine]>) { [weak self] lines in
            guard let self else { return }
            if self.holdChat { self.heldChat = lines } else { self.chat = lines }
        })
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
                let reason = PlaybackProblem.from(error)
                if reason == .offline { self.offline = true } else { self.problem = reason; self.controller.markFailed() }
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
        // Nothing was ever loaded (the address could not be resolved): resolve again from the target. Otherwise the player reloads what it had.
        if (offline || problem != nil), let current { start(current) } else { controller.retry() }
    }

    // MARK: Replays and clips

    private func startReplay(id: String, title: String, channelName: String, autoplay: Bool) {
        replayId = id
        offline = false; problem = nil
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
                self.problem = PlaybackProblem.from(error)
                self.controller.markFailed()
            }
        }
    }

    private func startClip(slug: String, title: String, channelName: String, autoplay: Bool) {
        offline = false; problem = nil
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let sources = try await self.graph.playback.clip(slug: slug)
                guard let best = (sources as [ClipSource]).max(by: { $0.height < $1.height }) else { throw CancellationError() }
                self.controller.load(masterUrl: best.url, variants: [], live: false, title: title, subtitle: channelName, autoplay: autoplay)
            } catch {
                self.problem = PlaybackProblem.from(error)
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
