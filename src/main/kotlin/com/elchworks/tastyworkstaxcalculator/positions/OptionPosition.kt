package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.temporal.ChronoField.YEAR

class OptionPosition (
    val stoTx: Transaction,
    private val exchangeRate: ExchangeRate,
    private var btcTx: Transaction? = null,
) {
    private val log = LoggerFactory.getLogger(OptionPosition::class.java)

    fun netPremium() = Profit(value = stoTx.value, date = stoTx.date)

    /**
     * Status for fiscal year.
     */
    fun status(year: Int): String =
        if(isClosedInYear(year)) "Closed" else "Open"

    fun isClosedInYear(year: Int): Boolean {
        val closedYear = yearClosed()
        val closedInYear = closedYear == year
        log.debug("isClosedInYear closedYear='{}', closedInYear='{}'", closedYear, closedInYear)
        return closedInYear
    }

    fun yearOpened(): Int = stoTx.date.atZone(ZoneId.of("CET")).get(YEAR)
    fun yearClosed(): Int? = btcTx?.date?.atZone(ZoneId.of("CET"))?.get(YEAR)



    fun profitAndLoss(): ProfitAndLoss {
        val netProfit = netProfit()
        val result = if(isLoss(netProfit)) {
            ProfitAndLoss(0.0F, -netProfit)
        } else {
            ProfitAndLoss( netProfit, 0.0F)
        }
        log.debug("profitAndLoss result='{}'", result)
        return result
    }

    fun buyToClose(btcTx: Transaction) {
        if(btcTx.quantity != stoTx.quantity) {
            error("Currently only complete closing of positions is supported.")
        }
        this.btcTx = btcTx
    }

    private fun netProfit(): Float {
        val premium = exchangeRate.usdToEur(Profit(stoTx.value, stoTx.date))
        val buyValue = if(btcTx != null) {
            exchangeRate.usdToEur(Profit(btcTx!!.value, btcTx!!.date))
        } else 0.0F
        // buyValue is negative
        val netProfit = premium + buyValue
        log.debug("netProfit premium='{}', buyValue='{}', netProfit='{}'", premium, buyValue, netProfit)
        return netProfit
    }

    private fun isLoss(netProfit: Float): Boolean {
        val isLoss = netProfit < 0.0F
        log.debug("isLoss netProfit='{}', isLoss='{}'", netProfit, isLoss)
        return isLoss
    }

    companion object {
        fun fromTransction(transaction: Transaction, exchangeRate: ExchangeRate) = OptionPosition(
            stoTx = transaction,
            exchangeRate = exchangeRate,
        )
    }
}