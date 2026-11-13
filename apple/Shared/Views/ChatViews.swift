import SwiftUI
import TwitchShared

/// Wraps its children into lines, like text: used for a chat message, whose words, badges and emotes are separate views.
struct FlowLayout: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat = 2

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(width: proposal.width ?? .infinity, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(width: bounds.width, subviews: subviews)
        for (index, frame) in result.frames.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY), proposal: ProposedViewSize(frame.size))
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> (size: CGSize, frames: [CGRect]) {
        var frames: [CGRect] = []
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0, maxWidth: CGFloat = 0
        var line: [Int] = []
        func closeLine() {
            for index in line { frames[index].origin.y = y + (lineHeight - frames[index].height) / 2 }
            y += lineHeight + lineSpacing
            line = []; x = 0; lineHeight = 0
        }
        for subview in subviews {
            let size = subview.sizeThatFits(ProposedViewSize(width: width.isFinite ? width : nil, height: nil))
            if x > 0, x + size.width > width { closeLine() }
            frames.append(CGRect(x: x, y: 0, width: size.width, height: size.height))
            line.append(frames.count - 1)
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
            maxWidth = max(maxWidth, x - spacing)
        }
        closeLine()
        return (CGSize(width: width.isFinite ? width : maxWidth, height: max(0, y - lineSpacing)), frames)
    }
}

extension ChatLine {
    /// The line as VoiceOver reads it: the author, then the words (emotes by name).
    var spokenText: String {
        let words = pieces.map { piece -> String in
            switch onEnum(of: piece) {
            case .text(let text): text.text
            case .strong(let strong): strong.text
            case .emote(let emote): emote.name
            }
        }.joined()
        return "\(nick). \(words)"
    }
}

/// One chat line: badges, coloured nick, then the words and emotes of the message; a quoted line above a reply, a card for notices.
struct ChatLineView: View {
    let line: ChatLine
    let settings: ChatSettings?
    var animate: Bool
    /// Over the picture (the Apple TV overlay, the fullscreen chat): every line gets its own dark backing and a shadow so it stays readable on any frame.
    var overVideo = false
    var onReply: ((ChatLine) -> Void)?

    /// The in-app text size, scaled with the system's Dynamic Type setting like any other text.
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    private var textSize: CGFloat {
        let base: CGFloat = switch settings?.textSize {
        case .small: 13
        case .large: 17
        default: 15
        }
        return base * scale
    }

    private var gap: CGFloat {
        switch settings?.density {
        case .compact: 1
        case .relaxed: 6
        default: 3
        }
    }

    var body: some View {
        Group {
            if line.kind == .system { notice } else { message }
        }
        .padding(.vertical, gap)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contextMenu {
            if line.canReply, let onReply { Button { onReply(line) } label: { Label("phone_chat_reply", systemImage: "arrowshape.turn.up.left") } }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(line.spokenText))
        .accessibilityAction(named: Text("phone_chat_reply")) { if line.canReply { onReply?(line) } }
    }

    private var message: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let reply = line.reply {
                Label { Text("\(reply.name): \(reply.body)").lineLimit(1) } icon: { Image(systemName: "arrowshape.turn.up.left.fill") }
                    .font(.system(size: textSize - 3 * scale)).foregroundStyle(.white.opacity(0.65))
            }
            FlowLayout(spacing: 3, lineSpacing: 1) {
                if settings?.showTimestamps == true { Text(line.time).font(.system(size: textSize - 3)).foregroundStyle(.white.opacity(0.5)) }
                if settings?.showBadges != false {
                    ForEach(Array(line.badgeUrls.enumerated()), id: \.offset) { _, url in EmoteImage(url: url, height: textSize + 2) }
                }
                Text(line.nick).font(.system(size: textSize, weight: .bold)).foregroundStyle(Color(argb: line.color)) + Text(":").font(.system(size: textSize)).foregroundColor(.white)
                ForEach(Array(line.pieces.enumerated()), id: \.offset) { _, piece in pieceView(piece) }
            }
        }
        .padding(.horizontal, 6).padding(.vertical, line.kind == .normal && !overVideo ? 0 : 3)
        .background(background, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        .shadow(color: .black.opacity(overVideo ? 0.9 : 0), radius: overVideo ? 1.5 : 0)
    }

    @ViewBuilder private func pieceView(_ piece: ChatPiece) -> some View {
        switch onEnum(of: piece) {
        case .text(let text):
            ForEach(Array(text.text.split(separator: " ", omittingEmptySubsequences: true).enumerated()), id: \.offset) { _, word in
                Text(String(word)).font(.system(size: textSize)).foregroundStyle(.white)
            }
        case .strong(let strong):
            Text(strong.text).font(.system(size: textSize, weight: .bold)).foregroundStyle(Theme.accent)
        case .emote(let emote):
            let layers = emote.layers as? [Emote] ?? []
            if let first = layers.first {
                ZStack {
                    ForEach(Array(layers.enumerated()), id: \.offset) { _, layer in
                        EmoteImage(url: layer.url, animatedUrl: layer.animatedUrl, animate: animate && (settings?.animatedEmotes ?? true), height: textSize + 9, aspect: CGFloat(first.aspect == 0 ? 1 : first.aspect))
                    }
                }
            }
        }
    }

    private var background: Color {
        switch line.kind {
        case .mention: overVideo ? Theme.accent.opacity(0.55) : Theme.accent.opacity(0.28)
        case .highlight: overVideo ? Color.yellow.opacity(0.35) : Color.yellow.opacity(0.18)
        default: overVideo ? Color.black.opacity(0.5) : .clear
        }
    }

    private var notice: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: noticeIcon).foregroundStyle(noticeColor)
            Text(line.pieces.compactMap { piece -> String? in
                if case .text(let text) = onEnum(of: piece) { return text.text }
                return nil
            }.joined()).font(.system(size: textSize - 1)).foregroundStyle(.white)
        }
        .padding(8).frame(maxWidth: .infinity, alignment: .leading)
        .background(noticeColor.opacity(0.18), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(alignment: .leading) { Rectangle().fill(noticeColor).frame(width: 3).clipShape(RoundedRectangle(cornerRadius: 2)) }
    }

    private var noticeIcon: String {
        switch line.notice {
        case .some(.sub), .some(.resub): "star.fill"
        case .some(.gift), .some(.giftCommunity): "gift.fill"
        case .some(.raid): "person.3.fill"
        case .some(.announcement): "megaphone.fill"
        default: "info.circle.fill"
        }
    }

    private var noticeColor: Color {
        switch line.accent {
        case "BLUE": .blue
        case "GREEN": .green
        case "ORANGE": .orange
        case "PURPLE": Theme.accent
        default: line.notice == nil ? .gray : Theme.accent
        }
    }
}

extension Color {
    /// Kotlin gives 0xAARRGGBB as a Long.
    init(argb: Int64) {
        self.init(.sRGB, red: Double((argb >> 16) & 0xFF) / 255, green: Double((argb >> 8) & 0xFF) / 255, blue: Double(argb & 0xFF) / 255, opacity: 1)
    }
}

/// The messages of the channel: newest at the bottom, auto-scrolling while the viewer is at the bottom, with a pill to jump back down.
struct ChatList: View {
    let lines: [ChatLine]
    let settings: ChatSettings?
    var overVideo = false
    var onReply: ((ChatLine) -> Void)?

    @State private var atBottom = true
    @State private var unread = 0

    var body: some View {
        ScrollViewReader { proxy in
            ZStack(alignment: .bottom) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(lines, id: \.id) { line in
                            ChatLineView(line: line, settings: settings, animate: lines.suffix(8).contains { $0.id == line.id }, overVideo: overVideo, onReply: onReply).id(line.id)
                        }
                    }
                    .padding(.horizontal, 6)
                }
                .scrollIndicators(.hidden)
                .defaultScrollAnchor(.bottom)
                .onScrollGeometryChange(for: Bool.self) { geometry in
                    geometry.contentOffset.y + geometry.containerSize.height >= geometry.contentSize.height - 48
                } action: { _, bottom in
                    atBottom = bottom
                    if bottom { unread = 0 }
                }
                if !atBottom {
                    Button {
                        unread = 0
                        if let last = lines.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                    } label: {
                        Label(unread > 0 ? plural("chat_new_messages", unread) : String(localized: "chat_back_to_live"), systemImage: "arrow.down").font(.footnote.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
                    }
                    .glassCapsule(interactive: true).buttonStyle(.plain).padding(.bottom, 8)
                }
            }
            .onChange(of: lines.last?.id) { _, id in
                guard let id else { return }
                if atBottom { proxy.scrollTo(id, anchor: .bottom) } else { unread += 1 }
            }
        }
    }
}
