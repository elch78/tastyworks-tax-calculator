package com.elchworks.tastyworkstaxcalculator.positions

data class ProfitAndLoss(
    val profit: Float,
    val loss: Float,
)

operator fun ProfitAndLoss.plus(other: ProfitAndLoss) = ProfitAndLoss(profit + other.profit, loss + other.loss)
