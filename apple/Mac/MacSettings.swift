import AppKit
import SwiftUI
import TwitchShared

/// The Settings window (Cmd+,): the Mac way, a tab per topic with grouped forms. It edits the same settings the other apps do, through the shared presenter.
struct MacSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        TabView {
            GeneralTab().tabItem { Label("mac_settings_general", systemImage: "gearshape") }
            PlaybackTab().tabItem { Label("phone_settings_playback", systemImage: "play.rectangle") }
            ChatTab().tabItem { Label("tile_chat", systemImage: "bubble.left.and.bubble.right") }
            AccountTab().tabItem { Label("mac_settings_account", systemImage: "person.crop.circle") }
            AboutTab().tabItem { Label("phone_settings_about", systemImage: "info.circle") }
        }
        .frame(width: 520)
        .scenePadding()
    }
}

private struct GeneralTab: View {
    @Environment(AppModel.self) private var app
    @AppStorage(ThemeChoice.key) private var theme = ThemeChoice.system.rawValue

    var body: some View {
        Form {
            Picker("phone_settings_theme", selection: $theme) {
                Text("phone_theme_system").tag(ThemeChoice.system.rawValue)
                Text("phone_theme_light").tag(ThemeChoice.light.rawValue)
                Text("phone_theme_dark").tag(ThemeChoice.dark.rawValue)
            }
            if let state = app.settingsState {
                Toggle("tile_animations", isOn: Binding(get: { state.animationsEnabled }, set: { _ in app.onSettingsEvent(SettingsEventToggleAnimations.shared) }))
            }
            LabeledContent("phone_settings_language") {
                Button("mac_open_language_settings") {
                    if let url = URL(string: "x-apple.systempreferences:com.apple.Localization-Settings.extension") { NSWorkspace.shared.open(url) }
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct PlaybackTab: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Form {
            if let state = app.settingsState {
                Toggle("tile_stats", isOn: Binding(get: { state.showStats }, set: { _ in app.onSettingsEvent(SettingsEventToggleStats.shared) }))
                Toggle("tile_raids", isOn: Binding(get: { state.followRaids }, set: { _ in app.onSettingsEvent(SettingsEventToggleFollowRaids.shared) }))
                Picker("phone_settings_chat_mode", selection: Binding(get: { state.chatMode }, set: { app.onSettingsEvent(SettingsEventSetChatMode(mode: $0)) })) {
                    Text("chat_hidden").tag(ChatMode.hidden)
                    Text("chat_compact").tag(ChatMode.compact)
                    Text("chat_overlay").tag(ChatMode.overlay)
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct ChatTab: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Form {
            if let state = app.settingsState {
                let chat = state.chat
                Picker("chat_opt_text", selection: Binding(get: { chat.textSize }, set: { update(chat, size: $0) })) {
                    Text("opt_small").tag(ChatTextSize.small)
                    Text("opt_medium").tag(ChatTextSize.medium)
                    Text("opt_large").tag(ChatTextSize.large)
                }
                Picker("chat_opt_spacing", selection: Binding(get: { chat.density }, set: { update(chat, density: $0) })) {
                    Text("opt_tight").tag(ChatDensity.compact)
                    Text("opt_normal").tag(ChatDensity.standard)
                    Text("opt_loose").tag(ChatDensity.relaxed)
                }
                Toggle("chat_opt_badges", isOn: Binding(get: { chat.showBadges }, set: { update(chat, badges: $0) }))
                Toggle("chat_opt_time", isOn: Binding(get: { chat.showTimestamps }, set: { update(chat, timestamps: $0) }))
                Toggle("chat_opt_animated", isOn: Binding(get: { chat.animatedEmotes }, set: { update(chat, animated: $0) }))
            }
        }
        .formStyle(.grouped)
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
}

private struct AccountTab: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openWindow) private var openWindow
    @State private var confirmSignOut = false

    var body: some View {
        Form {
            LabeledContent {
                if app.isSignedIn {
                    Button("sign_out", role: .destructive) { confirmSignOut = true }
                } else {
                    // The sign-in sheet belongs to the main window: bring it forward first.
                    Button("dialog_signin") { openWindow(id: "main"); NSApp.activate(); app.showSignIn = true }
                }
            } label: {
                Label {
                    VStack(alignment: .leading) {
                        Text(app.account ?? String(localized: "state_signin_title")).font(.headline)
                        if app.isSignedIn { Text("phone_settings_signed_in").font(.footnote).foregroundStyle(Theme.textSecondary) }
                    }
                } icon: { Image(systemName: "person.crop.circle.fill").font(.title).foregroundStyle(Theme.accent) }
            }
        }
        .formStyle(.grouped)
        .confirmationDialog(Text(String(format: String(localized: "dialog_signout_title"), app.account ?? "")), isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("dialog_signout", role: .destructive) { app.signOut() }
            Button("dialog_cancel", role: .cancel) {}
        }
    }
}

private struct AboutTab: View {
    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "1.0") (\(info?["CFBundleVersion"] as? String ?? "1"))"
    }

    var body: some View {
        Form {
            LabeledContent("phone_settings_about") { Text(version).foregroundStyle(Theme.textSecondary) }
            Text("phone_about_disclaimer").font(.footnote).foregroundStyle(Theme.textSecondary)
            Text("apple_licenses_body")
                .font(.footnote).foregroundStyle(Theme.textSecondary)
        }
        .formStyle(.grouped)
    }
}
