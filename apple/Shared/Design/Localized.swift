import Foundation

/// A plural string of the catalog (`<plurals>` on Android): the right form for `count` in the viewer's language ("1 minute", "5 minutes", Arabic's six forms...).
/// `bundle` is only for tests, which look a string up in a fixed language.
func plural(_ key: String, _ count: Int, bundle: Bundle = .main) -> String {
    String.localizedStringWithFormat(NSLocalizedString(key, bundle: bundle, comment: ""), count)
}
