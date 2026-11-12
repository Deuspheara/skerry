import SwiftUI
import TwitchShared

/// The chat input: a compact pill (emote button, field, send circle once there is text), a reply bar, `:` emote suggestions and the emote panel that takes the keyboard's place.
struct ChatComposer: View {
    @Environment(AppModel.self) private var app
    let playback: PlaybackModel
    @Binding var replyTo: ChatLine?

    @State private var text = ""
    @State private var pickerOpen = Self.debugPickerOpen
    @FocusState private var focused: Bool

    private static var debugPickerOpen: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-emotes")
        #else
        false
        #endif
    }

    var body: some View {
        VStack(spacing: 6) {
            if let reply = replyTo {
                HStack(spacing: 8) {
                    Image(systemName: "arrowshape.turn.up.left.fill").font(.footnote)
                    Text(String(format: String(localized: "phone_chat_replying_to"), reply.nick)).font(.footnote).lineLimit(1)
                    Spacer()
                    Button { replyTo = nil } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).accessibilityLabel(Text("phone_chat_cancel_reply"))
                }
                .foregroundStyle(Theme.textSecondary).padding(.horizontal, 14)
            }
            suggestionStrip
            if app.isSignedIn || app.launch.demo {
                HStack(spacing: 8) {
                    Button { togglePicker() } label: {
                        Image(systemName: pickerOpen ? "keyboard" : "face.smiling").font(.title3).frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain).foregroundStyle(Theme.textSecondary)
                    .accessibilityLabel(Text(pickerOpen ? "phone_emote_keyboard" : "phone_emote_button"))
                    TextField("phone_player_chat_hint", text: $text, axis: .vertical)
                        .lineLimit(1...3)
                        .focused($focused)
                        .submitLabel(.send)
                        .onSubmit(send)
                        .textInputAutocapitalization(.sentences)
                        .accessibilityLabel(Text(text.isEmpty ? "phone_player_chat_hint" : ""))
                    if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button(action: send) {
                            Image(systemName: "arrow.up").font(.subheadline.weight(.bold)).foregroundStyle(.white).frame(width: 32, height: 32).background(Theme.accentDeep, in: Circle())
                        }
                        .buttonStyle(.plain).accessibilityLabel(Text("chat_send")).transition(.scale.combined(with: .opacity))
                    }
                }
                .padding(.leading, 6).padding(.trailing, 6).frame(minHeight: 44)
                .glass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .padding(.horizontal, 10)
                .animation(app.motion, value: text.isEmpty)
            } else {
                Button { app.showSignIn = true } label: { Text("phone_player_chat_signin").font(.subheadline).padding(.vertical, 12).frame(maxWidth: .infinity) }
                    .glass(in: Capsule(), interactive: true).buttonStyle(.plain).padding(.horizontal, 10)
            }
            if let problem = playback.sendProblem { Text(problem).font(.footnote).foregroundStyle(.red) }
            if pickerOpen, let picker = playback.picker {
                EmotePanel(picker: picker, onPick: { insert($0) }).frame(height: 280).transition(.move(edge: .bottom))
            }
        }
        .padding(.vertical, 6)
        .onChange(of: focused) { _, now in if now { pickerOpen = false } }
        .animation(app.motion, value: pickerOpen)
    }

    private func togglePicker() {
        pickerOpen.toggle()
        if pickerOpen { focused = false } else { focused = true }
    }

    private func send() {
        let message = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty else { return }
        playback.send(message, replyTo: replyTo?.id)
        text = ""
        replyTo = nil
    }

    private func insert(_ name: String) {
        text += (text.isEmpty || text.hasSuffix(" ") ? "" : " ") + name + " "
        playback.pickedEmote(name)
    }

    /// `:ab` at the end of the text proposes emotes whose name contains `ab`.
    @ViewBuilder private var suggestionStrip: some View {
        if let word = text.split(separator: " ", omittingEmptySubsequences: false).last, word.hasPrefix(":"), word.count >= 3, let picker = playback.picker {
            let matches = (picker.search(query: String(word.dropFirst()), limit: 12) as? [Emote]) ?? []
            if !matches.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(matches, id: \.name) { emote in
                            Button {
                                text = String(text.dropLast(word.count)) + emote.name + " "
                                playback.pickedEmote(emote.name)
                            } label: {
                                HStack(spacing: 6) {
                                    EmoteImage(url: emote.url, height: 22, aspect: CGFloat(emote.aspect == 0 ? 1 : emote.aspect))
                                    Text(emote.name).font(.footnote)
                                }
                                .padding(.horizontal, 10).padding(.vertical, 6).glass(in: Capsule(), interactive: true)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 10)
                }
                .scrollIndicators(.hidden).accessibilityLabel(Text("phone_emote_suggestions"))
            }
        }
    }
}

/// The emote panel: a tab per source (plus Recent), a search field, and a grid. A tap inserts the name; locked emotes are shown dimmed and cannot be picked.
struct EmotePanel: View {
    let picker: EmotePickerState
    var onPick: (String) -> Void

    @State private var source: EmoteSource?
    @State private var query = ""

    private var sources: [EmoteSource] { (picker.sources as? [EmoteSource]) ?? [] }
    private var recent: [Emote] { picker.recent as? [Emote] ?? [] }

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.textSecondary)
                TextField("phone_emote_search_hint", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
                if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).accessibilityLabel(Text("phone_emote_search_clear")) }
            }
            .padding(.horizontal, 12).frame(height: 36).background(Theme.surface, in: Capsule()).padding(.horizontal, 10)
            tabs
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 40), spacing: 8)], spacing: 8) {
                    ForEach(visible, id: \.0.name) { emote, locked in
                        Button { if !locked { onPick(emote.name) } } label: {
                            EmoteImage(url: emote.url, animatedUrl: emote.animatedUrl, animate: false, height: 34, aspect: CGFloat(emote.aspect == 0 ? 1 : emote.aspect))
                                .frame(width: 40, height: 40).opacity(locked ? 0.35 : 1)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(emote.name))
                    }
                }
                .padding(.horizontal, 10)
                if visible.isEmpty { Text("phone_emote_empty").font(.footnote).foregroundStyle(Theme.textSecondary).padding(24) }
            }
            .scrollIndicators(.hidden)
        }
        .padding(.top, 8)
        .onAppear { if source == nil { source = recent.isEmpty ? sources.first : nil } }
    }

    private var tabs: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                if !recent.isEmpty { FilterChip(title: "phone_emote_tab_recent", selected: source == nil) { source = nil } }
                ForEach(sources, id: \.self) { item in
                    FilterChip(title: LocalizedStringKey(name(of: item)), selected: source == item) { source = item }
                }
            }
            .padding(.horizontal, 10)
        }
        .scrollIndicators(.hidden)
    }

    private func name(of source: EmoteSource) -> String {
        switch source { case .twitch: "Twitch"; case .sevenTv: "7TV"; case .bttv: "BTTV"; case .ffz: "FFZ"; default: "…" }
    }

    /// The emotes to show with whether each is locked: search results across lists, the recents, or the chosen source's lists.
    private var visible: [(Emote, Bool)] {
        if !query.trimmingCharacters(in: .whitespaces).isEmpty { return (picker.search(query: query, limit: 120) as? [Emote] ?? []).map { ($0, false) } }
        guard let source else { return recent.map { ($0, false) } }
        let groups = (picker.groupsOf(source: source) as? [EmoteGroup]) ?? []
        return groups.flatMap { group in (group.emotes as? [Emote] ?? []).map { ($0, group.section == .locked) } }
    }
}
