package com.elchworks.tastyworkstaxcalculator.positions.option

import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade

data class OptionBuyToCloseEvent(
    val stoTx: OptionTrade,
    val btcTx: OptionTrade,
    val quantitySold: Int,
)
