import AVFoundation
import Combine
import Foundation
import MediaPlayer
import Observation
import OSLog
import TwitchShared
#if os(iOS)
import AVKit
import UIKit
#endif

/// One quality choice of a stream. "auto" is the master playlist (adaptive); the others are a fixed variant playlist.
struct QualityOption: Identifiable, Hashable {
    let id: String
    let name: String
    let url: String
    let height: Int
    let audioOnly: Bool

    static let auto = QualityOption(id: "auto", name: "auto", url: "", height: 0, audioOnly: false)
}

/// The AVPlayer behind the player sheet: loads a Twitch HLS master playlist (or the bundled demo clip), publishes what the UI draws, and owns the
/// system integrations (audio session, Now Playing, remote commands, Picture in Picture, sleep timer, audio-only).
@MainActor
@Observable
final class PlayerController: NSObject {
    let player = AVPlayer()
    nonisolated static let log = Logger(subsystem: "com.twitchplayer.player", category: "playback")

    private(set) var isPlaying = false
    private(set) var isBuffering = false
    private(set) var hasPicture = false
    private(set) var videoSize: CGSize = .zero
    private(set) var position: Double = 0
    private(set) var duration: Double = 0
    private(set) var failed = false
    private(set) var qualities: [QualityOption] = []
    private(set) var qualityId = QualityOption.auto.id
    private(set) var audioOnly = false
    var muted = false { didSet { player.isMuted = muted } }
    /// When the sleep timer pauses playback (nil: no timer).
    private(set) var sleepEndsAt: Date?
    private(set) var pipActive = false
    private(set) var pipPossible = false

    private var masterUrl = ""
    private(set) var live = true
    private var title = ""
    private var subtitle = ""
    /// What the last `open` was asked to load: kept so a retry (and the tests) can tell a live reload from a replay resume.
    private(set) var lastOpen: (url: String, startAt: Double)?
    /// Called when the viewer taps the Picture in Picture window's "return to app" button: the app brings the player back.
    var restoreUI: (() -> Void)?

    /// Player-wide subscriptions (created once) and the current item's (replaced with each item, so they never accumulate).
    private var playerCancellables = Set<AnyCancellable>()
    private var itemCancellables = Set<AnyCancellable>()
    // The tokens below are removed in `tearDown()` and `deinit`; `nonisolated(unsafe)` because `deinit` is not main-actor isolated.
    nonisolated(unsafe) private var timeObserver: Any?
    nonisolated(unsafe) private var loopObserver: NSObjectProtocol?
    nonisolated(unsafe) private var interruptionObserver: NSObjectProtocol?
    nonisolated(unsafe) private var remoteTargets: [(command: MPRemoteCommand, token: Any)] = []
    private var sleepTask: Task<Void, Never>?
    private var resumeAfterInterruption = false
    private var sessionActive = false
    /// For the tests: how many remote-command handlers this controller has registered, and whether it holds the audio session.
    var remoteTargetCount: Int { remoteTargets.count }
    var audioSessionActive: Bool { sessionActive }
    #if os(iOS)
    private var pip: AVPictureInPictureController?
    private var pipLayer: AVPlayerLayer?
    private var pipDelegateBridge: PiPDelegateBridge?
    #endif

    override init() {
        super.init()
        player.allowsExternalPlayback = true
        player.automaticallyWaitsToMinimizeStalling = true
        observePlayer()
        configureRemoteCommands()
        observeInterruptions()
    }

    deinit {
        // `player` and the tokens are plain references: removing the observers here is safe from any thread.
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let loopObserver { NotificationCenter.default.removeObserver(loopObserver) }
        if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) }
        for target in remoteTargets { target.command.removeTarget(target.token) }
    }

    /// Releases every system hook (remote commands, observers, the audio session). The controller is unusable afterwards.
    func tearDown() {
        stop()
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        if let loopObserver { NotificationCenter.default.removeObserver(loopObserver); self.loopObserver = nil }
        if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver); self.interruptionObserver = nil }
        for target in remoteTargets { target.command.removeTarget(target.token) }
        remoteTargets.removeAll()
        playerCancellables.removeAll()
        itemCancellables.removeAll()
    }

    // MARK: Loading

    /// Loads a stream. `startAt` (seconds) resumes a replay.
    func load(masterUrl: String, variants: [QualityOption], live: Bool, title: String, subtitle: String, startAt: Double = 0, autoplay: Bool = true) {
        self.masterUrl = masterUrl
        self.live = live
        self.title = title
        self.subtitle = subtitle
        qualities = [.auto] + variants.filter { !$0.audioOnly }.sorted { $0.height > $1.height } + variants.filter(\.audioOnly)
        qualityId = QualityOption.auto.id
        audioOnly = false
        failed = false
        open(urlString: masterUrl, startAt: startAt, autoplay: autoplay)
        updateNowPlaying()
    }

    private func open(urlString: String, startAt: Double, autoplay: Bool) {
        lastOpen = (urlString, startAt)
        Self.log.info("open \(urlString.prefix(120), privacy: .public) startAt=\(startAt)")
        guard let url = Self.playableURL(urlString) else { failed = true; return }
        let item = AVPlayerItem(url: url)
        item.preferredForwardBufferDuration = 0
        #if !os(macOS)
        // The system player (the Apple TV's) names the playback from this metadata (the Mac's own Now Playing comes from `updateNowPlaying`).
        item.externalMetadata = [(AVMetadataIdentifier.commonIdentifierTitle, title), (AVMetadataIdentifier.iTunesMetadataTrackSubTitle, subtitle)].map { identifier, value in
            let metadata = AVMutableMetadataItem()
            metadata.identifier = identifier
            metadata.value = value as NSString
            metadata.extendedLanguageTag = "und"
            return metadata
        }
        #endif
        observe(item: item)
        player.replaceCurrentItem(with: item)
        if startAt > 0 { player.seek(to: CMTime(seconds: startAt, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) }
        player.isMuted = muted
        if autoplay { play() } else { player.pause() }
    }

    /// The demo URL (`demo://stream`) is the bundled test clip; everything else is a real URL.
    static func playableURL(_ string: String) -> URL? {
        if string == "demo://stream" { return Bundle.main.url(forResource: "demo_stream", withExtension: "mp4") }
        return URL(string: string)
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        sleepTask?.cancel()
        sleepEndsAt = nil
        position = 0; duration = 0; hasPicture = false; videoSize = .zero; failed = false; isPlaying = false
        itemCancellables.removeAll()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        deactivateSession()
    }

    func markFailed() { failed = true }

    /// Loads the current stream again. A live stream restarts at its live edge; a replay resumes where it stopped. The failure flag is cleared
    /// first, so a reload that fails again is reported again.
    func retry() {
        failed = false
        open(urlString: qualityId == QualityOption.auto.id ? masterUrl : (qualities.first { $0.id == qualityId }?.url ?? masterUrl), startAt: live ? 0 : position, autoplay: true)
    }

    // MARK: Transport

    func togglePlay() { isPlaying ? player.pause() : play() }

    func play() {
        activateSession()
        player.play()
    }

    func pause() { player.pause() }

    func seek(to seconds: Double) {
        player.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600))
        position = seconds
    }

    func skip(by seconds: Double) { seek(to: min(max(0, position + seconds), duration > 0 ? duration : .greatestFiniteMagnitude)) }

    /// The rate playback uses now (when playing) and every time it resumes.
    func setRate(_ rate: Float) {
        player.defaultRate = rate
        if isPlaying { player.rate = rate }
    }

    // MARK: Quality and audio-only

    func select(quality id: String) {
        guard let option = qualities.first(where: { $0.id == id }) else { return }
        qualityId = id
        audioOnly = option.audioOnly
        let resume = live ? 0 : position
        open(urlString: id == QualityOption.auto.id ? masterUrl : option.url, startAt: resume, autoplay: isPlaying || !live)
    }

    /// Audio-only on and off: the audio variant when the stream has one, the previous quality back.
    func setAudioOnly(_ on: Bool, previous: String) {
        if on, let audio = qualities.first(where: \.audioOnly) { select(quality: audio.id) } else if !on { select(quality: previous) }
    }

    // MARK: Sleep timer

    func setSleepTimer(minutes: Int?) {
        sleepTask?.cancel()
        guard let minutes else { sleepEndsAt = nil; return }
        let end = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepEndsAt = end
        sleepTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(end.timeIntervalSinceNow))
            guard !Task.isCancelled else { return }
            self?.player.pause()
            self?.sleepEndsAt = nil
        }
    }

    // MARK: Observation

    private func observePlayer() {
        player.publisher(for: \.timeControlStatus).receive(on: DispatchQueue.main).sink { [weak self] status in
            guard let self else { return }
            self.isPlaying = status == .playing
            self.isBuffering = status == .waitingToPlayAtSpecifiedRate
            self.updateNowPlaying()
        }.store(in: &playerCancellables)
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.position = time.seconds.isFinite ? time.seconds : 0
                if let item = self.player.currentItem, item.duration.isNumeric, !self.live { self.duration = item.duration.seconds }
            }
        }
    }

    private func observe(item: AVPlayerItem) {
        itemCancellables.removeAll()
        item.publisher(for: \.status).receive(on: DispatchQueue.main).sink { [weak self] status in
            guard let self else { return }
            if status == .failed {
                Self.log.error("item failed: \(item.error?.localizedDescription ?? "unknown", privacy: .public) (\((item.error as NSError?)?.code ?? 0)) \(String(describing: (item.error as NSError?)?.userInfo), privacy: .public)")
                self.failed = true
            }
        }.store(in: &itemCancellables)
        NotificationCenter.default.publisher(for: .AVPlayerItemNewErrorLogEntry, object: item).sink { _ in
            if let event = item.errorLog()?.events.last {
                Self.log.error("hls error \(event.errorStatusCode): \(event.errorComment ?? "", privacy: .public) \(event.uri ?? "", privacy: .public)")
            }
        }.store(in: &itemCancellables)
        item.publisher(for: \.presentationSize).receive(on: DispatchQueue.main).sink { [weak self] size in
            self?.videoSize = size
            self?.hasPicture = size.width > 0 && size.height > 0
        }.store(in: &itemCancellables)
        if let loopObserver { NotificationCenter.default.removeObserver(loopObserver) }
        // The demo clip loops; real streams and replays stop at the end.
        loopObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self, self.masterUrl == "demo://stream" else { return }
                self.player.seek(to: .zero)
                self.player.play()
            }
        }
    }

    // MARK: System integration

    /// The audio session is activated when playback starts and released when it stops: launching the app must not silence the viewer's music.
    private func activateSession() {
        #if os(iOS) || os(tvOS)
        guard !sessionActive else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
        sessionActive = true
        #endif
    }

    private func deactivateSession() {
        #if os(iOS) || os(tvOS)
        guard sessionActive else { return }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        sessionActive = false
        #endif
    }

    private func observeInterruptions() {
        #if os(iOS) || os(tvOS)
        interruptionObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let type = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init)
            let options = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map(AVAudioSession.InterruptionOptions.init)
            Task { @MainActor [weak self] in
                guard let self else { return }
                if type == .began {
                    self.resumeAfterInterruption = self.isPlaying
                } else if type == .ended, options?.contains(.shouldResume) == true, self.resumeAfterInterruption {
                    self.play()
                }
            }
        }
        #endif
    }

    /// The system calls remote-command handlers on its own queue: each hops to the main actor (never `assumeIsolated`).
    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        func add(_ command: MPRemoteCommand, _ action: @escaping @MainActor (PlayerController) -> Void) {
            let token = command.addTarget { [weak self] _ in
                Task { @MainActor [weak self] in if let self { action(self) } }
                return .success
            }
            remoteTargets.append((command, token))
        }
        add(center.playCommand) { $0.play() }
        add(center.pauseCommand) { $0.pause() }
        add(center.togglePlayPauseCommand) { $0.togglePlay() }
        center.skipForwardCommand.preferredIntervals = [15]
        center.skipBackwardCommand.preferredIntervals = [15]
        add(center.skipForwardCommand) { $0.skip(by: 15) }
        add(center.skipBackwardCommand) { $0.skip(by: -15) }
        center.skipForwardCommand.isEnabled = true
        center.skipBackwardCommand.isEnabled = true
    }

    private func updateNowPlaying() {
        guard player.currentItem != nil else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: subtitle,
            MPNowPlayingInfoPropertyIsLiveStream: live,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
        ]
        if !live, duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    // MARK: Picture in Picture

    #if os(iOS)
    func attach(layer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        if pipLayer === layer, pip != nil { return }
        pipLayer = layer
        let controller = AVPictureInPictureController(playerLayer: layer)
        controller?.canStartPictureInPictureAutomaticallyFromInline = true
        let bridge = PiPDelegateBridge(owner: self)
        controller?.delegate = bridge
        pip = controller
        pipDelegateBridge = bridge
        pipPossible = controller != nil
    }

    func startPictureInPicture() { if pip?.isPictureInPicturePossible == true { pip?.startPictureInPicture() } }

    func stopPictureInPicture() { pip?.stopPictureInPicture() }

    fileprivate func pipChanged(_ active: Bool) { pipActive = active }

    fileprivate func pipRestore() { restoreUI?() }
    #endif
}

#if os(iOS)
/// AVKit's delegate callbacks, forwarded to the controller on the main actor.
private final class PiPDelegateBridge: NSObject, AVPictureInPictureControllerDelegate {
    weak var owner: PlayerController?
    init(owner: PlayerController) { self.owner = owner }

    func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) {
        Task { @MainActor in owner?.pipChanged(true) }
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        Task { @MainActor in owner?.pipChanged(false) }
    }

    func pictureInPictureController(_ controller: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        // Tapping the window's "return" button: the player comes back to the full screen before the window closes.
        Task { @MainActor in
            owner?.pipRestore()
            completionHandler(true)
        }
    }
}
#endif

extension QualityOption {
    init(_ variant: StreamVariant) {
        self.init(id: variant.id, name: variant.audioOnly ? "audio" : variant.name, url: variant.url, height: Int(variant.height), audioOnly: variant.audioOnly)
    }
}
