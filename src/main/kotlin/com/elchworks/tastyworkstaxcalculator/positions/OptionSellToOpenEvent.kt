package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Trade

data class OptionSellToOpenEvent(
    val position: OptionPosition,
    val trade: Trade
)
