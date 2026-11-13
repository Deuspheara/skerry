import SwiftUI
import TwitchShared

/// Settings on the Apple TV: a focusable list (account, playback, chat, appearance, about) with the same options and the same shared presenter as the other apps.
struct TVSettingsScreen: View {
    @Environment(AppModel.self) private var app
    @AppStorage(ThemeChoice.key) private var theme = ThemeChoice.system.rawValue

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "1.0") (\(info?["CFBundleVersion"] as? String ?? "1"))"
    }

    var body: some View {
        List {
            Section {
                if app.isSignedIn {
                    LabeledContent { Button("sign_out", role: .destructive) { app.signOut() } } label: {
                        Label { Text(app.account ?? "") } icon: { Image(systemName: "person.crop.circle.fill") }
                    }
                } else {
                    Button { app.showSignIn = true } label: { Label("dialog_signin", systemImage: "person.crop.circle") }
                }
            }
            if let state = app.settingsState {
                Section("phone_settings_playback") {
                    Toggle("tile_stats", isOn: Binding(get: { state.showStats }, set: { _ in app.onSettingsEvent(SettingsEventToggleStats.shared) }))
                    Toggle("tile_raids", isOn: Binding(get: { state.followRaids }, set: { _ in app.onSettingsEvent(SettingsEventToggleFollowRaids.shared) }))
                }
                Section("tile_chat") {
                    let chat = state.chat
                    LabeledContent("phone_settings_chat_mode") {
                        Picker("phone_settings_chat_mode", selection: Binding(get: { state.chatMode }, set: { app.onSettingsEvent(SettingsEventSetChatMode(mode: $0)) })) {
                            Text("chat_hidden").tag(ChatMode.hidden)
                            Text("chat_compact").tag(ChatMode.compact)
                            Text("chat_overlay").tag(ChatMode.overlay)
                        }
                        .pickerStyle(.menu).labelsHidden()
                    }
                    LabeledContent("chat_opt_text") {
                        Picker("chat_opt_text", selection: Binding(get: { chat.textSize }, set: { update(chat, size: $0) })) {
                            Text("opt_small").tag(ChatTextSize.small)
                            Text("opt_medium").tag(ChatTextSize.medium)
                            Text("opt_large").tag(ChatTextSize.large)
                        }
                        .pickerStyle(.menu).labelsHidden()
                    }
                    Toggle("chat_opt_badges", isOn: Binding(get: { chat.showBadges }, set: { update(chat, badges: $0) }))
                    Toggle("chat_opt_animated", isOn: Binding(get: { chat.animatedEmotes }, set: { update(chat, animated: $0) }))
                }
                Section("phone_settings_appearance") {
                    LabeledContent("phone_settings_theme") {
                        Picker("phone_settings_theme", selection: $theme) {
                            Text("phone_theme_system").tag(ThemeChoice.system.rawValue)
                            Text("phone_theme_light").tag(ThemeChoice.light.rawValue)
                            Text("phone_theme_dark").tag(ThemeChoice.dark.rawValue)
                        }
                        .pickerStyle(.menu).labelsHidden()
                    }
                    Toggle("tile_animations", isOn: Binding(get: { state.animationsEnabled }, set: { _ in app.onSettingsEvent(SettingsEventToggleAnimations.shared) }))
                }
            }
            Section("phone_settings_about") {
                Text(String(format: String(localized: "phone_about_version"), version))
                Text("phone_about_disclaimer").font(.callout).foregroundStyle(.secondary)
            }
        }
    }

    private func update(_ chat: ChatSettings, size: ChatTextSize? = nil, badges: Bool? = nil, animated: Bool? = nil) {
        app.onSettingsEvent(SettingsEventUpdateChat { current in
            current.doCopy(
                textSize: size ?? current.textSize, density: current.density, backgroundOpacity: current.backgroundOpacity,
                showBadges: badges ?? current.showBadges, showTimestamps: current.showTimestamps,
                animatedEmotes: animated ?? current.animatedEmotes, animatedLimit: current.animatedLimit,
            )
        })
    }
}
