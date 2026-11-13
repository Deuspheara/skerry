import SwiftUI

/// The bottom bar: icons with their labels, a floating Liquid Glass capsule on 26 and later, a material capsule before. It is hidden on pages pushed
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
                        VStack(spacing: 2) {
                            Image(systemName: app.tab == tab ? tab.filledImage : tab.systemImage).font(.body)
                            Text(LocalizedStringKey(tab.titleKey)).font(.caption2.weight(.medium)).lineLimit(1).minimumScaleFactor(0.8)
                        }
                            .frame(width: 76, height: 50)
                            .foregroundStyle(app.tab == tab ? Theme.accentText : Theme.textSecondary)
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
            // Tinted with the page colour: untinted glass over bright thumbnails refracts them into coloured blobs in the light appearance.
            .glassCapsule(tint: Theme.background.opacity(0.55))
        }
        .padding(.bottom, 4)
        // Docked to the bottom safe area whatever the keyboard does: a focused field (or a hardware-keyboard accessory) must never lift the bar.
        .ignoresSafeArea(.keyboard)
        .environment(\.flatChrome, app.session.isDragging)
        .offset(y: hidden ? 140 : 0)
        .opacity(hidden ? 0 : 1)
        .animation(reduceMotion ? nil : .snappy, value: hidden)
        .accessibilityElement(children: .contain)
        .dynamicTypeSize(...DynamicTypeSize.xLarge)
    }
}
