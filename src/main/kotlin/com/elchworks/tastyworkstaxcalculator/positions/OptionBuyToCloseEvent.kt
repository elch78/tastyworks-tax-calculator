package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade

data class OptionBuyToCloseEvent(
    val stoTx: OptionTrade,
    val btcTx: OptionTrade,
)
