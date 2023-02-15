package com.elchworks.tastyworkstaxcalculator

data class OptionSellToOpenEvent(
    val position: OptionPosition,
    val transaction: Transaction
)
