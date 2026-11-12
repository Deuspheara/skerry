import SwiftUI
import TwitchShared
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

/// Device-code sign-in: open twitch.tv/activate, enter (or paste) the code, and the sheet closes itself when Twitch confirms.
struct SignInSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var model: SignInModel?
    @State private var copied = false
    @State private var openFailed = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 22) {
                if let model {
                    content(model)
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
                Spacer(minLength: 0)
            }
            .padding(24)
            .navigationTitle(Text("dialog_signin"))
            .navigationBarTitleDisplayModeInlineIfAvailable()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("dialog_cancel") { model?.cancel(); dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task { if model == nil { model = SignInModel(graph: app.graph) } }
        .onChange(of: model?.result) { _, result in
            guard result == .success else { return }
            model?.resultShown()
            Task { try? await Task.sleep(for: .seconds(0.8)); dismiss() }
        }
        .onDisappear { model?.cancel() }
    }

    @ViewBuilder private func content(_ model: SignInModel) -> some View {
        switch model.result {
        case .success:
            Label("phone_signin_done", systemImage: "checkmark.circle.fill").font(.headline).foregroundStyle(.green)
        case .failed:
            Text("phone_signin_failed").foregroundStyle(.red)
            Button("state_retry") { model.retry() }.buttonStyle(.borderedProminent)
        default:
            if let code = model.code {
                Text("phone_signin_step_open").font(.body).fixedSize(horizontal: false, vertical: true)
                Button {
                    guard let url = URL(string: code.verificationUri.hasPrefix("http") ? code.verificationUri : "https://\(code.verificationUri)") else { openFailed = true; return }
                    openURL(url) { accepted in openFailed = !accepted }
                } label: {
                    Label("phone_signin_open", systemImage: "safari").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                if openFailed { Text("phone_signin_open_failed").font(.footnote).foregroundStyle(.red) }
                Text("phone_signin_step_code").font(.body).fixedSize(horizontal: false, vertical: true)
                Text(code.userCode)
                    .font(.system(size: 38, weight: .bold, design: .monospaced)).kerning(4)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .glass(in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .textSelection(.enabled)
                    .accessibilityLabel(Text(code.userCode.map(String.init).joined(separator: " ")))
                HStack {
                    Button { copy(code.userCode, expiresIn: TimeInterval(code.expiresIn)) } label: {
                        Label(copied ? "phone_signin_copied" : "phone_signin_copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                    .buttonStyle(.bordered)
                    Spacer()
                    if let expiresAt = model.expiresAt { Countdown(until: expiresAt) }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
    }

    /// The code is only useful for a few minutes: it is kept on this device and removed from the clipboard when it expires.
    private func copy(_ value: String, expiresIn: TimeInterval) {
        #if canImport(UIKit)
        UIPasteboard.general.setItems([["public.utf8-plain-text": value]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(expiresIn)])
        #else
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(value, forType: .string)
        // The Mac has no expiring pasteboard item: clear it ourselves when the code expires, unless something else was copied since.
        let count = NSPasteboard.general.changeCount
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(expiresIn))
            if NSPasteboard.general.changeCount == count { NSPasteboard.general.clearContents() }
        }
        #endif
        copied = true
    }
}

private struct Countdown: View {
    let until: Date

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(until.timeIntervalSince(context.date)))
            if remaining == 0 {
                Text("phone_signin_expired").font(.footnote).foregroundStyle(.red)
            } else {
                Text(String(format: String(localized: "phone_signin_expires"), String(format: "%d:%02d", remaining / 60, remaining % 60)))
                    .font(.footnote.monospacedDigit()).foregroundStyle(Theme.textSecondary)
            }
        }
    }
}

extension View {
    @ViewBuilder func navigationBarTitleDisplayModeInlineIfAvailable() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}
