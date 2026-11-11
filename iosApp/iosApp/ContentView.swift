import UIKit
import SwiftUI
import Shared

/// Hosts the Compose UI and reports where iOS puts the vertical bar (iPhone Duo), which Kotlin/Native's
/// UIKit bindings can't read yet.
final class ComposeHostController: UIViewController {
    private let compose = MainViewControllerKt.MainViewController()
    private var reportedBar: [Double]?

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Folding, unfolding and rotating all resize the view, so this sees every change of the bar.
        reportVerticalBar()
    }

    private func reportVerticalBar() {
        var bar: [Double] = [0, 0, 0, 0, 0]
        if #available(iOS 27.1, *), traitCollection.verticalBarEdge != .unspecified {
            let trailing = traitCollection.verticalBarEdge == .trailing
            let edge: NSDirectionalRectEdge = trailing ? .trailing : .leading
            let region: UIView.LayoutRegion
            if #available(iOS 27.2, *) {
                region = .bar(onEdge: edge)
            } else {
                region = .bar(onEdge: edge, extent: 48)
            }
            let frame = view.layoutGuide(for: region).layoutFrame
            let bounds = view.bounds
            let fromRight = trailing != (view.effectiveUserInterfaceLayoutDirection == .rightToLeft)
            bar = [
                trailing ? 2 : 1,
                frame.minY,
                bounds.height - frame.maxY,
                fromRight ? bounds.width - frame.maxX : frame.minX,
                frame.width,
            ]
        }
        guard bar != reportedBar else { return }
        reportedBar = bar
        MainViewControllerKt.setVerticalBar(edge: Int32(bar[0]), top: bar[1], bottom: bar[2], edgeInset: bar[3], width: bar[4])
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeHostController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose handles the safe areas itself.
        ComposeView()
            .ignoresSafeArea()
    }
}
