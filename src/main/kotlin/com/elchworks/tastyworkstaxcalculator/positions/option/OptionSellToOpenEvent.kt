package com.elchworks.tastyworkstaxcalculator.positions.option

import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade

data class OptionSellToOpenEvent(
    val stoTx: OptionTrade
)
