package com.ibkrtax.mobile.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.ui.skins.bento.BentoSkin
import com.ibkrtax.mobile.ui.skins.calm.CalmSkin
import com.ibkrtax.mobile.ui.skins.classic.ClassicSkin
import com.ibkrtax.mobile.ui.skins.focus.FocusSkin

/** Stored by name; never rename an entry, or users of it fall back to Classic. */
enum class SkinId { CLASSIC, CALM, BENTO, FOCUS }

/** Everything every skin must reach, directly or behind taps (mobile-skins "Keep feature parity"). */
enum class Feature {
    PORTFOLIO_VALUE_AND_TODAY,
    POSITION_DETAILS,
    SOLD_POSITIONS,
    OWN_CURRENCY_AND_USD,
    TOP_MOVERS,
    DIVIDENDS_BY_YEAR_AND_MONTH,
    PAYER_HISTORY,
    ACTIVITY_SEARCH_AND_FILTERS,
    REPORT_IMPORT,
    BACKUP_KEYS_AND_LOCATION,
    PRICE_KEY,
    DELETE_IMPORTS,
    APP_VERSION,
    SKIN_SETTING,
    MANUAL_ENTRY,
}

/**
 * A complete user interface after unlock (mobile-skins spec): it owns its theme, navigation and
 * screens, and reads only [AppStates], so data, numbers and security stay shared.
 */
interface Skin {
    val id: SkinId
    val nameRes: Int
    val descriptionRes: Int

    /** Where each [Feature] lives in this skin; reviewed per skin and checked to cover all features. */
    val features: Map<Feature, String>

    @Composable
    fun App(states: AppStates)

    /** A small drawn thumbnail for the picker; never shows the user's data. */
    @Composable
    fun Preview(modifier: Modifier)
}

object SkinCatalog {
    val skins: List<Skin> = listOf(ClassicSkin, CalmSkin, BentoSkin, FocusSkin)

    val default: Skin = ClassicSkin

    /** Preselected in the first-launch picker. */
    val firstLaunch: Skin = CalmSkin

    /** The skin for a stored name; missing or unknown names (e.g. after a downgrade) give Classic. */
    fun resolve(stored: String?): Skin = skins.firstOrNull { it.id.name == stored } ?: default
}
