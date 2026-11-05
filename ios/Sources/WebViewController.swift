import UIKit
import WebKit

/// OCMate als iPhone-App: Die Oberfläche kommt aus dem App-Paket (Schema ocmate://).
/// Abrufe der Oberfläche an IPTV-Server (Playlist, EPG, Xtream-API) leitet die App selbst weiter,
/// dadurch gelten die Browser-Sperren für fremde Server (CORS) nicht.
/// Videos spielt der eingebaute Apple-Player direkt ab.
final class WebViewController: UIViewController, WKURLSchemeHandler, WKScriptMessageHandler, WKNavigationDelegate {

    private static let scheme = "ocmate"
    private static let startURL = URL(string: "ocmate://app/ocmate.html")!
    private let playerUA = "VLC/3.0.20 LibVLC/3.0.20"

    private var web: WKWebView!
    private let store = Store()
    /// "" = Player-Kennung (Standard), "webview" = Kennung des Browsers, sonst eigener Wert
    private var userAgentChoice = ""
    /// Laufende Weiterleitungen, damit abgebrochene Abrufe nicht mehr beantwortet werden
    private var running: [ObjectIdentifier: URLSessionDataTask] = [:]

    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 300
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: config)
    }()

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var prefersHomeIndicatorAutoHidden: Bool { true }

    override func loadView() {
        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(self, forURLScheme: Self.scheme)
        config.allowsInlineMediaPlayback = true
        config.allowsPictureInPictureMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let content = config.userContentController
        content.add(self, name: "oc")
        content.addUserScript(WKUserScript(source: bootScript(),
                                           injectionTime: .atDocumentStart,
                                           forMainFrameOnly: true))

        web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 11 / 255, green: 15 / 255, blue: 20 / 255, alpha: 1)
        web.scrollView.backgroundColor = web.backgroundColor
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.bounces = false
        web.customUserAgent = playerUA
        if #available(iOS 16.4, *) { web.isInspectable = true }
        view = web
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        web.load(URLRequest(url: Self.startURL))
        NotificationCenter.default.addObserver(self, selector: #selector(saveNow),
                                               name: UIApplication.willResignActiveNotification, object: nil)
    }

    @objc private func saveNow() { store.flush() }

    // MARK: Startskript

    /// Läuft vor der Oberfläche: stellt gespeicherte Daten wieder her, spiegelt Änderungen
    /// in die App und leitet Abrufe an fremde Server über die App.
    private func bootScript() -> String {
        return """
        (function(){
          var saved = \(store.json());
          try { for (var k in saved) { localStorage.setItem(k, saved[k]); } } catch (e) {}
          function post(m){ try { window.webkit.messageHandlers.oc.postMessage(m); } catch (e) {} }
          try {
            var S = Storage.prototype, setI = S.setItem, remI = S.removeItem;
            S.setItem = function(k, v){ setI.call(this, k, v); if (this === window.localStorage && String(k).indexOf('ocmate.') === 0) post({t:'set', k:String(k), v:String(v)}); };
            S.removeItem = function(k){ remI.call(this, k); if (this === window.localStorage) post({t:'del', k:String(k)}); };
          } catch (e) {}
          window.OCNative = { setUserAgent: function(u){ post({t:'ua', v:u || ''}); }, stopStreams: function(){} };
          var origFetch = window.fetch.bind(window);
          window.fetch = function(input, init){
            var u = typeof input === 'string' ? input : (input && input.url) || String(input);
            if (/^https?:\\/\\//i.test(u)) { return origFetch('ocmate://app/__proxy?u=' + encodeURIComponent(u), init); }
            return origFetch(input, init);
          };
        })();
        """
    }

    // MARK: Nachrichten aus der Oberfläche

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let type = body["t"] as? String else { return }
        switch type {
        case "set":
            if let key = body["k"] as? String, let value = body["v"] as? String { store.set(key, value) }
        case "del":
            if let key = body["k"] as? String { store.remove(key) }
        case "ua":
            userAgentChoice = (body["v"] as? String) ?? ""
            if userAgentChoice.isEmpty {
                web.customUserAgent = playerUA
            } else if userAgentChoice == "webview" {
                web.customUserAgent = nil
            } else {
                web.customUserAgent = userAgentChoice
            }
        default:
            break
        }
    }

    // MARK: ocmate:// – App-Dateien und Weiterleitung

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        guard let url = task.request.url else { return }

        if url.path == "/__proxy",
           let target = URLComponents(url: url, resolvingAgainstBaseURL: false)?
               .queryItems?.first(where: { $0.name == "u" })?.value,
           let targetURL = URL(string: target) {
            forward(task, to: targetURL)
            return
        }

        let name = (url.path.isEmpty || url.path == "/") ? "ocmate.html" : String(url.path.dropFirst())
        guard let file = Bundle.main.url(forResource: name, withExtension: nil, subdirectory: "web"),
              let data = try? Data(contentsOf: file) else {
            respond(task, url: url, status: 404, headers: [:], body: Data())
            return
        }
        let mime: String
        if name.hasSuffix(".html") { mime = "text/html; charset=utf-8" }
        else if name.hasSuffix(".js") { mime = "text/javascript; charset=utf-8" }
        else if name.hasSuffix(".png") { mime = "image/png" }
        else { mime = "application/octet-stream" }
        respond(task, url: url, status: 200, headers: ["Content-Type": mime], body: data)
    }

    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {
        let id = ObjectIdentifier(task)
        running[id]?.cancel()
        running[id] = nil
    }

    private func forward(_ task: WKURLSchemeTask, to target: URL) {
        var request = URLRequest(url: target)
        request.httpMethod = task.request.httpMethod ?? "GET"
        request.httpBody = task.request.httpBody
        for (key, value) in task.request.allHTTPHeaderFields ?? [:] {
            let lower = key.lowercased()
            if ["origin", "referer", "user-agent", "host", "accept-encoding"].contains(lower) { continue }
            request.setValue(value, forHTTPHeaderField: key)
        }
        if userAgentChoice != "webview" {
            request.setValue(userAgentChoice.isEmpty ? playerUA : userAgentChoice, forHTTPHeaderField: "User-Agent")
        }

        let id = ObjectIdentifier(task)
        let requestURL = task.request.url ?? target
        let dataTask = session.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self, self.running.removeValue(forKey: id) != nil else { return }
                if let error = error {
                    let text = Self.describe(error)
                    self.respond(task, url: requestURL, status: 502,
                                 headers: ["Content-Type": "text/plain; charset=utf-8"],
                                 body: Data(text.utf8))
                    return
                }
                let http = response as? HTTPURLResponse
                var headers: [String: String] = [:]
                for (key, value) in http?.allHeaderFields ?? [:] {
                    guard let k = key as? String else { continue }
                    let lower = k.lowercased()
                    if lower.hasPrefix("access-control-") || ["content-encoding", "content-length",
                        "transfer-encoding", "set-cookie"].contains(lower) { continue }
                    headers[k] = "\(value)"
                }
                self.respond(task, url: requestURL, status: http?.statusCode ?? 200,
                             headers: headers, body: data ?? Data())
            }
        }
        running[id] = dataTask
        dataTask.resume()
    }

    private func respond(_ task: WKURLSchemeTask, url: URL, status: Int, headers: [String: String], body: Data) {
        var all = headers
        all["Access-Control-Allow-Origin"] = "*"
        all["Content-Length"] = String(body.count)
        guard let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1",
                                             headerFields: all) else { return }
        task.didReceive(response)
        task.didReceive(body)
        task.didFinish()
    }

    /// Fehlertext im selben Stil wie die Android-Version, damit die Oberfläche
    /// verständliche Hinweise anzeigen kann (Adresse nicht gefunden, Zeitüberschreitung …).
    private static func describe(_ error: Error) -> String {
        let ns = error as NSError
        let text = ns.localizedDescription
        guard ns.domain == NSURLErrorDomain else { return "NetworkError: \(text)" }
        switch ns.code {
        case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed:
            return "UnknownHostException: \(text)"
        case NSURLErrorTimedOut:
            return "SocketTimeoutException: \(text)"
        case NSURLErrorCannotConnectToHost:
            return "ConnectException: Connection refused – \(text)"
        case NSURLErrorNetworkConnectionLost:
            return "Connection reset: \(text)"
        case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted,
             NSURLErrorServerCertificateHasBadDate, NSURLErrorServerCertificateHasUnknownRoot,
             NSURLErrorServerCertificateNotYetValid, NSURLErrorClientCertificateRejected:
            return "SSLHandshakeException: \(text)"
        case NSURLErrorNotConnectedToInternet:
            return "NetworkError: Keine Internetverbindung"
        default:
            return "NetworkError: \(text)"
        }
    }

    // MARK: Absturz der Oberfläche

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.load(URLRequest(url: Self.startURL))
    }
}
