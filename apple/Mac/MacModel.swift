import Foundation
import Observation

/// What only the Mac window needs on top of the shared `AppModel`: the search field, the chat column and the keyboard commands that reach into the view tree.
@MainActor
@Observable
final class MacModel {
    var searchText = ""
    /// The chat column beside the picture (a side column the viewer can resize or hide).
    var showChat = true
    /// Bumped by Cmd+F; the root view moves the keyboard focus to the toolbar search field.
    private(set) var focusSearchTick = 0
    func focusSearch() { focusSearchTick += 1 }

    /// Debug launch arguments (compiled out of release builds): `-search <text>` (the snapshot arguments are handled by `MacSnapshotRunner`).
    func applyLaunchArguments() {
        #if DEBUG
        if let text = LaunchMode.argument("-search") { searchText = text }
        if ProcessInfo.processInfo.arguments.contains("-minimised") { MacRuntime.app.session.minimise() }
        #endif
    }
}
