import Foundation
import TwitchShared

/// Runs `apply` on the main actor for every value of a Kotlin `StateFlow` until the returned task is cancelled.
/// SKIE bridges the flow to an `AsyncSequence`; the cast to `SkieSwiftStateFlow` is the one thing callers must write.
@MainActor
func observe<T>(_ flow: SkieSwiftStateFlow<T>, _ apply: @escaping @MainActor (T) -> Void) -> Task<Void, Never> {
    Task { @MainActor in
        for await value in flow { apply(value) }
    }
}

/// Owns the observation tasks and the Kotlin scope of one screen: `cancel()` stops every flow and every coroutine the presenters launched.
@MainActor
final class Lifetime {
    let scope = PresenterScope()
    private var tasks: [Task<Void, Never>] = []

    func keep(_ task: Task<Void, Never>) { tasks.append(task) }

    func cancel() {
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
        scope.cancel()
    }

    deinit { scope.cancel() }
}
