import SwiftUI

@main
struct IBKRTaxAssistantApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var lock = AppLock()
    @State private var screenCaptured = UIScreen.main.isCaptured

    init() {
        TempFiles.clear()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                // Lock and privacy cover stay outside the screens, as on Android (mobile-skins spec).
                if lock.state == .unlocked {
                    SkinRoot()
                } else {
                    LockScreen(
                        state: lock.state,
                        onUnlock: { Task { await lock.authenticate() } },
                        onContinueWithoutLock: lock.continueWithoutLock
                    )
                }
                if scenePhase != .active || screenCaptured {
                    PrivacyCover()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIScreen.capturedDidChangeNotification)) { _ in
                screenCaptured = UIScreen.main.isCaptured
            }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                Task { await lock.didBecomeActive() }
            case .background:
                lock.didEnterBackground()
                // Leaving the app ends the processing session: nothing may stay in temporary folders (task 5.4).
                TempFiles.clear()
            default:
                break
            }
        }
    }
}
