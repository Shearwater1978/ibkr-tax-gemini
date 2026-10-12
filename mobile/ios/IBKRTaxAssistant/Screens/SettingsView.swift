import SwiftUI
import UniformTypeIdentifiers

/// Settings, ported from the Android `SettingsScreen`; every skin shows the same sections.
struct SettingsView: View {
    @ObservedObject var data: DataState

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        List {
            Section {
                Text(Strings.settingsPrivacy)
            } header: {
                Text(Strings.settingsTitle)
            }
            SkinAndBrokerSettingsSection()
            PriceKeySection()
            BackupEncryptionSection(data: data)
            BackupFolderSection(data: data)
            PrivacySection()
            EraseDataSection(data: data)
            Section {
                Text("Version \(version)").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .onAppear { data.reload() }
    }
}

/// What the encryption section shows (Android `EncryptionStep`).
private enum EncryptionStep {
    case idle
    case createForm
    case changeForm
    case newCodeForm
    case working
}

/// Codes waiting for "I have written it down"; keys or the new code are stored only then.
private enum PendingCode: Identifiable {
    case setup(PendingKeySetup)
    case replacement(PendingRecoveryCode)

    var id: String { code }

    var code: String {
        switch self {
        case .setup(let pending): return pending.recoveryCode
        case .replacement(let pending): return pending.recoveryCode
        }
    }
}

/**
 * Backup encryption (Android `BackupEncryptionSettings`): set up a passphrase, show the recovery
 * code once, change the passphrase, or replace a lost recovery code.
 */
private struct BackupEncryptionSection: View {
    @ObservedObject var data: DataState
    @State private var step = EncryptionStep.idle
    @State private var error: String?
    @State private var pendingCode: PendingCode?

    var body: some View {
        Section {
            Text(Strings.encryptionBody).font(.footnote)
            switch step {
            case .working:
                HStack { ProgressView(); Text(Strings.encryptionWorking) }
            case .createForm:
                PassphraseForm(askCurrent: false, askNew: true, error: error, submit: Strings.encryptionCreate, onCancel: cancel) { _, new, confirm in
                    guard let problem = Self.problem(new, confirm) else { return run { pendingCode = .setup(try await keys().prepare(passphrase: new)) } }
                    error = problem
                }
            case .changeForm:
                PassphraseForm(askCurrent: true, askNew: true, error: error, submit: Strings.encryptionChangeSubmit, onCancel: cancel) { current, new, confirm in
                    guard let problem = Self.problem(new, confirm) else {
                        return run(retry: .changeForm) {
                            try await keys().changePassphrase(current: current, new: new)
                            await syncManifest()
                        }
                    }
                    error = problem
                }
            case .newCodeForm:
                PassphraseForm(askCurrent: true, askNew: false, error: error, submit: Strings.recoveryNewSubmit, onCancel: cancel) { current, _, _ in
                    run(retry: .newCodeForm) { pendingCode = .replacement(try await keys().prepareNewRecoveryCode(passphrase: current)) }
                }
            case .idle:
                switch data.keyStatus {
                case nil:
                    Text(Strings.loading)
                case .notSetUp?:
                    Button(Strings.encryptionSetUp) { step = .createForm }
                case .ready?:
                    Text(Strings.encryptionReady)
                    Text(Strings.encryptionNoCloudCopy).font(.footnote).foregroundStyle(.secondary)
                    Button(Strings.encryptionChange) { step = .changeForm }
                    Button(Strings.recoveryNew) { step = .newCodeForm }
                    Text(Strings.recoveryNewHint).font(.footnote).foregroundStyle(.secondary)
                }
            }
        } header: {
            Text(Strings.encryptionTitle)
        }
        .sheet(item: $pendingCode) { pending in
            RecoveryCodeSheet(code: pending.code, replaces: { if case .replacement = pending { return true } else { return false } }()) {
                Task {
                    let keys = try? keys()
                    switch pending {
                    case .setup(let setup): try? keys?.confirm(setup)
                    case .replacement(let replacement): try? keys?.confirmRecoveryCode(replacement)
                    }
                    await syncManifest()
                    pendingCode = nil
                    data.changed()
                }
            }
        }
    }

    private func keys() throws -> BackupKeys { try AppContainer.shared.backupKeys() }

    private func cancel() {
        step = .idle
        error = nil
    }

    /// Runs slow key work; a wrong current passphrase returns to `retry` with an error.
    private func run(retry: EncryptionStep = .createForm, _ work: @escaping () async throws -> Void) {
        error = nil
        step = .working
        Task {
            do {
                try await work()
                step = .idle
                data.changed()
            } catch {
                self.error = Strings.passphraseWrongCurrent
                step = retry
            }
        }
    }

    /// Keeps the wrapped-key manifest in the backup folder current after a key change (best effort; retried with backups).
    private func syncManifest() async {
        _ = await Task.detached { try? AppContainer.shared.backup().syncManifest() }.value
    }

    static func problem(_ new: String, _ confirm: String) -> String? {
        if new != confirm { return Strings.passphraseMismatch }
        switch PassphrasePolicy.check(new) {
        case .tooShort?: return Strings.passphraseTooShort(PassphrasePolicy.minLength)
        case .tooSimple?: return Strings.passphraseTooSimple
        case nil: return nil
        }
    }
}

private struct PassphraseForm: View {
    let askCurrent: Bool
    let askNew: Bool
    let error: String?
    let submit: String
    let onCancel: () -> Void
    let onSubmit: (_ current: String, _ new: String, _ confirm: String) -> Void
    @State private var current = ""
    @State private var new = ""
    @State private var confirm = ""

    var body: some View {
        if askNew { Text(Strings.passphraseRule(PassphrasePolicy.minLength)).font(.footnote) }
        if askCurrent { SecureField(Strings.passphraseCurrent, text: $current) }
        if askNew {
            SecureField(Strings.passphraseNew, text: $new)
            SecureField(Strings.passphraseConfirm, text: $confirm)
        }
        if let error { Text(error).font(.footnote).foregroundStyle(.red) }
        HStack {
            Button(submit) { onSubmit(current, new, confirm) }.buttonStyle(.borderedProminent)
            Spacer()
            Button(Strings.cancel, action: onCancel).buttonStyle(.borderless)
        }
    }
}

/// Shown exactly once; it cannot be swiped away before "I have written down the recovery code".
private struct RecoveryCodeSheet: View {
    let code: String
    /// A replacement code warns that the old one stops working.
    let replaces: Bool
    let onDone: () -> Void
    @State private var saved = false
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(Strings.recoveryTitle).font(.title2.bold())
            Text(Strings.recoveryBody).font(.footnote)
            Text(RecoveryCode.format(code)).font(.system(.title3, design: .monospaced)).textSelection(.disabled)
            Button(Strings.recoveryCopy) {
                // On this iPhone only (no Universal Clipboard) and cleared after a minute, like the Android SensitiveClipboard.
                UIPasteboard.general.setItems([[UTType.plainText.identifier: RecoveryCode.format(code)]],
                                              options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
                copied = true
            }
            if copied { Text(Strings.recoveryCopied).font(.footnote) }
            if replaces { Text(Strings.recoveryNewReplaces).font(.footnote) }
            Toggle(Strings.recoverySaved, isOn: $saved)
            Button(Strings.recoveryDone, action: onDone).buttonStyle(.borderedProminent).disabled(!saved)
            Spacer()
        }
        .padding(24)
        .interactiveDismissDisabled()
    }
}

/**
 * Backup location (Android `BackupFolderSettings`, task 1.5): the user picks a folder in the system
 * picker, the app keeps a bookmark and stores backups in its own subfolder there, warning when the
 * folder exists only on this iPhone. Changing the location keeps old backups where they are.
 */
private struct BackupFolderSection: View {
    @ObservedObject var data: DataState
    @State private var picking = false
    @State private var message: String?
    @State private var current: (kind: BackupLocationKind, path: String)?

    var body: some View {
        Section {
            Text(Strings.folderBody).font(.footnote)
            if let current {
                Text(Strings.locationCurrent(label(current.kind), current.path))
                if current.kind == .phone { Text(Strings.folderLocalOnly).font(.footnote).foregroundStyle(.red) }
                Text(Strings.locationChangeHint).font(.footnote).foregroundStyle(.secondary)
            }
            Button(Strings.locationChoose) { message = nil; picking = true }
            Text(Strings.locationChooseHint).font(.footnote).foregroundStyle(.secondary)
            if let message { Text(message) }
        } header: {
            Text(Strings.locationTitle)
        }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.folder]) { result in
            guard case .success(let url) = result else { return }
            Task {
                message = await Task.detached { Self.use(url) }.value
                await load()
                data.changed()
            }
        }
        .task { await load() }
    }

    private func load() async {
        current = await Task.detached { () -> (BackupLocationKind, String)? in
            guard let folder = try? AppContainer.shared.backupLocation().folder() else { return nil }
            return (BackupLocationKind.of(folder.root), ([folder.root.lastPathComponent] + [folder.subfolder].compactMap { $0 }).joined(separator: "/"))
        }.value
    }

    private func label(_ kind: BackupLocationKind) -> String {
        switch kind {
        case .iCloud: return Strings.locationLabelICloud
        case .phone: return Strings.locationLabelPhone
        case .other: return Strings.locationLabelOther
        }
    }

    /// Uses the picked folder (or the app's subfolder in it), then writes the manifest there.
    nonisolated private static func use(_ url: URL) -> String {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let bookmark = try? url.bookmarkData() else { return Strings.folderNotUsable }
        let exists = { (name: String) in FileManager.default.fileExists(atPath: url.appendingPathComponent(name).path) }
        // A folder that already holds a manifest is used as it is (for example the app's own folder picked again).
        let subfolder = exists(BackupService.manifestFile) ? nil : BackupLocation.subfolder(hasFolder: exists)
        guard (try? AppContainer.shared.backupLocation().set(BackupLocation(bookmark: bookmark, subfolder: subfolder))) != nil,
              let backup = try? AppContainer.shared.backup() else { return Strings.folderNotUsable }
        switch try? backup.syncManifest() {
        case .backedUp?: return Strings.folderSaved
        case .accessLost?: return Strings.backupAccessLost
        default: return (try? AppContainer.shared.backupKeys().status()) == .ready ? Strings.folderSaved : Strings.folderSavedNoKeys
        }
    }
}
