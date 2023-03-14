package com.elchworks.tastyworkstaxcalculator.positions

data class PositionCloseResult(
    val quantityClosed: Int,
    val quantityLeftInTx: Int,
    val quantityLeftInPosition: Int,
)
