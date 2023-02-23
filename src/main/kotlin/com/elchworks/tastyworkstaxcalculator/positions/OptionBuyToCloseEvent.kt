package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Transaction

data class OptionBuyToCloseEvent(
    val position: OptionPosition,
    val transaction: Transaction
)
