import AppKit
import SwiftUI

/// The menu bar: playback (space, arrows, full screen), navigation (Cmd+1...3, Cmd+F, Cmd+[) and the player window.
struct MacCommands: Commands {
    let app: AppModel
    let mac: MacModel

    private var controller: PlayerController { app.session.playback.controller }
    private var hasPlayer: Bool { app.session.target != nil }

    var body: some Commands {
        // The Mac has no Settings route in the sidebar: the system's "Settings..." item (Cmd+,) opens the Settings scene.
        CommandMenu(Text("mac_menu_playback")) {
            Button(controller.isPlaying ? "player_pause" : "player_play") { controller.togglePlay() }
                .keyboardShortcut(" ", modifiers: [])
                .disabled(!hasPlayer)
            Button("mac_skip_back") { controller.skip(by: -15) }
                .keyboardShortcut(.leftArrow, modifiers: [])
                .disabled(!hasPlayer || controller.live)
            Button("mac_skip_forward") { controller.skip(by: 15) }
                .keyboardShortcut(.rightArrow, modifiers: [])
                .disabled(!hasPlayer || controller.live)
            Divider()
            Button(controller.muted ? "mac_unmute" : "player_muted") { controller.muted.toggle() }
                .keyboardShortcut("m", modifiers: [.command, .option])
                .disabled(!hasPlayer)
            Button("player_fullscreen") { NSApp.keyWindow?.toggleFullScreen(nil) }
                .keyboardShortcut("f", modifiers: [])
                .disabled(!hasPlayer)
            Button("phone_player_chat_toggle") { mac.showChat.toggle() }
                .keyboardShortcut("c", modifiers: [.command, .shift])
                .disabled(!hasPlayer)
            Divider()
            Button(app.session.position == .expanded ? "phone_player_minimise" : "mac_expand_player") {
                if app.session.position == .expanded { app.session.minimise() } else { app.session.expand() }
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])
            .disabled(!hasPlayer)
            Button("mac_close_player") { app.session.close() }
                .keyboardShortcut(".", modifiers: [.command])
                .disabled(!hasPlayer)
        }
        CommandGroup(after: .sidebar) {
            ForEach(Array(app.tabs.enumerated()), id: \.element) { index, tab in
                Button(LocalizedStringKey(tab.titleKey)) { app.select(tab) }
                    .keyboardShortcut(KeyEquivalent(Character("\(index + 1)")), modifiers: .command)
            }
            Divider()
            Button("mac_go_back") { app.pop() }
                .keyboardShortcut("[", modifiers: .command)
        }
        CommandGroup(after: .textEditing) {
            Button("mac_find") { mac.focusSearch() }
                .keyboardShortcut("f", modifiers: .command)
        }
    }
}
