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

    func recordException(message: String) {
        let error = NSError(domain: "no.synth.where", code: 0, userInfo: [NSLocalizedDescriptionKey: message])
        Crashlytics.crashlytics().record(error: error)
    }

    func recordFatal(name: String, reason: String, stackTrace: [String]) {
        let model = ExceptionModel(name: name, reason: reason)
        model.stackTrace = stackTrace.map { StackFrame(symbol: $0, file: "", line: 0) }
        Crashlytics.crashlytics().record(exceptionModel: model)
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
                // Universal Links (where.synth.no/<id>), see apple-app-site-association on the server
                .onOpenURL { url in IosLinks.shared.handleUrl(url: url.absoluteString) }
        }
    }
}
