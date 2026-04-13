import MetalKit
import UIKit
import MateriaTriangle

final class TriangleViewController: UIViewController {
    private let metalView = MTKView(frame: .zero)
    private var controller: TriangleIosController?
    private var hasStarted = false

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .black
        navigationItem.title = "Materia Triangle"

        metalView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(metalView)

        NSLayoutConstraint.activate([
            metalView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            metalView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            metalView.topAnchor.constraint(equalTo: view.topAnchor),
            metalView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        let controller = TriangleIosHostKt.createDefaultTriangleIosController(metalView: metalView)
        self.controller = controller
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        guard !hasStarted, let controller else { return }
        hasStarted = true

        controller.start(
            onReady: { bootLog in
                print(bootLog)
            },
            onError: { message in
                print("MateriaTriangle iOS host error: \(message)")
            }
        )
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        controller?.resizeToDrawableSize()
    }

    deinit {
        controller?.stop()
    }
}
