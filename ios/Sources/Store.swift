import Foundation

/// Dauerhafte Kopie der OCMate-Daten (Playlists, Favoriten, Einstellungen).
/// Der Browser-Speicher der Web-Oberfläche wird hier gespiegelt und beim Start wiederhergestellt,
/// damit nichts verloren geht, falls iOS den Web-Speicher leert.
final class Store {
    private(set) var data: [String: String]
    private let fileURL: URL
    private var pending: DispatchWorkItem?

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("ocmate-store.json")
        if let raw = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([String: String].self, from: raw) {
            data = decoded
        } else {
            data = [:]
        }
    }

    func set(_ key: String, _ value: String) {
        data[key] = value
        scheduleWrite()
    }

    func remove(_ key: String) {
        data[key] = nil
        scheduleWrite()
    }

    /// Schreibt gesammelt nach einer halben Sekunde, statt bei jeder Änderung.
    private func scheduleWrite() {
        pending?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.flush() }
        pending = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: work)
    }

    func flush() {
        pending?.cancel()
        pending = nil
        if let encoded = try? JSONEncoder().encode(data) {
            try? encoded.write(to: fileURL, options: .atomic)
        }
    }

    /// Inhalt als JSON-Text zum Einbetten in das Startskript der Oberfläche.
    func json() -> String {
        guard let encoded = try? JSONEncoder().encode(data),
              let text = String(data: encoded, encoding: .utf8) else { return "{}" }
        return text
    }
}
