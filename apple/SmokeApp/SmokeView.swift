import SwiftUI
import TwitchShared

struct SmokeView: View {
    let model: SmokeModel

    var body: some View {
        List {
            Section("Settings presenter") {
                if let settings = model.settings {
                    LabeledContent("Chat mode", value: String(describing: settings.chatMode))
                    LabeledContent("Animations", value: settings.animationsEnabled ? "on" : "off")
                    LabeledContent("Session", value: settings.sessionLoading ? "loading" : (settings.account ?? "signed out"))
                }
                Button("Toggle animations", action: model.toggleAnimations)
            }
            Section("Home presenter") {
                Text(model.home)
            }
            Section("Platform seams") {
                ForEach(model.checks, id: \.self) { Text($0) }
            }
        }
        .navigationTitle("TwitchShared smoke")
    }
}
