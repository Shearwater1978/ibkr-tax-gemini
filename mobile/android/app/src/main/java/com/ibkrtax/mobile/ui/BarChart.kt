package com.ibkrtax.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One bar; [description] is read by screen readers and UI tests (e.g. "May 2025: 104 USD"). */
data class ChartBar(val value: Double, val color: Color, val description: String)

/** Bars that share an axis label, e.g. one month with a bar per year. */
data class BarGroup(val label: String, val bars: List<ChartBar>, val valueLabel: String? = null)

/**
 * Minimal bar chart drawn with Canvas (no chart library). Values below zero are drawn as
 * empty bars; the shared scale is the largest value in the chart.
 */
@Composable
fun BarChart(groups: List<BarGroup>, modifier: Modifier = Modifier, height: Dp = 140.dp, onGroupClick: ((Int) -> Unit)? = null) {
    val max = groups.flatMap { it.bars }.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val small = MaterialTheme.typography.labelSmall
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        groups.forEachIndexed { index, group ->
            val click = onGroupClick?.let { Modifier.clickable { it(index) } } ?: Modifier
            Column(modifier = Modifier.weight(1f).then(click), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(group.valueLabel.orEmpty(), style = small, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                Row(modifier = Modifier.height(height).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    group.bars.forEach { bar ->
                        Canvas(modifier = Modifier.weight(1f).height(height).semantics { contentDescription = bar.description }) {
                            val barHeight = (bar.value.coerceAtLeast(0.0) / max * size.height).toFloat()
                            drawRoundRect(
                                color = bar.color,
                                topLeft = Offset(0f, size.height - barHeight),
                                size = Size(size.width, barHeight),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                }
                Text(group.label, style = small, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
