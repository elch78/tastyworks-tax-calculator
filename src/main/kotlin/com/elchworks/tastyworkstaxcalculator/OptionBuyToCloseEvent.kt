package com.elchworks.tastyworkstaxcalculator

data class OptionBuyToCloseEvent(
    val position: OptionPosition,
    val transaction: Transaction
)
