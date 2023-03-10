package com.elchworks.tastyworkstaxcalculator.positions

data class ProfitAndLoss(
    val profit: Float,
    val loss: Float,
) {
    constructor(): this(0.0f, 0.0f)

    fun addProfit(value: Float) = ProfitAndLoss(profit + value, loss)
    fun addLoss(value: Float) = ProfitAndLoss(profit, loss + value)
}

operator fun ProfitAndLoss.plus(other: ProfitAndLoss) = ProfitAndLoss(profit + other.profit, loss + other.loss)
