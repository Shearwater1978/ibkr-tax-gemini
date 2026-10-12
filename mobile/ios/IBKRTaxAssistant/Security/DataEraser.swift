/// What erasure could do; `failedSteps` lists steps that threw (their data may remain).
struct ErasureResult: Equatable {
    var folder: FolderErasure?
    var failedSteps: [String]

    var complete: Bool { failedSteps.isEmpty && folder?.folderReachable != false && folder?.left == 0 }
}

/**
 * Erase all data (data-anonymization "Erasure and minimization", key-management "Erasure", task
 * 7.6), ported from the Android `DataEraser`. The order matters: backup files first, while the
 * folder is known; then the backup keys, which makes any copy left in the provider's trash
 * unreadable; then the database, settings and files on the device; device keys last. Every step
 * runs even if an earlier one fails.
 */
struct DataEraser {
    let eraseFolder: () throws -> FolderErasure
    let eraseBackupKeys: () throws -> Void
    let deleteLocalData: () throws -> Void
    let deleteDeviceKeys: () throws -> Void

    func eraseAll() -> ErasureResult {
        var failed: [String] = []
        let folder: FolderErasure?
        do { folder = try eraseFolder() } catch { folder = nil; failed.append("backup folder") }
        do { try eraseBackupKeys() } catch { failed.append("backup keys") }
        do { try deleteLocalData() } catch { failed.append("local data") }
        do { try deleteDeviceKeys() } catch { failed.append("device keys") }
        return ErasureResult(folder: folder, failedSteps: failed)
    }
}
