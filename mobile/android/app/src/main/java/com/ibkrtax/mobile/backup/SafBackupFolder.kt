package com.ibkrtax.mobile.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException

/**
 * [BackupFolder] over a folder confirmed with the Storage Access Framework
 * (ACTION_OPEN_DOCUMENT_TREE). With [subfolder] set, backups live in that folder inside
 * the confirmed one, created on first use.
 */
class SafBackupFolder(private val context: Context, private val treeUri: Uri, private val subfolder: String? = null) : BackupFolder {
    private fun root(): DocumentFile {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
        if (tree == null || !tree.exists() || !tree.canWrite()) throw FolderAccessLostException()
        if (subfolder == null) return tree
        val existing = tree.findFile(subfolder)
        if (existing != null && existing.isDirectory) return existing
        return tree.createDirectory(subfolder) ?: throw IOException("Could not create the backup folder")
    }

    override fun write(name: String, bytes: ByteArray) = access {
        val folder = root()
        val file = folder.findFile(name) ?: folder.createFile(MIME, name) ?: throw IOException("Could not create backup file")
        // "wt" truncates, so replacing a longer file leaves no stale tail.
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: throw IOException("Could not open backup file")
    }

    override fun read(name: String): ByteArray? = access {
        root().findFile(name)?.let { file -> context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } }
    }

    override fun list(): List<String> = access { root().listFiles().mapNotNull { it.name } }

    override fun delete(name: String) = access {
        root().findFile(name)?.delete()
        Unit
    }

    private fun <T> access(block: () -> T): T =
        try {
            block()
        } catch (e: SecurityException) {
            throw FolderAccessLostException(e)
        } catch (e: FileNotFoundException) {
            throw FolderAccessLostException(e)
        }

    companion object {
        private const val MIME = "application/octet-stream"

        fun isLocalOnly(treeUri: Uri): Boolean = BackupLocationKind.of(treeUri.authority) == BackupLocationKind.PHONE

        /** A readable path for Settings, e.g. "Backups › IBKR Tax Assistant backups". */
        fun displayName(context: Context, treeUri: Uri, subfolder: String?): String {
            val parent = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "?"
            return if (subfolder == null) parent else "$parent › $subfolder"
        }

        /** True when the confirmed folder already holds a backup manifest, so no subfolder is needed. */
        fun containsManifest(context: Context, treeUri: Uri): Boolean =
            DocumentFile.fromTreeUri(context, treeUri)?.findFile(BackupService.MANIFEST_FILE) != null
    }
}
