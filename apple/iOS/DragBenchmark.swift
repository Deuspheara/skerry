#if DEBUG
import OSLog
import QuartzCore
import UIKit

/// Debug only (`-drag-benchmark`): drives the player sheet through scripted minimise / expand drags, one synthetic touch sample per display frame,
/// and records the main thread's frame cadence from the display link, so a change to the drag can be judged by numbers, not by eye.
/// A frame whose interval is more than 1.5 times the nominal one is a hitch (the main thread was busy: layout, view bodies, the chat); `dropped` counts
/// the frames those intervals swallowed. The summary goes to the log (`com.twitchplayer.bench`) and to `Caches/dragbench.txt`.
@MainActor
final class DragBenchmark: NSObject {
    static let log = Logger(subsystem: "com.twitchplayer.bench", category: "drag")
    private static var running: DragBenchmark?

    private var link: CADisplayLink?
    private var timestamps: [CFTimeInterval] = []
    private var onFrame: ((CFTimeInterval) -> Void)?
    /// Main run loop work: each iteration's time between waking up and going back to sleep (view bodies, layout, display link callbacks).
    private var busy: [Double] = []
    private var wokeAt: CFTimeInterval = 0
    private var observer: CFRunLoopObserver?
    private var startedAt: CFTimeInterval = 0

    /// One scripted drag: translation samples (points, positive = down) at one per frame, then the release velocity (points per second).
    struct Step { let samples: [CGFloat]; let velocity: CGFloat; let settle: Int }

    /// `begin`, `changed(translation)` per frame, `ended(translation, velocity)`; the script alternates minimise and expand `cycles` times.
    static func run(label: String, travel: CGFloat, cycles: Int = 3, begin: @escaping () -> Void, changed: @escaping (CGFloat) -> Void, ended: @escaping (CGFloat, CGFloat) -> Void) {
        guard running == nil else { return }
        let bench = DragBenchmark()
        running = bench
        var steps: [Step] = []
        let frames = 40
        let ease: (Double) -> CGFloat = { t in CGFloat(t * t * (3 - 2 * t)) }
        for _ in 0..<cycles {
            steps.append(Step(samples: (1...frames).map { ease(Double($0) / Double(frames)) * travel * 0.7 }, velocity: 1_600, settle: 50))
            steps.append(Step(samples: (1...frames).map { -ease(Double($0) / Double(frames)) * travel * 0.7 }, velocity: -1_600, settle: 50))
        }
        var stepIndex = 0, sample = 0, settle = 0, started = false
        bench.start { _ in
            guard stepIndex < steps.count else { bench.finish(label: label); return }
            let step = steps[stepIndex]
            if sample < step.samples.count {
                if !started { begin(); started = true }
                changed(step.samples[sample])
                sample += 1
            } else if started {
                ended(step.samples.last ?? 0, step.velocity)
                started = false
            } else if settle < step.settle {
                settle += 1
            } else {
                stepIndex += 1; sample = 0; settle = 0
            }
        }
    }

    private func start(_ frame: @escaping (CFTimeInterval) -> Void) {
        onFrame = frame
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        startedAt = CACurrentMediaTime()
        let activities: CFRunLoopActivity = [.afterWaiting, .beforeWaiting]
        observer = CFRunLoopObserverCreateWithHandler(nil, activities.rawValue, true, 0) { [weak self] _, activity in
            MainActor.assumeIsolated {
                guard let self else { return }
                if activity == .afterWaiting { self.wokeAt = CACurrentMediaTime() } else if self.wokeAt > 0 { self.busy.append((CACurrentMediaTime() - self.wokeAt) * 1000); self.wokeAt = 0 }
            }
        }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
    }

    @objc private func tick(_ link: CADisplayLink) {
        timestamps.append(link.timestamp)
        onFrame?(link.timestamp)
    }

    private func finish(label: String) {
        link?.invalidate()
        if let observer { CFRunLoopRemoveObserver(CFRunLoopGetMain(), observer, .commonModes) }
        let wall = (CACurrentMediaTime() - startedAt) * 1000
        let work = busy.sorted()
        let loopP95 = work.isEmpty ? 0 : work[min(work.count - 1, Int(Double(work.count) * 0.95))]
        let deltas = zip(timestamps.dropFirst(), timestamps).map { ($0 - $1) * 1000 }.sorted()
        guard !deltas.isEmpty else { return }
        let nominal = deltas[deltas.count / 2]
        func percentile(_ p: Double) -> Double { deltas[min(deltas.count - 1, Int(Double(deltas.count) * p))] }
        let hitches = deltas.filter { $0 > nominal * 1.5 }
        let dropped = deltas.reduce(0) { $0 + max(0, Int(($1 / nominal).rounded()) - 1) }
        let text = String(format: "%@: frames=%d nominal=%.1fms p50=%.1f p95=%.1f p99=%.1f max=%.1f hitches=%d dropped=%d (%.1f%%) | main-thread busy %.0f%% of wall, loop p95=%.1fms max=%.1fms", label, deltas.count, nominal, percentile(0.5), percentile(0.95), percentile(0.99), deltas.last ?? 0, hitches.count, dropped, Double(dropped) * 100 / Double(deltas.count + dropped), work.reduce(0, +) * 100 / wall, loopP95, work.last ?? 0)
        Self.log.notice("\(text, privacy: .public)")
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("dragbench.txt")
        let existing = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        try? (existing + text + "\n").write(to: url, atomically: true, encoding: .utf8)
        Self.running = nil
    }
}
#endif
