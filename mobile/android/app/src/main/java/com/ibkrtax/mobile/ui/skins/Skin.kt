package com.ibkrtax.mobile.ui.skins

import androidx.compose.runtime.Composable
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.ui.skins.classic.ClassicSkin

/** Stored by name; never rename an entry, or users of it fall back to Classic. */
enum class SkinId { CLASSIC }

/**
 * A complete user interface after unlock (mobile-skins spec): it owns its theme, navigation and
 * screens, and reads only [AppStates], so data, numbers and security stay shared.
 */
interface Skin {
    val id: SkinId
    val nameRes: Int
    val descriptionRes: Int

    @Composable
    fun App(states: AppStates)
}

object SkinCatalog {
    val skins: List<Skin> = listOf(ClassicSkin)

    val default: Skin = ClassicSkin

    /** The skin for a stored name; missing or unknown names (e.g. after a downgrade) give Classic. */
    fun resolve(stored: String?): Skin = skins.firstOrNull { it.id.name == stored } ?: default
}
