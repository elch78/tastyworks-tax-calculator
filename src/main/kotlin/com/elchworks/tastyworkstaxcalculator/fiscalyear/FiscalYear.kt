package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.ExchangeRate
import com.elchworks.tastyworkstaxcalculator.positions.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.plus
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.optionDescription
import com.elchworks.tastyworkstaxcalculator.transactions.year
import org.slf4j.LoggerFactory

class FiscalYear(
    private val exchangeRate: ExchangeRate,
    private val fiscalYear: Int,
) {
    var profitAndLoss = ProfitAndLoss()
    private val log = LoggerFactory.getLogger(FiscalYear::class.java)


    fun printReport() {
        log.info("Profit and loss for fiscal year $fiscalYear: profit = ${profitAndLoss.profit}€ loss = ${profitAndLoss.loss}€")
    }

    fun onPositionOpened(stoEvent: OptionSellToOpenEvent) {
        val stoTx = stoEvent.stoTx
        val premium = txValueInEur(stoTx)
        profitAndLoss += ProfitAndLoss(premium, 0.0f)
        log.info("position opened. position='{}', premium='{}', profit='{}'€, loss='{}'€, fiscalYear='{}'",
            stoTx.optionDescription(), premium, profitAndLoss.profit, profitAndLoss.loss, fiscalYear)
    }

    fun onPositionClosed(btcEvent: OptionBuyToCloseEvent) {
        val btcTx = btcEvent.btcTx
        val stoTx = btcEvent.stoTx
        if(positionWasOpenedInThisFiscalYear(stoTx)) {
            val premium = txValueInEur(stoTx)
            val buyValue = txValueInEur(btcTx)
            val netProfit = netProfit(premium, buyValue)
            if(isLoss(netProfit)) {
                // is loss. Reduce profit by the whole premium, increase loss by netProfit
                profitAndLoss += ProfitAndLoss(-premium, -netProfit)
                log.info("Close with net loss. position='{}', netProfit='{}', profit='{}'€, loss='{}'€",
                    stoTx.optionDescription(), netProfit, profitAndLoss.profit, profitAndLoss.loss)
            } else {
                // no loss. Only reduce profit by buyValue.
                profitAndLoss += ProfitAndLoss(buyValue, 0.0f)
                log.info("Close with net profit. position='{}', netProfit='{}', profit='{}'€, loss='{}'€",
                    stoTx.optionDescription(), netProfit, profitAndLoss.profit, profitAndLoss.loss)
            }
        } else {
            // the position was not opened in the same year. The whole value of the transaction is a loss for the current year
            val netProfit = txValueInEur(stoTx)
            profitAndLoss += ProfitAndLoss(0.0f, -netProfit)
            log.info("Option position closed that was opened in a different fiscal year. netProfit='{}', profitAndLoss='{}'", netProfit, profitAndLoss)
        }

    }

    private fun isLoss(netProfit: Float): Boolean {
        val isLoss = netProfit < 0.0F
        log.debug("isLoss netProfit='{}', isLoss='{}'", netProfit, isLoss)
        return isLoss
    }

    private fun positionWasOpenedInThisFiscalYear(stoTx: OptionTrade) = stoTx.year() == fiscalYear

    private fun netProfit(premium: Float, buyValue: Float): Float {
        // buyValue is negative
        val netProfit = premium + buyValue
        log.debug("netProfit premium='{}', buyValue='{}', netProfit='{}'", premium, buyValue, netProfit)
        return netProfit
    }

    private fun txValueInEur(btcTx: OptionTrade) = exchangeRate.usdToEur(Profit(btcTx.value, btcTx.date))
}
