package com.elchworks.tastyworkstaxcalculator.positions.stock

import com.elchworks.tastyworkstaxcalculator.positions.PositionCloseResult
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import org.slf4j.LoggerFactory
import kotlin.math.min

class StockPosition(
    val btoTx: StockTransaction
) {
    private var quantityLeft: Int = btoTx.quantity

    fun sellToClose(quantity: Int): PositionCloseResult {
        val numSold = min(quantity, quantityLeft)
        LOG.debug("sellToClose btoTx.quantity='{}', sold='{}', quantity='{}', numSold='{}'", btoTx.quantity,
            this.quantityLeft, quantity, numSold)
        quantityLeft -= numSold
        LOG.debug("sellToClose quantityLeft='{}'", quantityLeft)
        val quantityLeftInTx = quantity - numSold
        val positionCloseResult = PositionCloseResult(numSold, quantityLeftInTx, quantityLeft)
        LOG.debug("positionCloseResult='{}'", positionCloseResult)
        return positionCloseResult
    }

    override fun toString(): String {
        return "StockPosition(btoTx=$btoTx, sold=$quantityLeft)"
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StockPosition::class.java)
    }
}
