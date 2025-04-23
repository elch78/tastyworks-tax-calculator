package com.elchworks.tastyworkstaxcalculator.portfolio

data class PositionCloseResult(
    val quantityClosed: Int,
    val quantityLeftInTx: Int,
    val quantityLeftInPosition: Int,
) {
    fun positionDepleted() = quantityLeftInPosition == 0
}
