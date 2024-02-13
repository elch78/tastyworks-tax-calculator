package com.elchworks.tastyworkstaxcalculator.positions.stock

import com.elchworks.tastyworkstaxcalculator.positions.PositionCloseResult
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import org.slf4j.LoggerFactory
import kotlin.math.min

class StockPosition(
    val btoTx: StockTransaction
) {
    private val log = LoggerFactory.getLogger(StockPosition::class.java)
    private var quantityLeft: Int = btoTx.quantity

    fun sellToClose(quantity: Int): PositionCloseResult {
        val currentSold = min(quantity, quantityLeft)
        log.debug("sellToClose btoTx.quantity='{}', sold='{}', quantity='{}', currentSold='{}'", btoTx.quantity,
            this.quantityLeft, quantity, currentSold)
        quantityLeft -= currentSold
        log.debug("sellToClose quantityLeft='{}'", quantityLeft)
        val quantityLeftInTx = quantity - currentSold
        val positionCloseResult = PositionCloseResult(currentSold, quantityLeftInTx, quantityLeft)
        log.debug("positionCloseResult='{}'", positionCloseResult)
        return positionCloseResult
    }

    override fun toString(): String {
        return "StockPosition(btoTx=$btoTx, sold=$quantityLeft)"
    }


}
