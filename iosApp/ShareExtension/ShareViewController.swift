import UIKit
import UniformTypeIdentifiers

/// "Collage" in the share sheet: saves the shared images to the ShareInbox, then opens Collage,
/// which takes them when it becomes active.
final class ShareViewController: UIViewController {
    override func loadView() {
        let spinner = UIActivityIndicatorView(style: .large)
        spinner.startAnimating()
        view = spinner
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let images = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }
        Task {
            if await ShareInbox.save(images) { openCollage() }
            extensionContext?.completeRequest(returningItems: nil)
        }
    }

    /// Extensions can't use UIApplication.shared, but the app object is in the responder chain.
    /// If Apple ever closes this, Collage still takes the share the next time it is opened.
    private func openCollage() {
        guard let scheme = Bundle.main.object(forInfoDictionaryKey: "CollageHostURLScheme") as? String,
              let url = URL(string: "\(scheme)://share")
        else { return }
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                application.open(url)
                return
            }
            responder = current.next
        }
    }
}
