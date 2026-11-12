import Foundation
import TwitchShared

/// Swift view of the shared `Load<T>`: what a list screen shows. Kotlin generics are erased in Objective-C, so the payload is cast here once.
enum LoadState<Content> {
    case loading
    case signedOut
    case failed
    /// `refreshFailed`: the content is a stored or older copy because the last refresh failed. `loadedAtMs` is when it was fetched (0 = unknown).
    case ready(Content, loadedAtMs: Int64, refreshFailed: Bool)

    var content: Content? {
        if case .ready(let content, _, _) = self { return content }
        return nil
    }

    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
}

extension LoadState {
    init(_ load: any Load, as type: Content.Type) {
        switch onEnum(of: load) {
        case .loading: self = .loading
        case .signedOut: self = .signedOut
        case .failed: self = .failed
        case .ready(let ready):
            if let content = ready.data as? Content {
                self = .ready(content, loadedAtMs: ready.loadedAtMs, refreshFailed: ready.refreshFailed)
            } else {
                self = .loading
            }
        }
    }
}
