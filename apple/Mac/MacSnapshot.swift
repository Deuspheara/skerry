import AppKit
import SwiftUI

/// Debug only: `-snapshot <path> [-snapshot-delay <seconds>] [-snapshot-size <w>x<h>] [-snapshot-view main|settings] [-snapshot-mode display|layer]` renders the window's contents to a PNG and quits, so screenshots
/// need no screen (a headless machine has none to capture). It hosts the same root view in its own window. Compiled out of release builds.
@MainActor
enum MacSnapshotRunner {
    private static var window: NSWindow?

    /// True in a debug run launched with `-snapshot`.
    static var isActive: Bool {
        #if DEBUG
        LaunchMode.argument("-snapshot") != nil
        #else
        false
        #endif
    }

    static func startIfRequested() {
        #if DEBUG
        guard let path = LaunchMode.argument("-snapshot") else { return }
        let size = (LaunchMode.argument("-snapshot-size") ?? "1280x800").split(separator: "x").compactMap { Double($0) }
        let (width, height) = (size.first ?? 1280, size.last ?? 800)
        let delay = Double(LaunchMode.argument("-snapshot-delay") ?? "") ?? 6
        DispatchQueue.main.async {
            NSApplication.shared.setActivationPolicy(.regular)
            let app = MacRuntime.app, mac = MacRuntime.mac
            app.applyLaunchArguments()
            mac.applyLaunchArguments()
            let scheme = (ThemeChoice(rawValue: UserDefaults.standard.string(forKey: ThemeChoice.key) ?? "") ?? .system).scheme
            // `-snapshot-view settings` renders the Settings window instead of the main window.
            // Without a display there is no display link, so animations and transitions never advance: the snapshot would show their first frame.
            let root: AnyView = LaunchMode.argument("-snapshot-view") == "settings"
                ? AnyView(MacSettingsView().environment(app).tint(Theme.accent).preferredColorScheme(scheme))
                : AnyView(MacRoot().environment(app).environment(mac).tint(Theme.accent).preferredColorScheme(scheme).transaction { $0.disablesAnimations = true })
            let controller = NSHostingController(rootView: root)
            let window = NSWindow(contentViewController: controller)
            window.setContentSize(NSSize(width: width, height: height))
            window.styleMask = [.titled, .closable, .resizable, .fullSizeContentView]
            window.title = "TwitchPlayer"
            NSApp.activate()
            window.makeKeyAndOrderFront(nil)
            window.orderFrontRegardless()
            Self.window = window
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(delay))
                write(window, to: path)
                NSApp.terminate(nil)
            }
        }
        #endif
    }

    #if DEBUG
    private static func write(_ window: NSWindow, to path: String) {
        // No display link drives a window that is never on screen: flush layout, drawing and Core Animation by hand.
        window.contentView?.layoutSubtreeIfNeeded()
        window.displayIfNeeded()
        CATransaction.flush()
        guard let view = window.contentView?.superview ?? window.contentView else { NSLog("snapshot: no content view"); return }
        NSLog("snapshot: view bounds %@ visible %d", NSStringFromRect(view.bounds), window.isVisible ? 1 : 0)
        let rep: NSBitmapImageRep
        if LaunchMode.argument("-snapshot-mode") == "layer" {
            // The layer tree (what the window server would composite) keeps pushed pages, overlays and everything SwiftUI animates in; it loses the
            // sidebar (vibrancy), which comes out white.
            view.wantsLayer = true
            let scale = 2.0
            guard let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(view.bounds.width * scale), pixelsHigh: Int(view.bounds.height * scale), bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0),
                  let context = NSGraphicsContext(bitmapImageRep: bitmap) else { NSLog("snapshot: no bitmap context"); return }
            context.cgContext.scaleBy(x: scale, y: scale)
            view.layer?.render(in: context.cgContext)
            // The sidebar is vibrancy, which the layer tree loses (it comes out white): paint it from the `cacheDisplay` pass instead.
            if let display = view.bitmapImageRepForCachingDisplay(in: view.bounds) {
                view.cacheDisplay(in: view.bounds, to: display)
                let sidebar = NSRect(x: 0, y: 0, width: 210, height: view.bounds.height)
                if let image = display.cgImage?.cropping(to: CGRect(x: 0, y: 0, width: sidebar.width * CGFloat(display.pixelsWide) / view.bounds.width, height: CGFloat(display.pixelsHigh))) {
                    context.cgContext.draw(image, in: sidebar)
                }
            }
            rep = bitmap
        } else {
            // `cacheDisplay` draws the view hierarchy without the window server's compositing: vibrancy (the sidebar's selection, the Settings tab bar)
            // is not reproduced and pushed pages or overlays that SwiftUI composites in layers can be missing; video layers are black.
            guard let bitmap = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { NSLog("snapshot: no bitmap rep"); return }
            view.cacheDisplay(in: view.bounds, to: bitmap)
            rep = bitmap
        }
        guard let data = rep.representation(using: .png, properties: [:]) else { NSLog("snapshot: png failed"); return }
        // The app is sandboxed: the PNG goes to the container's temporary directory (see apple/scripts/mac-run.sh, which copies it out).
        let target = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(URL(fileURLWithPath: path).lastPathComponent)
        do { try data.write(to: target); NSLog("snapshot: wrote %@", target.path) } catch { NSLog("snapshot: %@", "\(error)") }
    }
    #endif
}
