package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory

class OptionPosition (
    val stoTx: Transaction,
    private val exchangeRate: ExchangeRate,
    private var btcTx: Transaction? = null,
) {
    private val log = LoggerFactory.getLogger(OptionPosition::class.java)

    fun netPremium() = Profit(value = stoTx.value, date = stoTx.date)

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
