import SwiftUI

// Pages built in the next steps; they exist so the shell compiles and navigates.
struct SearchScreen: View {
    var namespace: Namespace.ID
    var body: some View { DetailBar(title: "Search") }
}

struct SettingsScreen: View {
    var body: some View { DetailBar(title: "Settings") }
}

struct ChannelScreen: View {
    let login: String
    var body: some View { DetailBar(title: login) }
}

struct SignInSheet: View {
    var body: some View { Text("Sign in") }
}
