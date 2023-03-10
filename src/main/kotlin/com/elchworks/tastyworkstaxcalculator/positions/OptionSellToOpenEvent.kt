package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Trade

data class OptionSellToOpenEvent(
    val stoTx: Trade
)
