import CoreImage.CIFilterBuiltins
import SwiftUI
import TwitchShared

/// Sign-in on the Apple TV, where there is nothing to type: a large code and a QR code to scan with a phone. The same device-code flow as the other apps; the
/// screen closes itself when the viewer approves the code on twitch.tv/activate.
struct TVSignInScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var model: SignInModel?

    var body: some View {
        VStack(spacing: 40) {
            Text("dialog_signin").font(.largeTitle.weight(.bold)).accessibilityAddTraits(.isHeader)
            if let model { content(model) } else { ProgressView().controlSize(.extraLarge) }
            Button("dialog_cancel") { model?.cancel(); dismiss() }
        }
        .padding(60)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
        .task { if model == nil { model = SignInModel(graph: app.graph) } }
        .onChange(of: model?.result) { _, result in
            guard result == .success else { return }
            model?.resultShown()
            Task { try? await Task.sleep(for: .seconds(1)); dismiss() }
        }
        .onDisappear { model?.cancel() }
    }

    @ViewBuilder private func content(_ model: SignInModel) -> some View {
        switch model.result {
        case .success:
            Label("phone_signin_done", systemImage: "checkmark.circle.fill").font(.title).foregroundStyle(.green)
        case .failed:
            Text("phone_signin_failed").font(.title3).foregroundStyle(.red)
            Button("state_retry") { model.retry() }
        default:
            if let code = model.code {
                HStack(alignment: .center, spacing: 80) {
                    if let image = Self.qr(for: Self.activationUrl(code)) {
                        Image(platform: image).interpolation(.none).resizable().frame(width: 360, height: 360)
                            .padding(18).background(.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .accessibilityLabel(Text("tv_signin_qr"))
                    }
                    VStack(alignment: .leading, spacing: 24) {
                        Text("tv_signin_scan").font(.title3).fixedSize(horizontal: false, vertical: true)
                        Text("tv_signin_code").font(.callout).foregroundStyle(.secondary)
                        Text(code.userCode).font(.system(size: 84, weight: .bold, design: .monospaced)).kerning(8)
                            .accessibilityLabel(Text(code.userCode.map(String.init).joined(separator: " ")))
                        Text(code.verificationUri.replacingOccurrences(of: "https://", with: "")).font(.title3.weight(.semibold)).foregroundStyle(Theme.accent)
                        if let expiresAt = model.expiresAt { Countdown(until: expiresAt) }
                    }
                    .frame(maxWidth: 760, alignment: .leading)
                }
            } else {
                ProgressView().controlSize(.extraLarge)
            }
        }
    }

    /// The page that pre-fills the code: the QR sends a phone straight to the approval step.
    static func activationUrl(_ code: DeviceCodeResponse) -> String {
        let base = code.verificationUri.hasPrefix("http") ? code.verificationUri : "https://\(code.verificationUri)"
        let separator = base.contains("?") ? "&" : "?"
        return "\(base)\(separator)device-code=\(code.userCode)"
    }

    static func qr(for text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

private struct Countdown: View {
    let until: Date

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(until.timeIntervalSince(context.date)))
            if remaining == 0 {
                Text("phone_signin_expired").font(.callout).foregroundStyle(.red)
            } else {
                Text(String(format: String(localized: "phone_signin_expires"), String(format: "%d:%02d", remaining / 60, remaining % 60)))
                    .font(.callout.monospacedDigit()).foregroundStyle(.secondary)
            }
        }
    }
}
