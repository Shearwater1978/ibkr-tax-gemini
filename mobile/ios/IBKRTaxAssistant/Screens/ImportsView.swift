import SwiftUI

/**
 * Report imports, ported from the Android `ImportsScreen`. Importing your own reports stays off
 * until encrypted backups exist on iOS (mobile-app-core task 1.2); debug builds can load the
 * synthetic sample reports, which are imported without a backup.
 */
struct ImportsView: View {
    @ObservedObject var data: DataState
    @State private var message: String?
    @State private var sampleMessages: [String] = []
    @State private var deleteTarget: ImportedReport?
    @State private var deleteAllOpen = false

    var body: some View {
        List {
            Section {
                Text(Strings.importsSupportedFormat)
                Button(Strings.actionImportReport) {}
                    .disabled(true)
                Text(Strings.importsUnavailableBackup).font(.footnote).foregroundStyle(.secondary)
                if let message { Text(message) }
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
        .confirmationDialog(Strings.deleteOneTitle, isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }),
                            titleVisibility: .visible, presenting: deleteTarget) { report in
            Button(Strings.deleteConfirm, role: .destructive) {
                Task { message = await data.deleteReport(report.id).map(Strings.deleteDone) }
            }
            Button(Strings.cancel, role: .cancel) {}
        } message: { _ in
            Text(Strings.deleteOneBody)
        }
        // No backup keys exist on iOS yet, so no passphrase is asked (Android asks once keys are set up).
        .confirmationDialog(Strings.deleteAllTitle, isPresented: $deleteAllOpen, titleVisibility: .visible) {
            Button(Strings.deleteConfirm, role: .destructive) {
                Task { message = await data.deleteAll().map(Strings.deleteDone) }
            }
            Button(Strings.cancel, role: .cancel) {}
        } message: {
            Text(Strings.deleteAllBodyNoKeys)
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
