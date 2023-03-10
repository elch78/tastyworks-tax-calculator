package com.elchworks.tastyworkstaxcalculator.positions

data class ProfitAndLoss(
    val profit: Float,
    val loss: Float,
) {
    constructor(): this(0.0f, 0.0f)
}

operator fun ProfitAndLoss.plus(other: ProfitAndLoss) = ProfitAndLoss(profit + other.profit, loss + other.loss)
