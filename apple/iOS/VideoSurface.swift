import AVFoundation
import SwiftUI
import UIKit

/// The one place the picture is drawn: an `AVPlayerLayer` view. The sheet never re-parents it (it only changes its frame), so rotation, the mini
/// strip and Picture in Picture all share the same layer.
struct VideoSurface: UIViewRepresentable {
    let controller: PlayerController
    var gravity: AVLayerVideoGravity = .resizeAspect

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.player = controller.player
        view.playerLayer.videoGravity = gravity
        view.backgroundColor = .black
        controller.attach(layer: view.playerLayer)
        return view
    }

    func updateUIView(_ view: PlayerLayerView, context: Context) {
        view.playerLayer.videoGravity = gravity
    }
}

final class PlayerLayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
