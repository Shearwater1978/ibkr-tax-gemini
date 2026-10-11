package com.ibkrtax.mobile.security

import com.ibkrtax.mobile.backup.FolderErasure
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataEraserTest {
    private val steps = mutableListOf<String>()

    private fun eraser(folder: () -> FolderErasure = { FolderErasure(11, 3, folderReachable = true) }, keys: () -> Unit = {}) = DataEraser(
        eraseFolder = { steps += "folder"; folder() },
        eraseBackupKeys = { steps += "backup keys"; keys() },
        deleteLocalData = { steps += "local data" },
        deleteDeviceKeys = { steps += "device keys" },
    )

    @Test
    fun runsEveryStepInOrder() = runTest {
        val result = eraser().eraseAll()
        assertEquals(listOf("folder", "backup keys", "local data", "device keys"), steps)
        assertTrue(result.complete)
        assertEquals(3, result.folder!!.filesDeleted)
    }

    @Test
    fun aFailingStepDoesNotStopTheOthers() = runTest {
        val result = eraser(folder = { throw IOException("offline") }, keys = { throw IllegalStateException("block store") }).eraseAll()
        assertEquals(listOf("folder", "backup keys", "local data", "device keys"), steps)
        assertEquals(listOf("backup folder", "backup keys"), result.failedSteps)
        assertFalse(result.complete)
    }

    @Test
    fun anUnreachableFolderOrLeftoverFilesAreNotComplete() = runTest {
        assertFalse(eraser(folder = { FolderErasure(11, 0, folderReachable = false) }).eraseAll().complete)
        assertFalse(eraser(folder = { FolderErasure(11, 2, folderReachable = true, left = 1) }).eraseAll().complete)
    }
}
