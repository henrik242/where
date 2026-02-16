import SwiftUI
import Shared

@main
struct WhereApp: App {
    init() {
        KoinHelperKt.initKoin()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
