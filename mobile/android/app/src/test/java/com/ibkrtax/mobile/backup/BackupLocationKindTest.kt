package com.ibkrtax.mobile.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupLocationKindTest {
    @Test
    fun recognisesPhoneAndGoogleDriveFolders() {
        assertEquals(BackupLocationKind.GOOGLE_DRIVE, BackupLocationKind.of("com.google.android.apps.docs.storage"))
        assertEquals(BackupLocationKind.PHONE, BackupLocationKind.of("com.android.externalstorage.documents"))
        assertEquals(BackupLocationKind.PHONE, BackupLocationKind.of("com.android.providers.downloads.documents"))
    }

    @Test
    fun otherStorageAppsMatchNeitherOption() {
        assertNull(BackupLocationKind.of("com.dropbox.android.document"))
        assertNull(BackupLocationKind.of(null))
    }
}
