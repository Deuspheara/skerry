import SwiftUI
import TwitchShared

/// The chat of the Apple TV player, built like the Android TV's (`ChatPanel`, `ChatLayer`): the numbers are Android's dp doubled (the Apple TV draws 1920 pt where
/// the Android TV draws 960 dp).
///
/// - **Compact**: a column at the trailing edge, full height, whose text area is 24 % of the screen plus an 8 dp start inset and a 24 dp end inset (at most a third
///   of the screen: the picture keeps two thirds), on the page colour with the chat background opacity over it; a 36 dp header with the "Chat" label, the room's
///   mode chips and the options glyph; the newest line rests above the safe bottom margin. The picture is resized once and the column slides in from the edge.
/// - **Overlay**: 340 dp wide and two thirds of the screen high at the bottom trailing corner, no panel, a wide dense shadow, the lines fading out towards the top.
/// - **Hidden**: nothing; the mode is kept, so the column leaves the way it came.
///
/// The messages are read-only (the system player owns the Siri Remote): the mode and the chat options are in the transport bar's menu.
enum TVChatMetrics {
    static let compactFraction: CGFloat = 0.24
    static let insetStart: CGFloat = 16
    static let insetEnd: CGFloat = 48
    static let overlayWidth: CGFloat = 680
    static let safeVertical: CGFloat = 54
    static let headerHeight: CGFloat = 72
    static let pageColor = Color(red: 0x0E / 255, green: 0x0E / 255, blue: 0x10 / 255)
    static let baseColor = Color(red: 0x18 / 255, green: 0x18 / 255, blue: 0x1B / 255)

    /// The compact column's width for a screen of `screenWidth`: the text area plus its insets, never more than a third of the screen.
    static func columnWidth(screenWidth: CGFloat) -> CGFloat {
        min(screenWidth * compactFraction + insetStart + insetEnd, screenWidth / 3)
    }
}

struct TVChatColumn: View {
    let playback: PlaybackModel
    let mode: ChatMode
    let screen: CGSize

    var body: some View {
        let overlay = mode == .overlay
        let width = overlay ? TVChatMetrics.overlayWidth : TVChatMetrics.columnWidth(screenWidth: screen.width)
        VStack(spacing: 0) {
            // Over the picture the header is chrome that would float above the messages: the Android TV shows it only with the transport bar, so the
            // overlay has none here (the mode and the options are in the transport bar's menu).
            if !overlay {
                TVChatHeader(room: playback.room, showTitle: true)
                    .padding(.leading, TVChatMetrics.insetStart).padding(.trailing, TVChatMetrics.insetEnd)
                    .padding(.top, TVChatMetrics.safeVertical)
            }
            ChatList(lines: playback.chat, settings: playback.chatSettings, shadowed: overlay, onReply: nil)
                .equatable()
                .padding(.leading, overlay ? 40 : TVChatMetrics.insetStart).padding(.trailing, overlay ? 40 : TVChatMetrics.insetEnd)
                .padding(.bottom, TVChatMetrics.safeVertical + 12)
                // Over the video the lines fade out towards the top of the panel (Android's `overlayFade` stops).
                .mask {
                    if overlay {
                        LinearGradient(stops: [
                            .init(color: .black.opacity(0), location: 0), .init(color: .black.opacity(0.04), location: 0.15), .init(color: .black.opacity(0.25), location: 0.35),
                            .init(color: .black.opacity(0.65), location: 0.6), .init(color: .black, location: 0.85),
                        ], startPoint: .top, endPoint: .bottom)
                    } else {
                        Rectangle()
                    }
                }
        }
        .frame(width: width, height: overlay ? screen.height * 2 / 3 : screen.height, alignment: .top)
        // The column always has an opaque underlay, whatever the background setting, so the video never shows through it.
        .background {
            if !overlay {
                TVChatMetrics.pageColor
                TVChatMetrics.baseColor.opacity(Double(playback.chatSettings?.backgroundOpacity ?? 80) / 100)
            }
        }
        .environment(\.colorScheme, .dark)
        // VoiceOver reads the chat as one live-updating element whose value is the latest messages (the visual list cannot be focused on tvOS).
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("tile_chat"))
        .accessibilityValue(Text(playback.chat.suffix(5).map(\.spokenText).joined(separator: ". ")))
        .accessibilityAddTraits(.updatesFrequently)
        .accessibilityRespondsToUserInteraction(true)
        .allowsHitTesting(false)
    }
}

/// "Chat" label, the room's mode chips (slow, followers, subscribers, emotes, unique) and the options glyph, 36 dp high like Android's.
private struct TVChatHeader: View {
    let room: RoomState?
    let showTitle: Bool

    var body: some View {
        HStack(spacing: 20) {
            if showTitle {
                Text("player_chat").font(.system(size: 22, weight: .medium)).foregroundStyle(Color(red: 0xC8 / 255, green: 0xC8 / 255, blue: 0xD0 / 255)).lineLimit(1)
                if let room { RoomChips(room: room) }
            }
            Spacer(minLength: 0)
            Image(systemName: "ellipsis").font(.system(size: 22, weight: .semibold)).foregroundStyle(Color(red: 0xC8 / 255, green: 0xC8 / 255, blue: 0xD0 / 255)).accessibilityHidden(true)
        }
        .frame(height: TVChatMetrics.headerHeight - 8)
        .padding(.bottom, 8)
    }
}

private struct RoomChips: View {
    let room: RoomState

    var body: some View {
        let slow = room.slowSeconds?.intValue
        let followers = room.followersOnlyMinutes?.intValue
        HStack(spacing: 8) {
            if let slow { chip("clock", String(format: String(localized: "unit_seconds_short"), Int32(slow))) }
            if let followers { chip("heart", followers > 0 ? String(format: String(localized: "unit_minutes_short"), Int32(followers)) : nil) }
            if room.subscribersOnly { chip("star", nil) }
            if room.emoteOnly { chip("face.smiling", nil) }
            if room.uniqueOnly { chip("rectangle.on.rectangle", nil) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(spoken))
    }

    private var spoken: String {
        var parts: [String] = []
        if let slow = room.slowSeconds?.intValue { parts.append(String(format: String(localized: "room_slow"), Int32(slow))) }
        if let followers = room.followersOnlyMinutes?.intValue { parts.append(followers == 0 ? String(localized: "room_followers") : String(format: String(localized: "room_followers_min"), Int32(followers))) }
        if room.subscribersOnly { parts.append(String(localized: "room_subscribers")) }
        if room.emoteOnly { parts.append(String(localized: "room_emotes")) }
        if room.uniqueOnly { parts.append(String(localized: "room_unique")) }
        return parts.joined(separator: ", ")
    }

    private func chip(_ symbol: String, _ value: String?) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 18, weight: .semibold))
            if let value { Text(value).font(.system(size: 18, weight: .medium).monospacedDigit()) }
        }
        .foregroundStyle(Color(red: 0xEF / 255, green: 0xEF / 255, blue: 0xF1 / 255))
        .padding(.horizontal, 12).frame(height: 44)
        .background(Color(red: 0x26 / 255, green: 0x26 / 255, blue: 0x2C / 255), in: RoundedRectangle(cornerRadius: 4))
    }
}
