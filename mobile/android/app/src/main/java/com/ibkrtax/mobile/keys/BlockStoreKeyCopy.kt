package com.ibkrtax.mobile.keys

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import kotlinx.coroutines.tasks.await

/** A copy of the data key that follows the user to a new phone (key-management spec, Block Store). */
interface CloudKeyCopy {
    /** True only when the copy would be end-to-end encrypted. */
    suspend fun isAvailable(): Boolean

    suspend fun store(dataKey: ByteArray)

    suspend fun retrieve(): ByteArray?

    suspend fun delete()
}

/**
 * Android Block Store: synced with the user's Android backup and end-to-end encrypted
 * with the device's screen lock (Android 9+). Never used when that encryption is off.
 */
class BlockStoreKeyCopy(context: Context) : CloudKeyCopy {
    private val client = Blockstore.getClient(context.applicationContext)

    override suspend fun isAvailable(): Boolean =
        runCatching { client.isEndToEndEncryptionAvailable.await() }.getOrDefault(false)

    override suspend fun store(dataKey: ByteArray) {
        check(isAvailable()) { "Block Store end-to-end encryption is not available" }
        client.storeBytes(
            StoreBytesData.Builder()
                .setKey(KEY)
                .setBytes(dataKey)
                .setShouldBackupToCloud(true)
                .build(),
        ).await()
    }

    override suspend fun retrieve(): ByteArray? =
        runCatching {
            client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()).await()
                .blockstoreDataMap[KEY]?.bytes
        }.getOrNull()

    override suspend fun delete() {
        runCatching { client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build()).await() }
    }

    private companion object {
        const val KEY = "ibkrtax.backup.data-key.v1"
    }
}
