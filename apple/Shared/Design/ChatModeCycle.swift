import TwitchShared

extension ChatMode {
    /// The chat button: portrait toggles the panel under the picture; landscape cycles hidden, side panel, over the picture (as on the Android phone).
    func cycled(landscape: Bool) -> ChatMode {
        guard landscape else { return self == .hidden ? .compact : .hidden }
        switch self {
        case .hidden: return .compact
        case .compact: return .overlay
        default: return .hidden
        }
    }
}
