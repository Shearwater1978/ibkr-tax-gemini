import LocalAuthentication
import SwiftUI

/// Face ID, Touch ID or the device passcode; replaceable in tests.
protocol DeviceOwnerAuthenticator {
    func canAuthenticate() -> Bool
    func authenticate(reason: String) async -> Bool
}

struct LocalAuthenticator: DeviceOwnerAuthenticator {
    func canAuthenticate() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    func authenticate(reason: String) async -> Bool {
        (try? await LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }
}

/**
 * App lock (device-hardening spec, auto-lock), the counterpart of the Android `MainActivity`
 * lock handling. Only leaving for the background starts the timer: the system unlock prompt
 * and the app switcher make the app inactive, not backgrounded.
 */
@MainActor
final class AppLock: ObservableObject {
    enum State {
        case locked

        /// The device has no passcode, so the app cannot verify the user.
        case noScreenLock

        case unlocked
    }

    @Published private(set) var state: State = .locked

    private let policy: AppLockPolicy
    private let authenticator: DeviceOwnerAuthenticator
    private var backgroundedAt: Date?
    private var authenticating = false

    /// Prompt automatically once per lock; after a cancel the user taps Unlock, so cancelling cannot loop.
    private var autoPrompt = true

    init(policy: AppLockPolicy = AppLockPolicy(), authenticator: DeviceOwnerAuthenticator = LocalAuthenticator()) {
        self.policy = policy
        self.authenticator = authenticator
    }

    func didBecomeActive(now: Date = Date()) async {
        if state == .unlocked && policy.shouldLock(backgroundedAt: backgroundedAt, now: now) {
            state = .locked
            autoPrompt = true
        }
        if state == .locked && autoPrompt {
            autoPrompt = false
            await authenticate()
        }
    }

    func didEnterBackground(now: Date = Date()) {
        if state == .unlocked && backgroundedAt == nil { backgroundedAt = now }
    }

    func authenticate() async {
        guard !authenticating else { return }
        guard authenticator.canAuthenticate() else {
            state = .noScreenLock
            return
        }
        authenticating = true
        defer { authenticating = false }
        if await authenticator.authenticate(reason: Strings.lockPromptReason) { unlock() } else { state = .locked }
    }

    func continueWithoutLock() {
        unlock()
    }

    private func unlock() {
        state = .unlocked
        backgroundedAt = nil
    }
}

/// Shown instead of the app until the user unlocks; it reveals no portfolio data.
struct LockScreen: View {
    let state: AppLock.State
    let onUnlock: () -> Void
    let onContinueWithoutLock: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(Strings.appName).font(.title2)
            if state == .noScreenLock {
                Text(Strings.lockNoScreenLock).multilineTextAlignment(.center)
                Button(Strings.lockContinue, action: onContinueWithoutLock).buttonStyle(.bordered)
            } else {
                Text(Strings.lockLocked).multilineTextAlignment(.center)
                Button(Strings.lockUnlock, action: onUnlock).buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
    }
}

/// Covers the app in the app switcher snapshot and while the screen is recorded or mirrored
/// (device-hardening spec; iOS cannot block screenshots themselves).
struct PrivacyCover: View {
    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground).ignoresSafeArea()
            Text(Strings.appName).font(.title2)
        }
    }
}
