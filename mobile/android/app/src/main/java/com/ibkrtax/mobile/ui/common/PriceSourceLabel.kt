package com.ibkrtax.mobile.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.prices.RefreshProgress
import com.ibkrtax.mobile.prices.PriceFailure

/**
 * Where a displayed price came from (mobile-market-prices "Show quote currency and freshness",
 * "Attribution"): the provider that produced the cached quote, not the current choice.
 * "Source: Twelve Data" opens https://twelvedata.com in a browser tab.
 */
@Composable
fun QuoteSourceLabel(source: PriceSource?, style: TextStyle = MaterialTheme.typography.bodySmall, color: Color = Color.Unspecified) {
    val context = LocalContext.current
    when (source) {
        PriceSource.TWELVE_DATA -> Text(
            stringResource(R.string.td_source),
            style = style.copy(textDecoration = TextDecoration.Underline),
            color = color,
            modifier = Modifier.clickable(role = Role.Button) { openInChromeTab(context, TWELVE_DATA_HOME) },
        )
        PriceSource.FINNHUB -> Text(stringResource(R.string.source_finnhub), style = style, color = color)
        PriceSource.YAHOO -> Text(stringResource(R.string.source_yahoo), style = style, color = color)
        // Quotes cached before sources were recorded, or none at all.
        PriceSource.NONE, null -> Unit
    }
}

/** One line per distinct source among the prices shown in [rows], Twelve Data first. */
@Composable
fun PriceSourcesLine(rows: List<PositionRow>, style: TextStyle = MaterialTheme.typography.bodySmall, color: Color = Color.Unspecified) {
    val sources = priceSources(rows)
    if (sources.isEmpty()) return
    Column { sources.forEach { QuoteSourceLabel(it, style, color) } }
}

/** The distinct sources of the prices in [rows], in a stable order. */
fun priceSources(rows: List<PositionRow>): List<PriceSource> {
    val present = rows.mapNotNull { it.price?.quote?.source }.toSet()
    return listOf(PriceSource.TWELVE_DATA, PriceSource.FINNHUB, PriceSource.YAHOO).filter { it in present }
}

/**
 * The price status line shared by the skins: what to say about the last refresh for the chosen
 * provider [chosen]; null when there is nothing to say.
 */
@Composable
fun priceStatusText(outcome: RefreshOutcome?, chosen: PriceSource): String? = when (outcome) {
    null -> stringResource(R.string.prices_refreshing)
    RefreshOutcome.NoProvider -> stringResource(R.string.prices_no_provider)
    RefreshOutcome.NoKey -> stringResource(if (chosen == PriceSource.TWELVE_DATA) R.string.prices_no_key_td else R.string.prices_no_key)
    RefreshOutcome.Updated -> null
    is RefreshOutcome.Failed -> stringResource(
        R.string.prices_failed,
        if (outcome.reason == PriceFailure.RATE_LIMITED && outcome.source == PriceSource.TWELVE_DATA) {
            stringResource(R.string.prices_failure_td_limit)
        } else {
            stringResource(
                when (outcome.reason) {
                    PriceFailure.OFFLINE -> R.string.prices_failure_offline
                    PriceFailure.INVALID_KEY -> R.string.prices_failure_invalid_key
                    PriceFailure.RATE_LIMITED -> R.string.prices_failure_rate_limited
                    PriceFailure.PROVIDER_ERROR -> R.string.prices_failure_provider
                },
            )
        },
    )
}

/** "Twelve Data · 24 of 80 · about 7 min left" while Twelve Data is being asked, else null. */
@Composable
fun twelveDataProgressText(progress: RefreshProgress?): String? =
    progress?.takeIf { it.source == PriceSource.TWELVE_DATA && it.total > 0 }?.let {
        stringResource(R.string.prices_progress_td, it.done, it.total, it.minutesLeft ?: 0)
    }
