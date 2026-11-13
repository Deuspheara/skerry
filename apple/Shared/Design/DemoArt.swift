import SwiftUI

/// Artwork for the offline demo data (`-demo` launch argument): the shared Kotlin demo hands out `demoart://x/<seed>/<style>` and
/// `demoemote://x/<name>/<still|anim>` URLs, drawn here, so screenshots are deterministic and need no network or brand art.
enum DemoArt {
    static func isDemo(_ url: URL) -> Bool {
        url.scheme == "demoart" || url.scheme == "demoemote" || url.absoluteString.contains("/emoticons/v2/demo-")
    }

    @ViewBuilder
    static func view(for url: URL) -> some View {
        if url.scheme == "demoart" {
            let parts = url.pathComponents.filter { $0 != "/" }
            ArtworkView(seed: Int(parts.first ?? "") ?? 0, style: Int(parts.dropFirst().first ?? "") ?? 0)
        } else {
            EmoteArtView(name: emoteName(url))
        }
    }

    static func emoteName(_ url: URL) -> String {
        if url.scheme == "demoemote" { return url.pathComponents.filter { $0 != "/" }.first ?? "smile" }
        let text = url.absoluteString
        if let range = text.range(of: "/emoticons/v2/demo-") {
            return String(text[range.upperBound...].prefix { $0 != "/" })
        }
        return "smile"
    }
}

private struct ArtworkView: View {
    let seed: Int
    let style: Int

    var body: some View {
        let hue = Double((seed * 47) % 360) / 360
        let a = Color(hue: hue, saturation: 0.55, brightness: 0.55)
        let b = Color(hue: (hue + 0.12).truncatingRemainder(dividingBy: 1), saturation: 0.7, brightness: 0.85)
        GeometryReader { proxy in
            let w = proxy.size.width, h = proxy.size.height
            ZStack {
                LinearGradient(colors: [a, b], startPoint: .topLeading, endPoint: .bottomTrailing)
                switch style % 4 {
                case 0:
                    ForEach(0..<5, id: \.self) { i in
                        Rectangle().fill(.white.opacity(0.07)).frame(width: w * 0.14, height: h * 2).rotationEffect(.degrees(25)).offset(x: CGFloat(i - 2) * w * 0.26)
                    }
                case 1:
                    Circle().fill(.white.opacity(0.14)).frame(width: min(w, h) * 0.9).offset(x: w * 0.25, y: -h * 0.15)
                    Circle().fill(.black.opacity(0.12)).frame(width: min(w, h) * 0.6).offset(x: -w * 0.3, y: h * 0.25)
                case 2:
                    Path { p in
                        p.move(to: CGPoint(x: 0, y: h)); p.addLine(to: CGPoint(x: w * 0.3, y: h * 0.4)); p.addLine(to: CGPoint(x: w * 0.55, y: h * 0.75))
                        p.addLine(to: CGPoint(x: w * 0.8, y: h * 0.3)); p.addLine(to: CGPoint(x: w, y: h)); p.closeSubpath()
                    }.fill(.black.opacity(0.22))
                default:
                    ForEach(0..<4, id: \.self) { i in
                        Capsule().fill(.white.opacity(0.10)).frame(width: w * 1.2, height: h * 0.12).offset(y: CGFloat(i - 2) * h * 0.22 + h * 0.1)
                    }
                }
            }
            .frame(width: w, height: h)
            .clipped()
        }
    }
}

private struct EmoteArtView: View {
    let name: String

    var body: some View {
        let spec = Self.spec(name)
        ZStack {
            Circle().fill(spec.color.gradient)
            Image(systemName: spec.symbol).font(.caption.weight(.bold)).foregroundStyle(.white)
        }
        .aspectRatio(1, contentMode: .fit)
    }

    static func spec(_ name: String) -> (symbol: String, color: Color) {
        switch name {
        case "heart": ("heart.fill", .pink)
        case "star": ("star.fill", .yellow)
        case "gg": ("hand.thumbsup.fill", .green)
        case "clap": ("hands.clap.fill", .orange)
        case "bounce": ("music.note", .purple)
        case "wave": ("figure.dance", .teal)
        case "spin": ("sparkles", .indigo)
        case "badge-sub": ("star.circle.fill", .purple)
        case "badge-mod": ("shield.fill", .green)
        case "badge-vip": ("diamond.fill", .pink)
        case "badge-bc": ("video.fill", .red)
        default: ("face.smiling.fill", .blue)
        }
    }
}
