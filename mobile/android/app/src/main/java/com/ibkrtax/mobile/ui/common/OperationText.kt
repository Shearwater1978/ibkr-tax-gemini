package com.ibkrtax.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import com.ibkrtax.mobile.ui.Formats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** Text for an operation's amount and details; pure so it can be unit-tested. */
object OperationDisplay {
    private val TRADES = setOf("BUY", "SELL", "TRANSFER")
    private val CASH = setOf("DIVIDEND", "TAX")

    /** Trades show the absolute value (the type says the direction); cash keeps its sign; corporate actions show shares. */
    fun amount(operation: Operation): String = when (operation.eventType) {
        in TRADES -> Formats.money(operation.amount.abs(), operation.currency)
        in CASH -> Formats.signedMoney(operation.amount, operation.currency)
        else -> signedQuantity(operation)
    }

    fun details(operation: Operation): String? = when (operation.eventType) {
        in TRADES -> "${Formats.quantity(operation.quantity.abs())} × ${Formats.number(operation.price, 2, 6)} ${operation.currency}"
        else -> null
    }

    private fun signedQuantity(operation: Operation): String =
        (if (operation.quantity.signum() > 0) "+" else "") + Formats.quantity(operation.quantity)
}

fun displayDate(date: String): String =
    try {
        LocalDate.parse(date.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: DateTimeParseException) {
        date
    }

@Composable
fun typeLabel(eventType: String): String = when (eventType) {
    "BUY" -> stringResource(R.string.operation_buy)
    "SELL" -> stringResource(R.string.operation_sell)
    "DIVIDEND" -> stringResource(R.string.operation_dividend)
    "TAX" -> stringResource(R.string.operation_tax)
    "TRANSFER" -> stringResource(R.string.operation_transfer)
    "SPLIT" -> stringResource(R.string.operation_split)
    "STOCK_DIV" -> stringResource(R.string.operation_stock_dividend)
    "MERGER" -> stringResource(R.string.operation_merger)
    "SPINOFF" -> stringResource(R.string.operation_spinoff)
    else -> eventType
}

fun OperationFilter.labelRes(): Int = when (this) {
    OperationFilter.BUYS -> R.string.operations_filter_buys
    OperationFilter.SELLS -> R.string.operations_filter_sells
    OperationFilter.DIVIDENDS -> R.string.operations_filter_dividends
    OperationFilter.TAXES -> R.string.operations_filter_taxes
    OperationFilter.OTHER -> R.string.operations_filter_other
}
