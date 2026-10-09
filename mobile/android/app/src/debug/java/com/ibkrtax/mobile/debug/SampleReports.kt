package com.ibkrtax.mobile.debug

import android.content.Context

/**
 * Debug builds only: synthetic fixtures from mobile/fixtures, used to exercise the
 * portfolio screens while report import stays disabled. Release builds use a stub.
 */
object SampleReports {
    const val AVAILABLE = true

    val names: List<String> = listOf("valid_basic.csv", "valid_followup.csv")

    fun read(context: Context, name: String): ByteArray =
        context.assets.open("flex-query/$name").use { it.readBytes() }
}
