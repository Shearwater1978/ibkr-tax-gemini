import SwiftUI

/**
 * Restore on a new phone (Android `RestoreSection`): pick the backup folder, then enter the
 * passphrase or recovery code. Shown while no keys and no reports exist.
 */
struct RestoreSection: View {
    @ObservedObject var data: DataState
    @State private var picking = false
    @State private var running = false
    @State private var message: String?
    @State private var folder: URL?
    @State private var useCode = false
    @State private var secret = ""
    @State private var wrong = false

    var body: some View {
        Section {
            Text(Strings.restoreBody).font(.footnote)
            Button(running ? Strings.restoreRunning : Strings.restoreChoose) { message = nil; picking = true }.disabled(running)
            if folder != nil {
                Text(Strings.restoreUnlockBody).font(.footnote)
                Picker("", selection: $useCode) {
                    Text(Strings.restoreUsePassphrase).tag(false)
                    Text(Strings.restoreUseCode).tag(true)
                }
                .pickerStyle(.segmented)
                .onChange(of: useCode) { _ in secret = "" }
                if useCode {
                    TextField(Strings.restoreUseCode, text: $secret).textInputAutocapitalization(.characters).autocorrectionDisabled()
                } else {
                    SecureField(Strings.restoreUsePassphrase, text: $secret)
                }
                if wrong { Text(Strings.restoreWrong).font(.footnote).foregroundStyle(.red) }
                Button(running ? Strings.restoreRunning : Strings.restoreAction) { run(secret: useCode ? .recoveryCode(secret) : .passphrase(secret)) }
                    .disabled(secret.trimmingCharacters(in: .whitespaces).isEmpty || running)
            }
            if let message { Text(message) }
        } header: {
            Text(Strings.restoreTitle)
        }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.folder]) { result in
            guard case .success(let url) = result else { return }
            folder = url
            run(secret: nil)
        }
    }

    private func run(secret: RestoreSecret?) {
        guard let folder else { return }
        running = true
        Task {
            let outcome = await data.restore(from: folder, secret: secret)
            running = false
            wrong = outcome == .wrongSecret
            switch outcome {
            case .needsSecret, .wrongSecret:
                break
            case .restored(let reports, let inserted, let already, let unreadable, let manual):
                self.folder = nil
                self.secret = ""
                message = Strings.restoreDone(reports, inserted, already: already, unreadable: unreadable, manual: manual)
            case .noBackup, .accessLost, .newerVersion:
                self.folder = nil
                message = outcome == .newerVersion ? Strings.restoreNewer : (outcome == .accessLost ? Strings.restoreAccessLost : Strings.restoreNoBackup)
            }
        }
    }
}

/**
 * "Erase all data" in Settings (Android `EraseDataSection`, task 7.6). Confirmed by typing a word
 * rather than the passphrase, since a forgotten passphrase is a reason to erase.
 */
struct EraseDataSection: View {
    @ObservedObject var data: DataState
    @State private var confirming = false
    @State private var typed = ""
    @State private var running = false
    @State private var result: ErasureResult?

    var body: some View {
        Section {
            Text(Strings.eraseBody).font(.footnote)
            Button(running ? Strings.eraseRunning : Strings.eraseAction, role: .destructive) { typed = ""; confirming = true }.disabled(running)
        } header: {
            Text(Strings.eraseTitle)
        }
        .alert(Strings.eraseConfirmTitle, isPresented: $confirming) {
            TextField(Strings.eraseConfirmWord, text: $typed).textInputAutocapitalization(.characters)
            Button(Strings.eraseConfirmButton, role: .destructive) {
                guard typed.trimmingCharacters(in: .whitespaces) == Strings.eraseConfirmWord else { return }
                running = true
                Task {
                    result = await data.eraseAll()
                    running = false
                }
            }
            Button(Strings.cancel, role: .cancel) {}
        } message: {
            Text(Strings.eraseConfirmBody(Strings.eraseConfirmWord))
        }
        .alert(Strings.eraseDoneTitle, isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            Button("OK") { result = nil }
        } message: {
            Text(result.map(Self.summary) ?? "")
        }
    }

    static func summary(_ result: ErasureResult) -> String {
        var parts = [Strings.eraseDoneBody(result.folder?.removedRecords ?? 0, result.folder?.filesDeleted ?? 0)]
        if let folder = result.folder, !folder.folderReachable { parts.append(Strings.eraseFolderUnreachable) }
        if let left = result.folder?.left, left > 0 { parts.append(Strings.eraseFilesLeft(left)) }
        if !result.failedSteps.isEmpty { parts.append(Strings.eraseStepsFailed(result.failedSteps.joined(separator: ", "))) }
        return parts.joined(separator: "\n\n")
    }
}
