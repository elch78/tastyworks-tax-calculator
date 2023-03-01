package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Trade

data class OptionBuyToCloseEvent(
    val position: OptionPosition,
    val trade: Trade
)
