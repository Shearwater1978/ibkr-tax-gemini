package com.ibkrtax.mobile.ui.skins.classic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.ui.Formats
import com.ibkrtax.mobile.ui.common.QuoteSourceLabel
import java.math.BigDecimal

/** Per-position breakdown in the position's own currency (mobile-position-performance spec). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionDetailsSheet(position: PositionPerformance, onDismiss: () -> Unit, onHistory: () -> Unit = {}) {
    val currency = position.currency
    val unavailable = stringResource(R.string.details_unavailable)
    fun money(value: BigDecimal?) = value?.let { Formats.money(it, currency) } ?: unavailable
    fun signed(value: BigDecimal?) = value?.let { Formats.signedMoney(it, currency) } ?: unavailable

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(position.ticker, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(if (position.isSold) R.string.details_sold else R.string.details_held, currency),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            val row = position.row
            DetailLine(stringResource(R.string.details_quantity), row?.holding?.quantity?.let(Formats::quantity) ?: "0")
            if (row != null) {
                DetailLine(stringResource(R.string.details_average), Formats.number(row.holding.averagePrice, 2, 4) + " " + currency)
                DetailLine(stringResource(R.string.details_invested), money(position.invested))
                DetailLine(stringResource(R.string.details_value), money(row.marketValue))
                QuoteSourceLabel(row.price?.quote?.source)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailLine(stringResource(R.string.details_price_gain), signed(position.priceGain), position.priceGain)
            DetailLine(stringResource(R.string.details_sales_gain), signed(position.realizedGain), position.realizedGain)
            DetailLine(stringResource(R.string.details_dividends), signed(position.dividends.net), position.dividends.net)
            Text(
                stringResource(
                    R.string.dividends_line,
                    Formats.money(position.dividends.gross, currency),
                    Formats.money(position.dividends.tax, currency),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (position.dividends.gross.signum() != 0 || position.dividends.tax.signum() != 0) {
                TextButton(onClick = onHistory) { Text(stringResource(R.string.payer_history_title) + " ›") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val total = position.total
            val percent = position.totalPercent?.let { " (" + Formats.signedPercent(it) + ")" }.orEmpty()
            DetailLine(stringResource(R.string.details_total), signed(total) + percent, total, bold = true)
            if (!position.realizedComplete) {
                Text(stringResource(R.string.details_sales_incomplete), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.details_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, sign: BigDecimal? = null, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, color = signColorOf(sign), fontWeight = if (bold) FontWeight.Bold else null)
    }
}

private fun signColorOf(value: BigDecimal?): Color = when (value?.signum()) {
    1 -> Color(0xFF2E7D32)
    -1 -> Color(0xFFC62828)
    else -> Color.Unspecified
}
