package com.elchworks.tastyworkstaxcalculator.portfolio

import java.time.Instant
import javax.money.MonetaryAmount

data class Profit(
    val value: MonetaryAmount,
    val date: Instant,
)
