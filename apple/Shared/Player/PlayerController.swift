import AVFoundation
import Combine
import Foundation
import MediaPlayer
import Observation
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
    private var cancellables = Set<AnyCancellable>()
    private var timeObserver: Any?
    private var sleepTask: Task<Void, Never>?
    private var loopObserver: NSObjectProtocol?
    private var resumeAfterInterruption = false
    #if os(iOS)
    private var pip: AVPictureInPictureController?
    private var pipDelegateBridge: PiPDelegateBridge?
    #endif

    override init() {
        super.init()
        player.allowsExternalPlayback = true
        player.automaticallyWaitsToMinimizeStalling = true
        configureAudioSession()
        observePlayer()
        configureRemoteCommands()
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
        guard let url = Self.playableURL(urlString) else { failed = true; return }
        let item = AVPlayerItem(url: url)
        item.preferredForwardBufferDuration = 0
        observe(item: item)
        player.replaceCurrentItem(with: item)
        if startAt > 0 { player.seek(to: CMTime(seconds: startAt, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) }
        player.isMuted = muted
        if autoplay { player.play() } else { player.pause() }
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
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    func markFailed() { failed = true }

    func retry() { open(urlString: qualityId == "auto" ? masterUrl : (qualities.first { $0.id == qualityId }?.url ?? masterUrl), startAt: position, autoplay: true); failed = false }

    // MARK: Transport

    func togglePlay() { isPlaying ? player.pause() : player.play() }

    func play() { player.play() }

    func pause() { player.pause() }

    func seek(to seconds: Double) {
        player.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600))
        position = seconds
    }

    func skip(by seconds: Double) { seek(to: min(max(0, position + seconds), duration > 0 ? duration : .greatestFiniteMagnitude)) }

    func setRate(_ rate: Float) { if isPlaying { player.rate = rate } else { player.defaultRate = rate } }

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
        }.store(in: &cancellables)
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.position = time.seconds.isFinite ? time.seconds : 0
                if let item = self.player.currentItem, item.duration.isNumeric, !self.live { self.duration = item.duration.seconds }
            }
        }
    }

    private func observe(item: AVPlayerItem) {
        item.publisher(for: \.status).receive(on: DispatchQueue.main).sink { [weak self] status in
            if status == .failed { self?.failed = true }
        }.store(in: &cancellables)
        item.publisher(for: \.presentationSize).receive(on: DispatchQueue.main).sink { [weak self] size in
            self?.videoSize = size
            self?.hasPicture = size.width > 0 && size.height > 0
        }.store(in: &cancellables)
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

    private func configureAudioSession() {
        #if os(iOS) || os(tvOS)
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let type = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init)
            let options = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map(AVAudioSession.InterruptionOptions.init)
            MainActor.assumeIsolated {
                guard let self else { return }
                if type == .began { self.resumeAfterInterruption = self.isPlaying } else if type == .ended, options?.contains(.shouldResume) == true, self.resumeAfterInterruption {
                    self.player.play()
                }
            }
        }
        #endif
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.player.play() }; return .success }
        center.pauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.player.pause() }; return .success }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.togglePlay() }; return .success }
        center.skipForwardCommand.preferredIntervals = [15]
        center.skipBackwardCommand.preferredIntervals = [15]
        center.skipForwardCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.skip(by: 15) }; return .success }
        center.skipBackwardCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.skip(by: -15) }; return .success }
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
        completionHandler(true)
    }
}
#endif

extension QualityOption {
    init(_ variant: StreamVariant) {
        self.init(id: variant.id, name: variant.audioOnly ? "audio" : variant.name, url: variant.url, height: Int(variant.height), audioOnly: variant.audioOnly)
    }
}
