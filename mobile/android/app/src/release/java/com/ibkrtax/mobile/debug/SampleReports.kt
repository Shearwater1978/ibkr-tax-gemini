package com.ibkrtax.mobile.debug

import android.content.Context

/** Release stub: sample reports are not packaged and the debug import action is hidden. */
object SampleReports {
    const val AVAILABLE = false

    val names: List<String> = emptyList()

    fun read(context: Context, name: String): ByteArray =
        throw UnsupportedOperationException("Sample reports exist only in debug builds")
}
