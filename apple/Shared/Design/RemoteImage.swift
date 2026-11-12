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

/// Thumbnails and avatars: a memory cache over a 128 MB on-disk `URLCache` (Twitch image URLs are immutable per size), decoded off the main thread.
actor ImagePipeline {
    static let shared = ImagePipeline()

    private let session: URLSession
    private let memory = NSCache<NSURL, PlatformImage>()
    private var inFlight: [URL: Task<PlatformImage?, Never>] = [:]

    init() {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(memoryCapacity: 16 << 20, diskCapacity: 128 << 20, directory: nil)
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        session = URLSession(configuration: configuration)
        memory.countLimit = 300
    }

    nonisolated func cached(_ url: URL) -> PlatformImage? { memory.object(forKey: url as NSURL) }

    func image(for url: URL) async -> PlatformImage? {
        if let hit = memory.object(forKey: url as NSURL) { return hit }
        if let running = inFlight[url] { return await running.value }
        let task = Task<PlatformImage?, Never> { [session] in
            guard let (data, _) = try? await session.data(from: url) else { return nil }
            return PlatformImage(data: data)
        }
        inFlight[url] = task
        let result = await task.value
        inFlight[url] = nil
        if let result { memory.setObject(result, forKey: url as NSURL) }
        return result
    }
}

/// An image from a URL with a placeholder; demo URLs are drawn instead of downloaded.
struct RemoteImage<Placeholder: View>: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    @ViewBuilder var placeholder: () -> Placeholder

    @State private var loaded: PlatformImage?

    var body: some View {
        Group {
            if let url, DemoArt.isDemo(url) {
                DemoArt.view(for: url)
            } else if let image = loaded ?? url.flatMap({ ImagePipeline.shared.cached($0) }) {
                Image(platform: image).resizable().aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
        .task(id: url) {
            guard let url, !DemoArt.isDemo(url), loaded == nil else { return }
            loaded = await ImagePipeline.shared.image(for: url)
        }
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
