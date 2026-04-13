import UIKit
import WebKit

final class VolumeTextureViewController: UIViewController, WKNavigationDelegate {
    private lazy var webView: WKWebView = {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.bounces = false
        webView.scrollView.isScrollEnabled = false
        return webView
    }()

    private let errorLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.numberOfLines = 0
        label.textColor = .white
        label.font = UIFont.monospacedSystemFont(ofSize: 14, weight: .regular)
        label.textAlignment = .center
        label.isHidden = true
        return label
    }()

    override func viewDidLoad() {
        super.viewDidLoad()

        title = "Materia Volume Texture"
        view.backgroundColor = UIColor(red: 0.03, green: 0.07, blue: 0.10, alpha: 1.0)

        view.addSubview(webView)
        view.addSubview(errorLabel)

        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            errorLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            errorLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            errorLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])

        loadBundledExample()
    }

    private func loadBundledExample() {
        let fileManager = FileManager.default
        let candidates: [(root: URL, index: URL)] = (Bundle.main.resourceURL.map { resourceURL in
            [
                (
                    root: resourceURL.appendingPathComponent("Web", isDirectory: true),
                    index: resourceURL.appendingPathComponent("Web", isDirectory: true).appendingPathComponent("index.html")
                ),
                (
                    root: resourceURL,
                    index: resourceURL.appendingPathComponent("index.html")
                )
            ]
        } ?? [])

        guard let bundleMatch = candidates.first(where: { fileManager.fileExists(atPath: $0.index.path) }) else {
            showError("Missing bundled volume-texture index.html.")
            return
        }

        webView.loadFileURL(bundleMatch.index, allowingReadAccessTo: bundleMatch.root)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        showError("Failed to load bundled example.\n\(error.localizedDescription)")
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        showError("Failed to load bundled example.\n\(error.localizedDescription)")
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        errorLabel.isHidden = true
    }

    private func showError(_ message: String) {
        errorLabel.text = message
        errorLabel.isHidden = false
        print("MateriaVolumeTextureDemo error: \(message)")
    }
}
