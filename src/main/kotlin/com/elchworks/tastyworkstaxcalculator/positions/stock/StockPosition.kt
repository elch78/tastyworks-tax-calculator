package com.elchworks.tastyworkstaxcalculator.positions.stock

import com.elchworks.tastyworkstaxcalculator.positions.PositionCloseResult
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import org.slf4j.LoggerFactory
import kotlin.math.min

class StockPosition(
    val btoTx: StockTransaction
) {
    private val log = LoggerFactory.getLogger(StockPosition::class.java)
    private var sold: Int = 0

    fun sellToClose(quantity: Int): PositionCloseResult {
        val quantityLeft = btoTx.quantity - sold
        val currentSold = min(quantity, quantityLeft)
        log.debug("sellToClose btoTx.quantity='{}', sold='{}', quantity='{}', currentSold='{}'", btoTx.quantity, sold, quantity, currentSold)
        sold += currentSold
        log.debug("sellToClose new sold='{}'", sold)
        val quantityLeftInTx = quantity - currentSold
        val quantityLeftInPosition = btoTx.quantity - sold
        val positionCloseResult = PositionCloseResult(currentSold, quantityLeftInTx, quantityLeftInPosition)
        log.debug("positionCloseResult='{}'", positionCloseResult)
        return positionCloseResult
    }

    override fun toString(): String {
        return "StockPosition(btoTx=$btoTx, sold=$sold)"
    }


}
