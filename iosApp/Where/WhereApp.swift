import SwiftUI
import Shared
import FirebaseCore
import FirebaseCrashlytics

class IosCrashReporterBridge: CrashReporterBridge {
    func setEnabled(enabled: Bool) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
    }

    func log(message: String) {
        Crashlytics.crashlytics().log(message)
    }
}

@main
struct WhereApp: App {
    init() {
        FirebaseApp.configure()
        CrashReporter.shared.bridge = IosCrashReporterBridge()
        AppSetupKt.startApp()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
