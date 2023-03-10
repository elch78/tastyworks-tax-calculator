package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade

data class OptionSellToOpenEvent(
    val stoTx: OptionTrade
)
