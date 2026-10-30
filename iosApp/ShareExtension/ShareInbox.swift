import Foundation
import UniformTypeIdentifiers

/// Where shared images wait for Collage: the app group's `Inbox`, one folder per share named by
/// time, moved into place only when complete. Collage reads it in IosSharedPhotos.kt.
enum ShareInbox {
    /// Saves the images; false if none could be read.
    static func save(_ images: [NSItemProvider]) async -> Bool {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "CollageAppGroup") as? String,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
        else { return false }
        let inbox = container.appendingPathComponent("Inbox", isDirectory: true)
        let files = FileManager.default
        let pending = inbox.appendingPathComponent(".pending-\(UUID().uuidString)", isDirectory: true)
        defer { try? files.removeItem(at: pending) } // Gone already once moved into place.
        do {
            try files.createDirectory(at: pending, withIntermediateDirectories: true)
            var saved = 0
            for (index, image) in images.enumerated() {
                if await copy(image, to: pending.appendingPathComponent(String(format: "%03d", index))) {
                    saved += 1
                }
            }
            guard saved > 0 else { return false }
            let name = String(format: "%015llu", UInt64(Date().timeIntervalSince1970 * 1000))
            try files.moveItem(at: pending, to: inbox.appendingPathComponent(name))
            return true
        } catch {
            NSLog("Collage share: %@", error.localizedDescription)
            return false
        }
    }

    /// Copies one image to `base` plus its file extension. Items backed by data or objects are
    /// written to a temporary file by the system, so this covers them too.
    private static func copy(_ image: NSItemProvider, to base: URL) async -> Bool {
        await withCheckedContinuation { continuation in
            image.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                // The file only exists until this handler returns.
                guard let url else { return continuation.resume(returning: false) }
                let copied = try? FileManager.default.copyItem(at: url, to: base.appendingPathExtension(url.pathExtension))
                continuation.resume(returning: copied != nil)
            }
        }
    }
}
