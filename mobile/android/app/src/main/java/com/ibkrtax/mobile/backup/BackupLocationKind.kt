package com.ibkrtax.mobile.backup

/** The two backup locations offered in Settings (backup-location spec "Choose the backup folder"). */
enum class BackupLocationKind {
    PHONE,
    GOOGLE_DRIVE,
    ;

    companion object {
        const val DRIVE_PACKAGE = "com.google.android.apps.docs"
        const val DRIVE_AUTHORITY = "com.google.android.apps.docs.storage"

        /** Device-only storage providers. */
        val PHONE_AUTHORITIES = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
        )

        /** The app's own folder inside the folder the user confirms (mobile-neutral-presentation). */
        const val SUBFOLDER = "Portfolio Companion backups"

        /** The folder name used before the app was renamed. */
        const val LEGACY_SUBFOLDER = "IBKR Tax Assistant backups"

        /** Reuses a folder from before the rename if the confirmed folder has one, so backups are not split. */
        fun subfolderFor(hasFolder: (String) -> Boolean): String = if (hasFolder(LEGACY_SUBFOLDER)) LEGACY_SUBFOLDER else SUBFOLDER

        /** Which offered location a picked folder belongs to; null for other storage apps. */
        fun of(authority: String?): BackupLocationKind? = when (authority) {
            DRIVE_AUTHORITY -> GOOGLE_DRIVE
            in PHONE_AUTHORITIES -> PHONE
            else -> null
        }
    }
}
