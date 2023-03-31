package com.elchworks.tastyworkstaxcalculator.fiscalyear

import javax.money.MonetaryAmount

data class ProfitsSummary(
    val profitsFromOptions: MonetaryAmount,
    val lossesFromOptions: MonetaryAmount,
    val profitsFromStocks: MonetaryAmount,
)
