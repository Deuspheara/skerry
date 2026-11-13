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
                        Image(systemName: inlinePicker && pickerOpen ? "keyboard" : "face.smiling").font(.title3).frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain).foregroundStyle(Theme.textSecondary)
                    .accessibilityLabel(Text(inlinePicker && pickerOpen ? "phone_emote_keyboard" : "phone_emote_button"))
                    #if os(macOS)
                    .help(Text("phone_emote_button"))
                    .popover(isPresented: $pickerOpen, arrowEdge: .top) {
                        if let picker = playback.picker { EmotePanel(picker: picker, onPick: { insert($0) }).frame(width: 340, height: 360) }
                    }
                    #endif
                    TextField("phone_player_chat_hint", text: $text, axis: .vertical)
                        .lineLimit(1...3)
                        .focused($focused)
                        .submitLabel(.send)
                        .onSubmit(send)
                        .sentenceCapitalization()
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
            #if !os(macOS)
            if pickerOpen, let picker = playback.picker {
                EmotePanel(picker: picker, onPick: { insert($0) }).frame(height: 300).transition(.move(edge: .bottom))
            }
            #endif
        }
        .padding(.vertical, 6)
        .onAppear {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-focus-chat") { focused = true }
            #endif
        }
        .onChange(of: focused) { _, now in if now, inlinePicker { pickerOpen = false } }
        .animation(app.motion, value: pickerOpen)
    }

    /// The iPhone's panel takes the keyboard's place; on the Mac it is a popover next to the field and the field keeps the keyboard.
    private var inlinePicker: Bool {
        #if os(macOS)
        false
        #else
        true
        #endif
    }

    private func togglePicker() {
        pickerOpen.toggle()
        if inlinePicker { focused = !pickerOpen }
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
                TextField("phone_emote_search_hint", text: $query).noCapitalization().autocorrectionDisabled()
                if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).accessibilityLabel(Text("phone_emote_search_clear")) }
            }
            .padding(.horizontal, 12).frame(height: 36).background(Theme.surface, in: Capsule()).padding(.horizontal, 10)
            tabs
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6, pinnedViews: [.sectionHeaders]) {
                    ForEach(sections, id: \.id) { section in
                        Section {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 40), spacing: 8)], spacing: 8) {
                                ForEach(section.emotes, id: \.name) { emote in
                                    Button { if !section.locked { onPick(emote.name) } } label: {
                                        EmoteImage(url: emote.url, animatedUrl: emote.animatedUrl, animate: false, height: 34, aspect: CGFloat(emote.aspect == 0 ? 1 : emote.aspect))
                                            .frame(width: 40, height: 40).opacity(section.locked ? 0.35 : 1)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(Text(emote.name))
                                }
                            }
                            .padding(.horizontal, 10)
                        } header: {
                            if let title = section.title {
                                Text(title).font(.caption.weight(.bold)).foregroundStyle(Theme.textSecondary).textCase(.uppercase)
                                    .padding(.horizontal, 14).padding(.vertical, 4).frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Theme.background)
                            }
                        }
                    }
                }
                if sections.isEmpty { emptyState }
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

    struct EmoteBlock { let id: String; let title: LocalizedStringKey?; let emotes: [Emote]; let locked: Bool }

    /// What the panel shows, in blocks with a header each: search results, the recents (with an empty message), or the chosen source's sections (channel, global, other
    /// channels, locked).
    private var sections: [EmoteBlock] {
        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
            let found = picker.search(query: query, limit: 120) as? [Emote] ?? []
            return found.isEmpty ? [] : [EmoteBlock(id: "search", title: nil, emotes: found, locked: false)]
        }
        guard let source else { return recent.isEmpty ? [] : [EmoteBlock(id: "recent", title: "emote_section_recent", emotes: recent, locked: false)] }
        let groups = (picker.groupsOf(source: source) as? [EmoteGroup]) ?? []
        let order: [(EmoteSection, LocalizedStringKey)] = [(.channel, "emote_section_channel"), (.global, "emote_section_global"), (.otherChannels, "emote_section_other"), (.locked, "emote_section_locked")]
        return order.compactMap { section, title in
            let emotes = groups.filter { $0.section == section }.flatMap { $0.emotes as? [Emote] ?? [] }
            return emotes.isEmpty ? nil : EmoteBlock(id: "\(source)-\(section)", title: title, emotes: emotes, locked: section == .locked)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: query.isEmpty ? "clock.arrow.circlepath" : "magnifyingglass").font(.title2).foregroundStyle(Theme.textSecondary).accessibilityHidden(true)
            Text("phone_emote_empty").font(.footnote).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(32)
    }
}
