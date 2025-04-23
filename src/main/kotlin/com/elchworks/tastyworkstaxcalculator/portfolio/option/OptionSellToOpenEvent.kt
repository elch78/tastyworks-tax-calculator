package com.elchworks.tastyworkstaxcalculator.portfolio.option

import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade

data class OptionSellToOpenEvent(
    val stoTx: OptionTrade
)
