package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.Transaction

data class OptionSellToOpenEvent(
    val position: OptionPosition,
    val transaction: Transaction
)
