import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let mapViewFactory = MapViewFactory()
        let offlineMapFactory = OfflineMapFactory()
        return MainViewControllerKt.MainViewController(mapViewProvider: mapViewFactory, offlineMapManager: offlineMapFactory)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
