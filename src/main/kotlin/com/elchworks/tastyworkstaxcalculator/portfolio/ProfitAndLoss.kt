package com.elchworks.tastyworkstaxcalculator.portfolio

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.plus
import javax.money.MonetaryAmount

data class ProfitAndLoss(
    val profit: MonetaryAmount,
    val loss: MonetaryAmount,
) {
    constructor(): this(eur(0f), eur(0f))
}

operator fun ProfitAndLoss.plus(other: ProfitAndLoss) = ProfitAndLoss(profit + other.profit, loss + other.loss)
