package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import org.slf4j.LoggerFactory
import kotlin.math.min

class StockPosition(
    private val btoTx: StockTrade
) {
    private val log = LoggerFactory.getLogger(StockPosition::class.java)
    private var sold: Int = 0

    /**
     * @return quantityLeft maybe <0 if more stocks are sold than are left in this position
     */
    fun sellToClose(stcTx: StockTrade): Int {
        val quantityLeft = btoTx.quantity - sold - stcTx.quantity
        log.debug("sellToClose btoTx.quantity='{}', sold='{}', stcTx.quantity='{}', quantityLeft='{}'", btoTx.quantity, sold, stcTx.quantity, quantityLeft)
        sold = min(btoTx.quantity, sold + stcTx.quantity)
        log.debug("sellToClose new sold='{}'", sold)
        return quantityLeft
    }
}
