import ImageIO
import SwiftUI
import TwitchShared
import UIKit

/// Decodes (animated) GIF/WebP/APNG into a looping `UIImage` with ImageIO, cached by URL. Stills decode to their first frame.
actor EmoteLoader {
    static let shared = EmoteLoader()
    private var cache: [URL: UIImage] = [:]
    private var order: [URL] = []
    private var inFlight: [URL: Task<UIImage?, Never>] = [:]
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(memoryCapacity: 8 << 20, diskCapacity: 64 << 20, directory: nil)
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        return URLSession(configuration: configuration)
    }()

    func image(_ url: URL, animated: Bool) async -> UIImage? {
        let key = animated ? url : url.appendingPathExtension("still")
        if let hit = cache[key] { return hit }
        if let running = inFlight[key] { return await running.value }
        let task = Task<UIImage?, Never> { [session] in
            guard let (data, _) = try? await session.data(from: url) else { return nil }
            return Self.decode(data, animated: animated)
        }
        inFlight[key] = task
        let image = await task.value
        inFlight[key] = nil
        if let image {
            cache[key] = image
            order.append(key)
            if order.count > 400 { cache[order.removeFirst()] = nil }
        }
        return image
    }

    nonisolated static func decode(_ data: Data, animated: Bool) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let count = CGImageSourceGetCount(source)
        guard animated, count > 1 else { return CGImageSourceCreateImageAtIndex(source, 0, nil).map { UIImage(cgImage: $0) } }
        var frames: [UIImage] = []
        var total = 0.0
        for index in 0..<count {
            guard let frame = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(UIImage(cgImage: frame))
            let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
            let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            let webp = properties?[kCGImagePropertyWebPDictionary] as? [CFString: Any]
            let delay = (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (webp?[kCGImagePropertyWebPUnclampedDelayTime] as? Double) ?? 0.08
            total += max(0.02, delay)
        }
        return UIImage.animatedImage(with: frames, duration: total)
    }
}

/// An emote or badge image: drawn for demo URLs, loaded for real ones, animated when `animate` is on and the emote has an animated version.
struct EmoteImage: View {
    let url: String
    var animatedUrl: String?
    var animate = false
    var height: CGFloat = 22
    var aspect: CGFloat = 1

    @State private var image: UIImage?

    var body: some View {
        let target = (animate ? animatedUrl : nil) ?? url
        Group {
            if let parsed = URL(string: target), DemoArt.isDemo(parsed) {
                DemoArt.view(for: parsed)
            } else if let image {
                if image.images != nil { AnimatedImageView(image: image) } else { Image(uiImage: image).resizable().scaledToFit() }
            } else {
                Color.clear
            }
        }
        .frame(width: height * max(aspect, 0.2), height: height)
        .task(id: target) {
            guard let parsed = URL(string: target), !DemoArt.isDemo(parsed) else { return }
            image = await EmoteLoader.shared.image(parsed, animated: animate && animatedUrl != nil)
        }
        .accessibilityHidden(true)
    }
}

private struct AnimatedImageView: UIViewRepresentable {
    let image: UIImage

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView(image: image)
        view.contentMode = .scaleAspectFit
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) { if view.image !== image { view.image = image } }
}
