package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Trade

data class OptionBuyToCloseEvent(
    val stoTx: Trade,
    val btcTx: Trade,
)
