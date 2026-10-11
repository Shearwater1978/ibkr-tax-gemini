package com.ibkrtax.mobile.ui.common

import com.ibkrtax.mobile.backup.BackupLocationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrokerAndFolderTest {
    @Test
    fun storedBrokerNamesResolveExactly() {
        assertEquals(BrokerId.INTERACTIVE_BROKERS, BrokerId.resolve("INTERACTIVE_BROKERS"))
        assertNull(BrokerId.resolve(null)) // not chosen yet
        assertNull(BrokerId.resolve("DEGIRO")) // not supported (yet)
    }

    @Test
    fun newBackupFoldersUseTheNewNameUnlessAnOldOneIsThere() {
        assertEquals("Portfolio Companion backups", BackupLocationKind.subfolderFor { false })
        assertEquals("IBKR Tax Assistant backups", BackupLocationKind.subfolderFor { it == "IBKR Tax Assistant backups" })
    }
}
