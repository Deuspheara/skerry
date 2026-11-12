import SwiftUI

/// The bottom bar: icons only, a floating Liquid Glass capsule on 26 and later, a material capsule before. It is hidden on pages pushed
/// over a tab (detail pages) so those get the full height.
struct TabBar: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var selection

    var body: some View {
        let hidden = !(app.paths[app.tab] ?? []).isEmpty
        GlassGroup(spacing: 8) {
            HStack(spacing: 4) {
                ForEach(app.tabs, id: \.self) { tab in
                    Button { withAnimation(reduceMotion ? nil : .snappy) { app.select(tab) } } label: {
                        Image(systemName: app.tab == tab ? tab.filledImage : tab.systemImage)
                            .font(.title3)
                            .frame(width: 64, height: 44)
                            .foregroundStyle(app.tab == tab ? Theme.accent : Theme.textSecondary)
                            .background {
                                if app.tab == tab {
                                    Capsule().fill(Theme.accent.opacity(0.16)).matchedGeometryEffect(id: "selected", in: selection)
                                }
                            }
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(LocalizedStringKey(tab.titleKey)))
                    .accessibilityAddTraits(app.tab == tab ? [.isSelected, .isButton] : .isButton)
                    .accessibilityIdentifier("tab-\(tab.rawValue)")
                }
            }
            .padding(6)
            .glassCapsule()
        }
        .padding(.bottom, 4)
        .offset(y: hidden ? 140 : 0)
        .opacity(hidden ? 0 : 1)
        .animation(reduceMotion ? nil : .snappy, value: hidden)
        .accessibilityElement(children: .contain)
    }
}
