package com.ibkrtax.mobile.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies a secret the way password managers do: marked sensitive (hidden from the
 * clipboard preview and keyboard history on Android 13+) and cleared after [clearAfterMillis],
 * but only if the clipboard still holds this copy.
 */
object SensitiveClipboard {
    const val CLEAR_AFTER_MILLIS = 60_000L

    fun copy(context: Context, label: String, text: String, clearAfterMillis: Long = CLEAR_AFTER_MILLIS) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        clipboard.setPrimaryClip(clip)
        // The system stamps the clip when it is set; that stamp identifies "our" copy later.
        val stamp = clipboard.primaryClipDescription?.timestamp ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            // Android may hide the clipboard from apps without focus; then leave it alone.
            if (clipboard.primaryClipDescription?.timestamp == stamp) clipboard.clearPrimaryClip()
        }, clearAfterMillis)
    }
}
