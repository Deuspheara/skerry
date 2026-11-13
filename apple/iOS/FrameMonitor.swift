#if DEBUG
import OSLog
import QuartzCore
import UIKit

/// Debug only (`-frame-monitor`): records the main thread's frame cadence while a REAL touch drags the player sheet, phase by phase (the finger down, then the
/// spring settling), with whatever else is happening (live video, the chat). A frame whose interval is more than 1.5 times the nominal one is a hitch; the run-loop
/// observer gives the longest pass of the main thread. One line per phase goes to the log (`com.twitchplayer.bench`) and to `Caches/framemon.txt`.
@MainActor
final class FrameMonitor: NSObject {
    static let shared = FrameMonitor()
    static let enabled = ProcessInfo.processInfo.arguments.contains("-frame-monitor")
    private static let log = Logger(subsystem: "com.twitchplayer.bench", category: "frames")

    private var link: CADisplayLink?
    private var stamps: [CFTimeInterval] = []
    private var busy: [Double] = []
    private var wokeAt: CFTimeInterval = 0
    private var observer: CFRunLoopObserver?
    private var phase = ""
    private var startedAt: CFTimeInterval = 0

    func start(_ phase: String) {
        guard Self.enabled else { return }
        finish()
        self.phase = phase
        stamps = []; busy = []; wokeAt = 0
        startedAt = CACurrentMediaTime()
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        let activities: CFRunLoopActivity = [.afterWaiting, .beforeWaiting]
        observer = CFRunLoopObserverCreateWithHandler(nil, activities.rawValue, true, 0) { [weak self] _, activity in
            MainActor.assumeIsolated {
                guard let self else { return }
                if activity == .afterWaiting { self.wokeAt = CACurrentMediaTime() } else if self.wokeAt > 0 { self.busy.append((CACurrentMediaTime() - self.wokeAt) * 1000); self.wokeAt = 0 }
            }
        }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
    }

    /// Ends the current phase (if any) and starts the next one.
    func next(_ phase: String) { start(phase) }

    func stop() { finish() }

    @objc private func tick(_ link: CADisplayLink) { stamps.append(link.timestamp) }

    private func finish() {
        guard link != nil else { return }
        link?.invalidate(); link = nil
        if let observer { CFRunLoopRemoveObserver(CFRunLoopGetMain(), observer, .commonModes) }
        observer = nil
        let deltas = zip(stamps.dropFirst(), stamps).map { ($0 - $1) * 1000 }
        guard deltas.count >= 3 else { return }
        let sorted = deltas.sorted()
        let nominal = 1000.0 / Double(UIScreen.main.maximumFramesPerSecond)
        func pct(_ p: Double) -> Double { sorted[min(sorted.count - 1, Int(Double(sorted.count) * p))] }
        let hitches = deltas.filter { $0 > nominal * 1.5 }.count
        let dropped = deltas.reduce(0) { $0 + max(0, Int(($1 / nominal).rounded()) - 1) }
        let wall = (CACurrentMediaTime() - startedAt) * 1000
        let work = busy.sorted()
        let worstAt = deltas.firstIndex(of: sorted.last ?? 0) ?? 0
        let text = String(format: "%@: worstAt=%d/%d frames=%d nominal=%.1fms p50=%.1f p95=%.1f p99=%.1f max=%.1f hitches=%d dropped=%d (%.1f%%) | busy %.0f%% longest pass %.1fms", phase, worstAt, deltas.count, deltas.count, nominal, pct(0.5), pct(0.95), pct(0.99), sorted.last ?? 0, hitches, dropped, Double(dropped) * 100 / Double(deltas.count + dropped), work.reduce(0, +) * 100 / wall, work.last ?? 0)
        Self.log.notice("\(text, privacy: .public)")
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("framemon.txt")
        let existing = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        try? (existing + text + "\n").write(to: url, atomically: true, encoding: .utf8)
    }
}
#endif
