import SwiftUI

/// Modifiers that exist on one Apple platform only, so shared views do not scatter `#if`.
extension View {
    /// Sentence capitalisation in free text fields (iOS only: the Mac and the Apple TV have no such option).
    @ViewBuilder func sentenceCapitalization() -> some View {
        #if os(iOS)
        self.textInputAutocapitalization(.sentences)
        #else
        self
        #endif
    }

    /// No automatic capitalisation, for search boxes and names (iOS only).
    @ViewBuilder func noCapitalization() -> some View {
        #if os(iOS)
        self.textInputAutocapitalization(.never)
        #else
        self
        #endif
    }
}
