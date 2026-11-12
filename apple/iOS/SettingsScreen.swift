import SwiftUI
import TwitchShared
#if canImport(UIKit)
import UIKit
#endif

/// Settings, grouped like the settings of other apps: an account card on top, then rounded sections with an icon, the current value and a chevron or control.
struct SettingsScreen: View {
    @Environment(AppModel.self) private var app
    @AppStorage(ThemeChoice.key) private var theme = ThemeChoice.system.rawValue
    @State private var confirmSignOut = false

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "1.0") (\(info?["CFBundleVersion"] as? String ?? "1"))"
    }

    var body: some View {
        VStack(spacing: 0) {
            DetailBar(title: String(localized: "phone_nav_settings"))
            ScrollView {
                VStack(spacing: 22) {
                    accountCard
                    if let state = app.settingsState {
                        section("phone_settings_playback") {
                            ToggleRow(icon: "chart.bar", title: "tile_stats", isOn: state.showStats) { app.onSettingsEvent(SettingsEventToggleStats.shared) }
                            ToggleRow(icon: "arrow.uturn.right", title: "tile_raids", isOn: state.followRaids) { app.onSettingsEvent(SettingsEventToggleFollowRaids.shared) }
                            MenuRow(icon: "rectangle.stack.badge.play", title: "phone_settings_feed_autoplay", value: feedLabel(state.feedAutoplay)) {
                                Button("Wi-Fi") { app.onSettingsEvent(SettingsEventSetFeedAutoplay(mode: .wifiOnly)) }
                                Button("opt_on") { app.onSettingsEvent(SettingsEventSetFeedAutoplay(mode: .always)) }
                                Button("opt_off") { app.onSettingsEvent(SettingsEventSetFeedAutoplay(mode: .off)) }
                            }
                            Text("phone_settings_feed_help").font(.footnote).foregroundStyle(Theme.textSecondary).padding(.horizontal, 16).padding(.bottom, 12)
                        }
                        section("tile_chat") {
                            MenuRow(icon: "bubble.left.and.bubble.right", title: "phone_settings_chat_mode", value: chatModeLabel(state.chatMode)) {
                                Button("chat_hidden") { app.onSettingsEvent(SettingsEventSetChatMode(mode: .hidden)) }
                                Button("chat_compact") { app.onSettingsEvent(SettingsEventSetChatMode(mode: .compact)) }
                                Button("chat_overlay") { app.onSettingsEvent(SettingsEventSetChatMode(mode: .overlay)) }
                            }
                            MenuRow(icon: "textformat.size", title: "chat_opt_text", value: textSizeLabel(state.chat.textSize)) {
                                Button("opt_small") { update(state.chat, size: .small) }
                                Button("opt_medium") { update(state.chat, size: .medium) }
                                Button("opt_large") { update(state.chat, size: .large) }
                            }
                            MenuRow(icon: "line.3.horizontal", title: "chat_opt_spacing", value: densityLabel(state.chat.density)) {
                                Button("opt_tight") { update(state.chat, density: .compact) }
                                Button("opt_normal") { update(state.chat, density: .standard) }
                                Button("opt_loose") { update(state.chat, density: .relaxed) }
                            }
                            ToggleRow(icon: "shield.lefthalf.filled", title: "chat_opt_badges", isOn: state.chat.showBadges) { update(state.chat, badges: !state.chat.showBadges) }
                            ToggleRow(icon: "clock", title: "chat_opt_time", isOn: state.chat.showTimestamps) { update(state.chat, timestamps: !state.chat.showTimestamps) }
                            ToggleRow(icon: "face.smiling", title: "chat_opt_animated", isOn: state.chat.animatedEmotes) { update(state.chat, animated: !state.chat.animatedEmotes) }
                        }
                        section("phone_settings_appearance") {
                            MenuRow(icon: "circle.lefthalf.filled", title: "phone_settings_theme", value: themeLabel(ThemeChoice(rawValue: theme) ?? .system)) {
                                Button("phone_theme_system") { theme = ThemeChoice.system.rawValue }
                                Button("phone_theme_light") { theme = ThemeChoice.light.rawValue }
                                Button("phone_theme_dark") { theme = ThemeChoice.dark.rawValue }
                            }
                            ToggleRow(icon: "sparkles", title: "tile_animations", isOn: state.animationsEnabled) { app.onSettingsEvent(SettingsEventToggleAnimations.shared) }
                        }
                    }
                    section("phone_settings_language") {
                        Button { openSystemSettings() } label: {
                            RowLabel(icon: "globe", title: "phone_settings_language", value: Locale.current.localizedString(forIdentifier: Locale.current.identifier) ?? "", chevron: true)
                        }
                        .buttonStyle(.plain)
                    }
                    section("phone_settings_about") {
                        RowLabel(icon: "info.circle", title: LocalizedStringKey("phone_about_version \(version)"), value: "", chevron: false)
                        Text("phone_about_disclaimer").font(.footnote).foregroundStyle(Theme.textSecondary).padding(.horizontal, 16).padding(.bottom, 12)
                        NavigationLink { LicensesScreen() } label: { RowLabel(icon: "doc.text", title: "phone_licenses", value: "", chevron: true) }.buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .confirmationDialog(Text(String(format: String(localized: "dialog_signout_title"), app.account ?? "")), isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("dialog_signout", role: .destructive) { app.signOut() }
            Button("dialog_cancel", role: .cancel) {}
        }
    }

    private var accountCard: some View {
        HStack(spacing: 14) {
            AccountGlyph(account: app.account).scaleEffect(1.4).frame(width: 56, height: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(app.account ?? String(localized: "state_signin_title")).font(.headline).lineLimit(1)
                if app.isSignedIn { Text("phone_settings_signed_in").font(.footnote).foregroundStyle(Theme.textSecondary) }
            }
            Spacer(minLength: 0)
            Button { if app.isSignedIn { confirmSignOut = true } else { app.showSignIn = true } } label: {
                Text(app.isSignedIn ? "sign_out" : "dialog_signin").font(.subheadline.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 9)
            }
            .glassCapsule(interactive: true).buttonStyle(.plain)
        }
        .padding(16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func section<Content: View>(_ title: LocalizedStringKey, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.footnote.weight(.semibold)).textCase(.uppercase).foregroundStyle(Theme.textSecondary).padding(.horizontal, 8)
            VStack(spacing: 0, content: content)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }

    private func openSystemSettings() {
        #if canImport(UIKit)
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        #endif
    }

    private func update(_ chat: ChatSettings, size: ChatTextSize? = nil, density: ChatDensity? = nil, badges: Bool? = nil, timestamps: Bool? = nil, animated: Bool? = nil) {
        app.onSettingsEvent(SettingsEventUpdateChat { current in
            current.doCopy(
                textSize: size ?? current.textSize, density: density ?? current.density, backgroundOpacity: current.backgroundOpacity,
                showBadges: badges ?? current.showBadges, showTimestamps: timestamps ?? current.showTimestamps,
                animatedEmotes: animated ?? current.animatedEmotes, animatedLimit: current.animatedLimit,
            )
        })
    }

    private func feedLabel(_ mode: FeedAutoplay) -> LocalizedStringKey {
        switch mode { case .wifiOnly: "Wi-Fi"; case .always: "opt_on"; case .off: "opt_off"; default: "opt_off" }
    }
    private func chatModeLabel(_ mode: ChatMode) -> LocalizedStringKey {
        switch mode { case .hidden: "chat_hidden"; case .compact: "chat_compact"; case .overlay: "chat_overlay"; default: "chat_compact" }
    }
    private func textSizeLabel(_ size: ChatTextSize) -> LocalizedStringKey {
        switch size { case .small: "opt_small"; case .medium: "opt_medium"; case .large: "opt_large"; default: "opt_medium" }
    }
    private func densityLabel(_ density: ChatDensity) -> LocalizedStringKey {
        switch density { case .compact: "opt_tight"; case .standard: "opt_normal"; case .relaxed: "opt_loose"; default: "opt_normal" }
    }
    private func themeLabel(_ choice: ThemeChoice) -> LocalizedStringKey {
        switch choice { case .system: "phone_theme_system"; case .light: "phone_theme_light"; case .dark: "phone_theme_dark" }
    }
}

private struct RowLabel: View {
    let icon: String
    let title: LocalizedStringKey
    let value: LocalizedStringKey
    let chevron: Bool

    init(icon: String, title: LocalizedStringKey, value: LocalizedStringKey, chevron: Bool) {
        self.icon = icon; self.title = title; self.value = value; self.chevron = chevron
    }

    init(icon: String, title: LocalizedStringKey, value: String, chevron: Bool) {
        self.init(icon: icon, title: title, value: LocalizedStringKey(value), chevron: chevron)
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.body).foregroundStyle(Theme.accent).frame(width: 28)
            Text(title).font(.body).lineLimit(1)
            Spacer(minLength: 8)
            Text(value).font(.subheadline).foregroundStyle(Theme.textSecondary).lineLimit(1)
            if chevron { Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary).flipsForRightToLeftLayoutDirection(true) }
        }
        .padding(.horizontal, 16).padding(.vertical, 13)
        .contentShape(Rectangle())
    }
}

private struct ToggleRow: View {
    let icon: String
    let title: LocalizedStringKey
    let isOn: Bool
    let toggle: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.body).foregroundStyle(Theme.accent).frame(width: 28)
            Toggle(isOn: Binding(get: { isOn }, set: { _ in toggle() })) { Text(title).font(.body) }
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

private struct MenuRow<Items: View>: View {
    let icon: String
    let title: LocalizedStringKey
    let value: LocalizedStringKey
    @ViewBuilder var items: () -> Items

    var body: some View {
        Menu(content: items) { RowLabel(icon: icon, title: title, value: value, chevron: false) }
            .buttonStyle(.plain)
    }
}

struct LicensesScreen: View {
    var body: some View {
        VStack(spacing: 0) {
            DetailBar(title: String(localized: "phone_licenses"))
            ScrollView {
                Text("Built with Kotlin Multiplatform, Ktor, DataStore, Koin, kotlinx libraries and SKIE, released under the Apache License 2.0, and SwiftUI.")
                    .font(.body).padding(16)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }
}
