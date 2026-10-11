package com.ibkrtax.mobile.ui.skins.focus

import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.fx.UsdConverter
import com.ibkrtax.mobile.portfolio.PortfolioSummary
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.portfolio.UsdTotals
import com.ibkrtax.mobile.portfolio.YearDividends
import com.ibkrtax.mobile.ui.Formats
import com.ibkrtax.mobile.ui.common.skinMoney
import java.math.BigDecimal
import java.math.RoundingMode

/** How a part of a sentence is shown: plain, bold, or bold in the gain or loss colour. */
enum class Tone { PLAIN, STRONG, GAIN, LOSS }

data class Part(val text: String, val tone: Tone = Tone.PLAIN)

data class Sentence(val parts: List<Part>) {
    val text: String get() = parts.joinToString("") { it.text }
}

/** Which income period a sentence describes. */
sealed interface IncomePeriod {
    data class Year(val year: Int, val isCurrent: Boolean) : IncomePeriod

    data class AllYears(val firstYear: Int) : IncomePeriod
}

/**
 * The Focus sentences (mobile-skin-focus "Focus summaries"), built only from the app's own
 * numbers. English only, like the rest of the app.
 */
object Summaries {
    /** Value line under the portfolio total: today's move, its main drivers and the total return. */
    fun today(rows: List<PositionRow>, totals: UsdTotals, summary: PortfolioSummary, rates: FxRates?): Sentence {
        val parts = mutableListOf<Part>()
        val moves = rows.mapNotNull { row -> row.dailyPnl?.let { UsdConverter.toUsd(it, row.displayCurrency, rates) }?.let { row.ticker to it } }
        val day = totals.dailyPnl.setScale(0, RoundingMode.HALF_UP)
        when {
            moves.isEmpty() -> parts += Part("Today’s change is not known yet.")
            day.signum() == 0 -> parts += Part("Your portfolio is flat today.")
            else -> {
                val up = day.signum() > 0
                parts += Part("You’re ")
                parts += Part("${if (up) "up" else "down"} ${dollars(day.abs())} today", if (up) Tone.GAIN else Tone.LOSS)
                val drivers = moves.filter { it.second.signum() == day.signum() }.sortedByDescending { it.second.abs() }.take(2).map { it.first }
                if (drivers.isNotEmpty()) parts += Part(", mostly from ${drivers.joinToString(" and ")}")
                parts += Part(".")
            }
        }
        summary.totalProfitPercent?.let { pct ->
            val whole = pct.setScale(0, RoundingMode.HALF_UP)
            parts += Part(if (whole.signum() >= 0) " Since you started, your money grew by " else " Since you started, your money shrank by ")
            parts += Part("${Formats.number(whole.abs(), 0, 0)}%", if (whole.signum() >= 0) Tone.GAIN else Tone.LOSS)
            parts += Part(".")
        }
        if (!totals.isComplete || totals.missingDailyPnl > 0) parts += Part(" Some prices are missing, so these figures are incomplete.")
        return Sentence(parts)
    }

    /** The income headline: net dividends of the period and, for a year, the change against the year before. */
    fun income(dividends: YearDividends, change: BigDecimal?, period: IncomePeriod): Sentence {
        val parts = mutableListOf<Part>()
        val net = Part(cents(dividends.usdNet.amount), Tone.GAIN)
        when (period) {
            is IncomePeriod.AllYears -> {
                parts += Part("Since ${period.firstYear} your companies paid you ")
                parts += net
                parts += Part(" after ${cents(dividends.usdTax.amount.abs())} tax.")
            }
            is IncomePeriod.Year -> {
                parts += Part("${if (period.isCurrent) "This year" else "In ${period.year}"} your companies paid you ")
                parts += net
                parts += Part(" after tax")
                val whole = change?.setScale(0, RoundingMode.HALF_UP)
                val than = if (period.isCurrent) "the same months last year" else "the year before"
                when {
                    whole == null -> parts += Part(".")
                    whole.signum() == 0 -> parts += Part(", about the same as $than.")
                    else -> {
                        parts += Part(": ")
                        parts += Part("${Formats.number(whole.abs(), 0, 0)}% ${if (whole.signum() > 0) "more" else "less"}", Tone.STRONG)
                        parts += Part(" than $than.")
                    }
                }
            }
        }
        val missing = (dividends.usdNet.missingCurrencies + dividends.usdTax.missingCurrencies).sorted()
        if (missing.isNotEmpty()) parts += Part(" Amounts in ${missing.joinToString()} could not be converted and are left out.")
        return Sentence(parts)
    }

    /** A position's story: what was put in and what it is worth, sales and dividends, then the total (last). */
    fun story(p: PositionPerformance): List<Sentence> {
        val cur = p.currency
        val row = p.row
        val worth = when {
            row == null -> Sentence(listOf(Part("You put in "), Part(money(p.invested, cur), Tone.STRONG), Part(" and have sold all your shares.")))
            else -> {
                val start = listOf(Part("You put in "), Part(money(p.invested, cur), Tone.STRONG), Part(" for your ${Formats.quantity(row.holding.quantity)} shares."))
                val value = row.marketValue?.let { listOf(Part(" They are now worth "), Part(money(it, cur), Tone.STRONG), Part(".")) }
                    ?: listOf(Part(" Their current price is not available."))
                Sentence(start + value)
            }
        }

        val extra = mutableListOf<Part>()
        val sold = p.realizedGain.signum() != 0
        val paid = p.dividends.gross.signum() != 0 || p.dividends.tax.signum() != 0
        if (sold || paid) {
            extra += Part("On top of that, ")
            if (sold) {
                val gain = p.realizedGain.signum() > 0
                extra += Part(if (gain) "you earned " else "you lost ")
                extra += Part(money(p.realizedGain.abs(), cur), if (gain) Tone.GAIN else Tone.LOSS)
                extra += Part(if (gain) " from shares you sold" else " on shares you sold")
            }
            if (paid) {
                extra += Part(if (!sold) "you earned " else if (p.realizedGain.signum() > 0) ", and " else ", and earned ")
                extra += Part(money(p.dividends.net, cur), Tone.GAIN)
                extra += Part(" in dividends after ${money(p.dividends.tax.abs(), cur)} tax")
            }
            extra += Part(".")
        } else {
            extra += Part("There were no sales or dividends so far.")
        }
        if (!p.realizedComplete) extra += Part(" Some sales could not be matched, so this may be incomplete.")

        val total = p.total
        val all = if (total == null) {
            Sentence(listOf(Part("All in all: not known yet, because the price is missing.")))
        } else {
            val tone = if (total.signum() >= 0) Tone.GAIN else Tone.LOSS
            val pct = p.totalPercent?.setScale(0, RoundingMode.HALF_UP)?.let { " · " + (if (it.signum() > 0) "+" else if (it.signum() < 0) "−" else "") + Formats.number(it.abs(), 0, 0) + "%" }.orEmpty()
            Sentence(listOf(Part("All in all: "), Part(signedMoney(total, cur) + pct, tone)))
        }
        return listOf(worth, Sentence(extra), all)
    }

    /** Whole dollars, e.g. "$110". */
    private fun dollars(value: BigDecimal): String = "$" + Formats.number(value.setScale(0, RoundingMode.HALF_UP), 0, 0)

    private fun cents(value: BigDecimal): String = skinMoney(value, "USD")

    private fun money(value: BigDecimal, currency: String): String = skinMoney(value, currency)

    private fun signedMoney(value: BigDecimal, currency: String): String = skinMoney(value, currency, signed = true)
}
