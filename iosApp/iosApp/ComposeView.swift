import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let mapViewFactory = MapViewFactory()
        return MainViewControllerKt.MainViewController(mapViewProvider: mapViewFactory)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
