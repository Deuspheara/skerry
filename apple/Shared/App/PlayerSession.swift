import Foundation
import Observation
import TwitchShared

/// What the player was asked to play.
enum PlaybackTarget: Hashable {
    case live(login: String, name: String, title: String, avatarUrl: String?, thumbnailUrl: String)
    case replay(id: String, title: String, channelLogin: String, channelName: String, thumbnailUrl: String)
    case clip(slug: String, title: String, channelLogin: String, channelName: String, thumbnailUrl: String)

    var title: String {
        switch self {
        case .live(_, _, let title, _, _), .replay(_, let title, _, _, _), .clip(_, let title, _, _, _): title
        }
    }

    var channelName: String {
        switch self {
        case .live(_, let name, _, _, _): name
        case .replay(_, _, _, let name, _), .clip(_, _, _, let name, _): name
        }
    }

    var channelLogin: String {
        switch self {
        case .live(let login, _, _, _, _): login
        case .replay(_, _, let login, _, _), .clip(_, _, let login, _, _): login
        }
    }

    var thumbnailUrl: String {
        switch self {
        case .live(_, _, _, _, let url), .replay(_, _, _, _, let url), .clip(_, _, _, _, let url): url
        }
    }
}

/// Where the player sheet rests: expanded over the app, or docked as a strip above the tab bar. No target means no player.
enum SheetPosition: String { case expanded, collapsed }

/// The player session: one target at a time, living above the tab navigation so it survives page changes.
@MainActor
@Observable
final class PlayerSession {
    let playback: PlaybackModel
    private(set) var target: PlaybackTarget?
    var position: SheetPosition = .expanded
    /// Bumped each time a target is opened, so the player view starts a fresh load even for the same channel.
    private(set) var generation = 0

    init(graph: TwitchGraph) {
        playback = PlaybackModel(graph: graph)
        playback.controller.restoreUI = { [weak self] in self?.expand() }
    }

    func open(_ next: PlaybackTarget) {
        if target == next { position = .expanded; return }
        target = next
        position = .expanded
        generation += 1
        playback.start(next)
    }

    func expand() { if target != nil { position = .expanded } }

    func minimise() { if target != nil { position = .collapsed } }

    func close() {
        playback.end()
        target = nil
        position = .expanded
    }
}
