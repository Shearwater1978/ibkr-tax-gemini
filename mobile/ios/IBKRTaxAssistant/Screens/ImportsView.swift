import SwiftUI
import UniformTypeIdentifiers

/**
 * Report imports, ported from the Android `ImportsScreen`. Importing stays off until backup
 * encryption and a backup folder are set up (mobile-app-core task 1.2); debug builds can also load
 * the synthetic sample reports, which are imported without a backup.
 */
struct ImportsView: View {
    @ObservedObject var data: DataState
    @State private var message: String?
    @State private var sampleMessages: [String] = []
    @State private var deleteTarget: ImportedReport?
    @State private var deleteAllOpen = false
    @State private var picking = false
    @State private var importing = false
    @State private var progress: String?
    @State private var fileResults: [String] = []
    @State private var passphrase = ""

    var body: some View {
        List {
            // A new phone without keys or reports can restore its backups first.
            if data.keyStatus == .notSetUp && data.history?.isEmpty == true {
                RestoreSection(data: data)
            }
            Section {
                Text(Strings.importsSupportedFormat)
                Button(importing ? Strings.importing : Strings.actionImportReport) { picking = true }
                    .disabled(!data.importReady || importing)
                if !data.importReady {
                    // Import stays off until backups work (mobile-app-core task 1.2); say which step is next.
                    Text(data.keyStatus == .ready ? Strings.importsUnavailableFolder : Strings.importsUnavailableKeys).font(.footnote).foregroundStyle(.secondary)
                }
                Text(Strings.importMultipleHint).font(.footnote).foregroundStyle(.secondary)
                if let progress { Text(progress) }
                ForEach(fileResults, id: \.self) { Text($0).font(.footnote) }
                if let message { Text(message) }
                let pending = data.history?.filter { $0.backupStatus == ReportImporter.backupPending }.count ?? 0
                if pending > 0 {
                    Text(Strings.backupPendingCount(pending)).font(.footnote)
                    Button(Strings.backupRetry) {
                        Task {
                            guard let retry = await data.retryBackups() else { return }
                            message = retry.accessLost ? Strings.backupAccessLost : Strings.backupRetried(retry.backedUp, retry.stillPending)
                        }
                    }
                }
            } header: {
                Text(Strings.importsTitle)
            }
            if SampleReports.available {
                Section(Strings.debugSamplesTitle) {
                    Button(Strings.debugSamplesAction) {
                        Task { sampleMessages = await data.loadSamples().map(Self.describe) }
                    }
                    ForEach(sampleMessages, id: \.self) { Text($0).font(.footnote) }
                }
            }
            Section(Strings.importsHistoryTitle) {
                if data.storageUnavailable {
                    Text(Strings.storageUnavailable)
                } else if let reports = data.history {
                    if reports.isEmpty { Text(Strings.importsEmpty) }
                    ForEach(reports, id: \.id) { report in
                        ReportRow(report: report, onDelete: { deleteTarget = report })
                    }
                    if !reports.isEmpty {
                        Button(Strings.deleteAllAction, role: .destructive) { deleteAllOpen = true }
                    }
                } else {
                    Text(Strings.loading)
                }
            }
        }
        .onAppear { data.reload() }
        // IBKR exports may be labelled as CSV, plain text or generic data by different apps.
        .fileImporter(isPresented: $picking, allowedContentTypes: [.commaSeparatedText, .plainText, .data], allowsMultipleSelection: true) { result in
            guard case .success(let urls) = result, !urls.isEmpty else { return }
            importing = true
            message = nil
            fileResults = []
            Task {
                fileResults = await data.importFiles(urls) { done, total in progress = Strings.importProgress(done, total) }
                progress = nil
                importing = false
            }
        }
        .confirmationDialog(Strings.deleteOneTitle, isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }),
                            titleVisibility: .visible, presenting: deleteTarget) { report in
            Button(Strings.deleteConfirm, role: .destructive) {
                Task { message = await data.deleteReport(report.id).map(Strings.deleteDone) }
            }
            Button(Strings.cancel, role: .cancel) {}
        } message: { _ in
            Text(Strings.deleteOneBody)
        }
        // "Delete all" needs the backup passphrase once backup encryption is set up (mobile-report-upload spec).
        .alert(Strings.deleteAllTitle, isPresented: $deleteAllOpen) {
            if data.keyStatus == .ready { SecureField(Strings.deleteAllPassphrase, text: $passphrase) }
            Button(Strings.deleteConfirm, role: .destructive) {
                let entered = passphrase
                passphrase = ""
                Task { message = await data.deleteAll(passphrase: entered).map(Strings.deleteDone) ?? Strings.deleteWrongPassphrase }
            }
            Button(Strings.cancel, role: .cancel) { passphrase = "" }
        } message: {
            Text(data.keyStatus == .ready ? Strings.deleteAllBody : Strings.deleteAllBodyNoKeys)
        }
    }

    private static func describe(_ sample: (name: String, result: ImportResult?)) -> String {
        switch sample.result {
        case .imported(_, let inserted, let skipped)?: return Strings.debugSamplesImported(sample.name, inserted, skipped)
        case .alreadyImported?: return Strings.debugSamplesAlready(sample.name)
        default: return Strings.debugSamplesRejected(sample.name)
        }
    }
}

private struct ReportRow: View {
    let report: ImportedReport
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Strings.importsHistoryItem(Formats.dateTime(report.importedAt), report.accountMasked ?? Strings.importsAccountUnknown))
            Text(Strings.importsHistoryCounts(report.inserted, report.skipped)).font(.footnote).foregroundStyle(.secondary)
            Text(backupStatus).font(.footnote).foregroundStyle(.secondary)
            Button(Strings.deleteOneAction, role: .destructive, action: onDelete).font(.footnote).buttonStyle(.borderless)
        }
    }

    private var backupStatus: String {
        switch report.backupStatus {
        case ReportImporter.backupDone?: return Strings.backupStatusDone
        case ReportImporter.backupPending?: return Strings.backupStatusPending
        default: return Strings.backupStatusNone
        }
    }
}
