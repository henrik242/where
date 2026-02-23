import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let mapViewFactory = MapViewFactory()
        let offlineMapFactory = OfflineMapFactory()
        let hexMapViewFactory = MapViewFactory()
        return MainViewControllerKt.MainViewController(
            mapViewProvider: mapViewFactory,
            offlineMapManager: offlineMapFactory,
            hexMapViewProvider: hexMapViewFactory
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
