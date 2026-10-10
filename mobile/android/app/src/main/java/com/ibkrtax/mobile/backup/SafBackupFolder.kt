package com.ibkrtax.mobile.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException

/** [BackupFolder] over a folder chosen with the Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE). */
class SafBackupFolder(private val context: Context, private val treeUri: Uri) : BackupFolder {
    private fun root(): DocumentFile {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.exists() || !root.canWrite()) throw FolderAccessLostException()
        return root
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

        /** Device-only storage providers: a lost phone would also lose the backup. */
        private val LOCAL_AUTHORITIES = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
        )

        fun isLocalOnly(treeUri: Uri): Boolean = treeUri.authority in LOCAL_AUTHORITIES

        /** A readable folder name for Settings, e.g. "IBKR backups". */
        fun displayName(context: Context, treeUri: Uri): String =
            DocumentFile.fromTreeUri(context, treeUri)?.name ?: DocumentsContract.getTreeDocumentId(treeUri)
    }
}
