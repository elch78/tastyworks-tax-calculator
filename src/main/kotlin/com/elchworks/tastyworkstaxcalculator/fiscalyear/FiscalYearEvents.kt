package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import java.time.Year
import javax.money.MonetaryAmount

/**
 * Published by FiscalYear when option profit/loss is updated.
 * Contains both the delta (change) and the new total state.
 */
data class OptionProfitLossUpdatedEvent(
    val year: Year,
    val profitLossDelta: ProfitAndLoss,
    val totalProfitAndLoss: ProfitAndLoss
)

/**
 * Published by FiscalYear when stock profit/loss is updated.
 * Contains both the delta (change) and the new total state.
 */
data class StockProfitLossUpdatedEvent(
    val year: Year,
    val profitLossDelta: MonetaryAmount,
    val totalProfitAndLoss: MonetaryAmount
)
