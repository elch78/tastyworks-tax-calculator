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
        val numSold = min(quantity, quantityLeft)
        log.debug("sellToClose btoTx.quantity='{}', sold='{}', quantity='{}', numSold='{}'", btoTx.quantity,
            this.quantityLeft, quantity, numSold)
        quantityLeft -= numSold
        log.debug("sellToClose quantityLeft='{}'", quantityLeft)
        val quantityLeftInTx = quantity - numSold
        val positionCloseResult = PositionCloseResult(numSold, quantityLeftInTx, quantityLeft)
        log.debug("positionCloseResult='{}'", positionCloseResult)
        return positionCloseResult
    }

    override fun toString(): String {
        return "StockPosition(btoTx=$btoTx, sold=$quantityLeft)"
    }


}
