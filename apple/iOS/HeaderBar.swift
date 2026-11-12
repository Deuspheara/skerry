import SwiftUI

/// The header of the root screens: the app mark, a search pill that opens Search, and the account button that opens Settings.
/// `namespace` lets the pill morph into the search field of the Search page (see SearchScreen).
struct HeaderBar: View {
    @Environment(AppModel.self) private var app
    var namespace: Namespace.ID
    var tab: AppTab

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "play.tv.fill").font(.title3).foregroundStyle(Theme.accent).accessibilityHidden(true)
            Button { app.push(.search) } label: {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").font(.subheadline.weight(.semibold))
                    Text("phone_search_hint").font(.subheadline).lineLimit(1)
                    Spacer(minLength: 0)
                }
                .foregroundStyle(Theme.textSecondary)
                .padding(.horizontal, 14).frame(height: 38)
                .glassCapsule(interactive: true)
            }
            .buttonStyle(.plain)
            .matchedTransitionSource(id: SearchPill.id(tab), in: namespace)
            .accessibilityLabel(Text("nav_search"))
            Button { app.push(.settings) } label: {
                AccountGlyph(account: app.account)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(app.isSignedIn ? "cd_account_signed_in" : "cd_account_signed_out"))
        }
        .padding(.horizontal, 16).padding(.vertical, 6)
    }
}

/// One source id per tab: all three tab stacks are alive at once, and the zoom transition must start from the visible one.
enum SearchPill { static func id(_ tab: AppTab) -> String { "search-pill-\(tab.rawValue)" } }

struct AccountGlyph: View {
    let account: String?

    var body: some View {
        ZStack {
            Circle().fill(Theme.accentDeep.opacity(account == nil ? 0.0 : 0.35))
            if let account {
                Text(String(account.prefix(1)).uppercased()).font(.subheadline.weight(.bold))
            } else {
                Image(systemName: "person.crop.circle").font(.title2).foregroundStyle(Theme.textSecondary)
            }
        }
        .frame(width: 38, height: 38)
        .glass(in: Circle(), interactive: true)
    }
}
