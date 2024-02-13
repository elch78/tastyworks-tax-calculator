package com.elchworks.tastyworkstaxcalculator.positions.option

import com.elchworks.tastyworkstaxcalculator.positions.PositionCloseResult
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import org.slf4j.LoggerFactory
import kotlin.math.min

class OptionShortPosition(val stoTx: OptionTrade) {
    private var quantityLeft = stoTx.quantity

    fun buyToClose(quantity: Int): PositionCloseResult {
        LOG.debug("buyToClose quantity='{}', quantityLeft='{}'", quantity, quantityLeft)
        val numBought = min(quantity, quantityLeft)
        quantityLeft -= numBought
        LOG.debug("buyToClose numBought='{}', quantityLeft='{}'", numBought, quantityLeft)
        val quantityLeftInTx = quantity - numBought
        val result = PositionCloseResult(numBought, quantityLeftInTx, quantityLeft)
        LOG.debug("buyToClose result='{}'", result)
        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(OptionShortPosition::class.java)
    }
}
