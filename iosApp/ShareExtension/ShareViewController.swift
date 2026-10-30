import UIKit
import UniformTypeIdentifiers

/// "Collage" in the share sheet: copies the shared images into the app group's Inbox, then opens
/// Collage, which takes them when it becomes active (see IosSharedPhotos.kt).
final class ShareViewController: UIViewController {
    private var started = false

    override func viewDidLoad() {
        super.viewDidLoad()
        let spinner = UIActivityIndicatorView(style: .large)
        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.startAnimating()
        view.addSubview(spinner)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task { await shareToCollage() }
    }

    @MainActor
    private func shareToCollage() async {
        defer { extensionContext?.completeRequest(returningItems: nil) }
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }
        guard !providers.isEmpty, let inbox = Self.inboxURL() else { return }

        // Written under a hidden name and renamed when complete, so the app never sees half a share.
        let files = FileManager.default
        let pending = inbox.appendingPathComponent(".pending-\(UUID().uuidString)")
        do {
            try files.createDirectory(at: pending, withIntermediateDirectories: true)
            var copied = 0
            for (index, provider) in providers.enumerated() {
                if await Self.copyImage(from: provider, to: pending.appendingPathComponent(String(format: "%03d", index))) {
                    copied += 1
                }
            }
            guard copied > 0 else {
                try? files.removeItem(at: pending)
                return
            }
            let millis = UInt64(Date().timeIntervalSince1970 * 1000)
            try files.moveItem(at: pending, to: inbox.appendingPathComponent(String(format: "%015llu", millis)))
        } catch {
            NSLog("Collage share: %@", error.localizedDescription)
            try? files.removeItem(at: pending)
            return
        }
        openCollage()
    }

    private static func inboxURL() -> URL? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "CollageAppGroup") as? String,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
        else { return nil }
        return container.appendingPathComponent("Inbox", isDirectory: true)
    }

    /// Copies one image to `base` plus its file extension; false if the item couldn't be read.
    private static func copyImage(from provider: NSItemProvider, to base: URL) async -> Bool {
        let copied = await withCheckedContinuation { continuation in
            provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                // The file only exists until this handler returns.
                guard let url else { return continuation.resume(returning: false) }
                let ext = url.pathExtension.isEmpty ? "jpg" : url.pathExtension
                continuation.resume(returning: (try? FileManager.default.copyItem(at: url, to: base.appendingPathExtension(ext))) != nil)
            }
        }
        if copied { return true }
        // Some apps share an in-memory image rather than a file.
        return await withCheckedContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
                continuation.resume(returning: data.flatMap { try? $0.write(to: base.appendingPathExtension("img")) } != nil)
            }
        }
    }

    /// Share extensions can't use UIApplication.shared; the app object is reachable through the
    /// responder chain. If this ever stops working, the share is still taken the next time
    /// Collage becomes active.
    private func openCollage() {
        guard let scheme = Bundle.main.object(forInfoDictionaryKey: "CollageHostURLScheme") as? String,
              let url = URL(string: "\(scheme)://share")
        else { return }
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return
            }
            responder = current.next
        }
    }
}
