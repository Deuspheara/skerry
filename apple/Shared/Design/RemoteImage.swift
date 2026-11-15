import SwiftUI

#if canImport(UIKit)
import UIKit
typealias PlatformImage = UIImage
extension Image { init(platform image: PlatformImage) { self.init(uiImage: image) } }
#else
import AppKit
typealias PlatformImage = NSImage
extension Image { init(platform image: PlatformImage) { self.init(nsImage: image) } }
#endif

/// Thumbnails, avatars and category art: a memory cache over our own on-disk store (the Caches directory, 200 MB, least recently used pruned),
/// so a launch shows the pictures of the last session at once instead of downloading them again. The disk store does not depend on the CDN's
/// cache headers. A picture is fetched once per URL (Twitch image URLs are immutable per size); category art, which hardly ever changes, is
/// therefore shown from disk every launch.
actor ImagePipeline {
    static let shared = ImagePipeline()

    private let session: URLSession
    private let memory = NSCache<NSURL, PlatformImage>()
    private var inFlight: [URL: Task<PlatformImage?, Never>] = [:]
    private let directory: URL
    private static let diskLimit = 200 << 20

    init() {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = nil
        session = URLSession(configuration: configuration)
        memory.countLimit = 400
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        directory = base.appendingPathComponent("images", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        Task { await self.prune() }
    }

    nonisolated func cached(_ url: URL) -> PlatformImage? { memory.object(forKey: url as NSURL) }

    private func file(for url: URL) -> URL {
        var hash: UInt64 = 5381
        for byte in url.absoluteString.utf8 { hash = (hash &* 33) ^ UInt64(byte) }
        let tail = url.absoluteString.utf8.count
        return directory.appendingPathComponent(String(hash, radix: 16) + "-" + String(tail))
    }

    func image(for url: URL) async -> PlatformImage? {
        if let hit = memory.object(forKey: url as NSURL) { return hit }
        if let running = inFlight[url] { return await running.value }
        let path = file(for: url)
        let task = Task<PlatformImage?, Never> { [session] in
            if let data = try? Data(contentsOf: path), let image = PlatformImage(data: data) {
                try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: path.path)
                return image
            }
            guard let (data, response) = try? await session.data(from: url),
                  (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true,
                  let image = PlatformImage(data: data) else { return nil }
            try? data.write(to: path, options: .atomic)
            return image
        }
        inFlight[url] = task
        let result = await task.value
        inFlight[url] = nil
        if let result { memory.setObject(result, forKey: url as NSURL) }
        return result
    }

    /// Keeps the store under its limit, dropping the pictures not shown for the longest time.
    private func prune() {
        let keys: [URLResourceKey] = [.contentModificationDateKey, .fileSizeKey]
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys) else { return }
        var entries = files.compactMap { url -> (URL, Date, Int)? in
            guard let values = try? url.resourceValues(forKeys: Set(keys)) else { return nil }
            return (url, values.contentModificationDate ?? .distantPast, values.fileSize ?? 0)
        }
        var total = entries.reduce(0) { $0 + $1.2 }
        guard total > Self.diskLimit else { return }
        entries.sort { $0.1 < $1.1 }
        for entry in entries where total > Self.diskLimit * 3 / 4 {
            try? FileManager.default.removeItem(at: entry.0)
            total -= entry.2
        }
    }
}

/// An image from a URL with a placeholder; demo URLs are drawn instead of downloaded. A picture that arrives after the view is shown fades in
/// over the placeholder (or over the previous picture when the URL changes); one that is already cached appears at once.
struct RemoteImage<Placeholder: View>: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    @ViewBuilder var placeholder: () -> Placeholder

    @State private var loaded: (url: URL, image: PlatformImage)?

    var body: some View {
        Group {
            if let url, DemoArt.isDemo(url) {
                DemoArt.view(for: url)
            } else if let image = shown {
                Image(platform: image).resizable().aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
                    .id(ObjectIdentifier(image))
            } else {
                placeholder()
            }
        }
        .animation(.easeOut(duration: 0.2), value: shown.map(ObjectIdentifier.init))
        .task(id: url) {
            guard let url, !DemoArt.isDemo(url), loaded?.url != url, ImagePipeline.shared.cached(url) == nil else { return }
            if let image = await ImagePipeline.shared.image(for: url) { loaded = (url, image) }
        }
    }

    /// The picture for the current URL; while a new URL loads, the previous one stays instead of flashing the placeholder.
    private var shown: PlatformImage? {
        if let url, let hit = ImagePipeline.shared.cached(url) { return hit }
        return loaded?.image
    }
}

extension RemoteImage where Placeholder == Color {
    init(url: URL?, contentMode: ContentMode = .fill) {
        self.init(url: url, contentMode: contentMode) { Theme.surfaceHigh }
    }
}

/// A round avatar with the channel's initial while the picture loads (or when there is none).
struct Avatar: View {
    let url: String?
    let name: String
    var size: CGFloat = 36

    var body: some View {
        RemoteImage(url: url.flatMap(URL.init(string:))) {
            ZStack {
                Theme.accentDeep.opacity(0.35)
                Text(String(name.prefix(1)).uppercased()).font(.system(size: size * 0.42, weight: .semibold)).foregroundStyle(.primary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}
