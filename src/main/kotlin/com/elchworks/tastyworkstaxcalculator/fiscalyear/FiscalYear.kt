package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.OptionPosition
import org.slf4j.LoggerFactory

class FiscalYear {
    private val positions = mutableListOf<OptionPosition>()
    private val log = LoggerFactory.getLogger(FiscalYear::class.java)

    fun addOptionPosition(position: OptionPosition) = positions.add(position)

    fun calculateProfitAndLoss() {
        var profit: Float = 0.0F
        var loss: Float = 0.0F
        positions.forEach {
            var profitAndLoss = it.profitAndLoss()
            profit += profitAndLoss.profit
            loss += profitAndLoss.loss
        }
        printReport(profit, loss)
        log.info("profit='{}', loss='{}'", profit, loss)
    }

    private fun printReport(profit: Float, loss: Float) {

        val report = positions
            .sortedBy { it.stoTx.date }
            .map {
                val stoTx = it.stoTx
                val profitAndLoss = it.profitAndLoss()
                "${stoTx.rootSymbol}\t\t${stoTx.date}\t${stoTx.value}\t${it.status(2021)}\t${profitAndLoss.profit}\t${profitAndLoss.loss}"
            }
            .joinToString ( "\n", prefix = "\nSymbol\tDate\tPremium\tStatus\tProfit\tLoss\n", postfix = "\ntotal\tprofit\t$profit\tloss\t$loss" )
        log.info("{}", report)
    }
}
