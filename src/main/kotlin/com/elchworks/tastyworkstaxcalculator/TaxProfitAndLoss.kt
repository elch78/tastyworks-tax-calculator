package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TaxProfitAndLoss (
    private val exchangeRate: ExchangeRate
){
    private val log = LoggerFactory.getLogger(TaxProfitAndLoss::class.java)
    var profit: Float = 0.0F
    var loss: Float = 0.0F

    @EventListener
    fun onOptionSellToOpen(event: OptionSellToOpenEvent) {
        val sellValue = eurValue(event.position)
        profit += sellValue
        log.debug("option STO sellValue='{}', profit='{}'", sellValue, profit)
    }

    @EventListener
    fun onOptionBuyToClose(event: OptionBuyToCloseEvent) {
        val sellValueEur = eurValue(event.position)
        val buyValueUsd = event.transaction.value
        val buyDate = event.transaction.date
        val buyValueEur = exchangeRate.usdToEur(Profit(buyValueUsd, buyDate))
        val netProfit = netProfit(sellValueEur, buyValueEur)
        subtractProfit(buyValueEur)
        if(isLoss(netProfit)) {
            addLoss(netProfit)
        }
        log.debug("option BTC netProfit='{}'", netProfit)
    }

    private fun eurValue(position: OptionPosition): Float {
        return exchangeRate.usdToEur(position.netPremium())
    }

    private fun subtractProfit(buyValue: Float) {
        // buyValue is negative
        profit += buyValue
        log.debug("subtractProfit buyValue='{}', profit='{}'", buyValue, profit)
    }

    private fun addLoss(netProfit: Float) {
        loss -= netProfit
        log.debug("netProfit='{}', loss='{}'", netProfit, loss)
    }

    private fun isLoss(netProfit: Float): Boolean {
        val isLoss = netProfit < 0.0F
        log.debug("netProfit='{}', isLoss='{}'", netProfit, isLoss)
        return isLoss
    }

    private fun netProfit(sellValue: Float, buyValue: Float): Float {
        // buyValue is negative
        val netProfit = sellValue + buyValue
        log.debug("netProfit sellValue='{}', buyValue='{}', netProfit='{}'", sellValue, buyValue, netProfit)
        return netProfit
    }


}
