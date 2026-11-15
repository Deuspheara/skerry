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
    /// Over the picture (the fullscreen chat): every line gets its own dark backing and a shadow so it stays readable on any frame.
    var overVideo = false
    /// Over the picture without any backing (the Apple TV overlay, like the Android TV one): a wide, dense shadow only.
    var shadowed = false
    var onReply: ((ChatLine) -> Void)?
    @Environment(\.colorScheme) private var scheme
    /// Text colours: white over the video (it has its own backing and shadow), otherwise the interface's (the Mac and TV columns are dark, so they stay white).
    private var onVideo: Bool { overVideo || shadowed }
    private var ink: Color { onVideo ? .white : .primary }
    private var quiet: Color { onVideo ? .white.opacity(0.6) : Theme.textSecondary }
    private func nickColor(_ argb: Int64) -> Color { onVideo ? Color(argb: argb) : NickContrast.nick(argb, light: scheme == .light) }

    /// The in-app text size, scaled with the system's Dynamic Type setting like any other text.
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    private var textSize: CGFloat {
        #if os(tvOS)
        // The Android TV's sizes (14, 15 and 17 sp, drawn on a 960 dp wide screen) at the Apple TV's 1920 pt: twice the numbers.
        return CGFloat(settings?.textSize.sp ?? 15) * 2 * scale
        #else
        let base: CGFloat = switch settings?.textSize {
        case .small: 13
        case .large: 17
        default: 15
        }
        return base * scale
        #endif
    }

    private var emoteHeight: CGFloat {
        #if os(tvOS)
        (textSize * 1.75).rounded()
        #else
        textSize + 9
        #endif
    }

    private var badgeHeight: CGFloat {
        #if os(tvOS)
        min(32, max(30, textSize + 2))
        #else
        textSize + 2
        #endif
    }

    private var gap: CGFloat {
        #if os(tvOS)
        CGFloat(settings?.density.gapDp ?? 3) * 2
        #else
        switch settings?.density {
        case .compact: 1
        case .relaxed: 6
        default: 3
        }
        #endif
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
                    .font(.system(size: textSize - 3 * scale)).foregroundStyle(quiet)
            }
            FlowLayout(spacing: 3, lineSpacing: 1) {
                if settings?.showTimestamps == true { Text(line.time).font(.system(size: textSize - 3)).foregroundStyle(quiet) }
                if settings?.showBadges != false {
                    ForEach(Array(line.badgeUrls.enumerated()), id: \.offset) { _, url in EmoteImage(url: url, height: badgeHeight) }
                }
                Text(line.nick).font(.system(size: textSize, weight: .semibold)).foregroundStyle(nickColor(line.color)) + Text(":").font(.system(size: textSize)).foregroundColor(ink)
                if line.bits > 0 { Text("◆\(line.bits)").font(.system(size: textSize, weight: .bold)).foregroundStyle(onVideo ? Color(red: 0xD9 / 255, green: 0xC7 / 255, blue: 0xFF / 255) : Theme.accentDeep) }
                ForEach(Array(line.pieces.enumerated()), id: \.offset) { _, piece in pieceView(piece) }
            }
        }
        .padding(.horizontal, rowInset).padding(.vertical, line.kind == .normal && !overVideo ? 0 : 3)
        #if os(tvOS)
        .background(background)
        .overlay(alignment: .leading) { if let rule { Rectangle().fill(rule).frame(width: 4) } }
        #else
        .background(background, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        #endif
        .shadow(color: .black.opacity(overVideo ? 0.9 : (shadowed ? 0.95 : 0)), radius: overVideo ? 1.5 : (shadowed ? 5 : 0))
        .shadow(color: .black.opacity(shadowed ? 0.8 : 0), radius: shadowed ? 1.5 : 0, y: shadowed ? 1 : 0)
    }

    @ViewBuilder private func pieceView(_ piece: ChatPiece) -> some View {
        switch onEnum(of: piece) {
        case .text(let text):
            ForEach(Array(text.text.split(separator: " ", omittingEmptySubsequences: true).enumerated()), id: \.offset) { _, word in
                Text(String(word)).font(.system(size: textSize)).foregroundStyle(ink)
            }
        case .strong(let strong):
            Text(strong.text).font(.system(size: textSize, weight: .bold)).foregroundStyle(Theme.accentText)
        case .emote(let emote):
            let layers = emote.layers as? [Emote] ?? []
            if let first = layers.first {
                ZStack {
                    ForEach(Array(layers.enumerated()), id: \.offset) { _, layer in
                        EmoteImage(url: layer.url, animatedUrl: layer.animatedUrl, animate: animate && (settings?.animatedEmotes ?? true), height: emoteHeight, aspect: CGFloat(first.aspect == 0 ? 1 : first.aspect))
                    }
                }
            }
        }
    }

    private var rowInset: CGFloat {
        #if os(tvOS)
        12
        #else
        6
        #endif
    }

    #if os(tvOS)
    private static let twitchPurple = Color(red: 0x91 / 255, green: 0x46 / 255, blue: 0xFF / 255)
    /// The Android TV's rule at the start of a mention (purple) or a highlighted line (light purple).
    private var rule: Color? {
        switch line.kind {
        case .mention: Self.twitchPurple
        case .highlight: Color(red: 0xBF / 255, green: 0x94 / 255, blue: 0xFF / 255)
        default: nil
        }
    }
    #endif

    private var background: Color {
        #if os(tvOS)
        switch line.kind {
        case .mention: Self.twitchPurple.opacity(0.30)
        case .highlight: Self.twitchPurple.opacity(0.16)
        default: overVideo ? Color.black.opacity(0.5) : .clear
        }
        #else
        switch line.kind {
        case .mention: overVideo ? Theme.accent.opacity(0.55) : Theme.accent.opacity(0.28)
        case .highlight: overVideo ? Color.yellow.opacity(0.35) : Color.yellow.opacity(0.18)
        default: overVideo ? Color.black.opacity(0.5) : .clear
        }
        #endif
    }

    @ViewBuilder private var notice: some View {
        #if os(tvOS)
        // Twitch's own sentence (sub, raid...): quieter than a chatter's line, like on the Android TV.
        Text(line.pieces.compactMap { piece -> String? in
            if case .text(let text) = onEnum(of: piece) { return text.text }
            return nil
        }.joined())
        .font(.system(size: textSize).italic()).foregroundStyle(Color(red: 0xC8 / 255, green: 0xC8 / 255, blue: 0xD0 / 255))
        .padding(.horizontal, rowInset).frame(maxWidth: .infinity, alignment: .leading)
        .shadow(color: .black.opacity(shadowed ? 0.95 : 0), radius: shadowed ? 5 : 0)
        #else
        noticeCard
        #endif
    }

    private var noticeCard: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: noticeIcon).foregroundStyle(noticeColor)
            Text(line.pieces.compactMap { piece -> String? in
                if case .text(let text) = onEnum(of: piece) { return text.text }
                return nil
            }.joined()).font(.system(size: textSize - 1)).foregroundStyle(ink)
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
/// What the chat list's scroll position says: at the live edge or not, and how tall the list is.
private struct ScrollReading: Equatable {
    var bottom: Bool
    var height: CGFloat
}

struct ChatList: View, Equatable {
    let lines: [ChatLine]
    let settings: ChatSettings?
    var overVideo = false
    /// See `ChatLineView.shadowed`.
    var shadowed = false
    /// While the sheet is being dragged the chat is held as it was: new messages wait for the finger to lift (a drag frame must not re-lay out the list).
    var frozen = false
    var onReply: ((ChatLine) -> Void)?

    /// Equal means "do not rebuild": the closure is ignored (it only sets a state), a frozen list never changes, and two line arrays are the same list when their
    /// ends match (lines are immutable and the feed only appends and trims).
    static func == (a: ChatList, b: ChatList) -> Bool {
        if b.frozen { return true }
        return a.frozen == b.frozen && a.overVideo == b.overVideo && a.shadowed == b.shadowed && a.settings === b.settings
            && a.lines.count == b.lines.count && a.lines.first?.id == b.lines.first?.id && a.lines.last?.id == b.lines.last?.id
    }

    @State private var atBottom = true
    @State private var unread = 0
    /// While the list's height is changing (the keyboard coming or going) a "not at the bottom" reading is the resize, not the viewer scrolling up.
    @State private var resizingUntil = Date.distantPast

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        let animated = Set(lines.suffix(8).map(\.id))
                        ForEach(lines, id: \.id) { line in
                            ChatLineView(line: line, settings: settings, animate: animated.contains(line.id), overVideo: overVideo, shadowed: shadowed, onReply: onReply).id(line.id)
                        }
                    }
                    .padding(.horizontal, 6)
                }
                .scrollIndicators(.hidden)
                #if os(iOS)
                // Dragging the chat down (or tapping it) puts the keyboard away.
                .scrollDismissesKeyboard(.interactively)
                .simultaneousGesture(TapGesture().onEnded { Keyboard.dismiss() })
                #endif
                .defaultScrollAnchor(.bottom)
                .onScrollGeometryChange(for: ScrollReading.self) { geometry in
                    ScrollReading(bottom: geometry.contentOffset.y + geometry.containerSize.height >= geometry.contentSize.height - 48, height: geometry.containerSize.height)
                } action: { old, new in
                    if old.height != new.height {
                        // The list was resized (the keyboard coming, going or being dragged): that is not the viewer scrolling up. While it follows the live edge, the
                        // latest line stays in view, and "back to live" neither appears nor disappears on the way.
                        resizingUntil = Date().addingTimeInterval(0.5)
                        if atBottom, let last = lines.last {
                            var transaction = Transaction(); transaction.disablesAnimations = true
                            withTransaction(transaction) { proxy.scrollTo(last.id, anchor: .bottom) }
                        }
                        return
                    }
                    if !new.bottom && Date() < resizingUntil { return }
                    atBottom = new.bottom
                    if new.bottom { unread = 0 }
                }
                #if !os(tvOS)
                if !atBottom {
                    Button {
                        unread = 0
                        if let last = lines.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                    } label: {
                        Label(unread > 0 ? plural("chat_new_messages", unread) : String(localized: "chat_back_to_live"), systemImage: "arrow.down").font(.footnote.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
                    }
                    .glassCapsule(interactive: true).buttonStyle(.plain).padding(.vertical, 6)
                }
                #endif
            }
            .onChange(of: lines.last?.id) { _, id in
                guard let id else { return }
                if atBottom { proxy.scrollTo(id, anchor: .bottom) } else { unread += 1 }
            }
        }
    }
}
