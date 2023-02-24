package com.elchworks.tastyworkstaxcalculator.fiscalyear

import com.elchworks.tastyworkstaxcalculator.positions.OptionPosition
import com.elchworks.tastyworkstaxcalculator.positions.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.positions.plus
import org.slf4j.LoggerFactory

class FiscalYear {
    private val positions = mutableListOf<OptionPosition>()
    private val log = LoggerFactory.getLogger(FiscalYear::class.java)

    fun addOptionPosition(position: OptionPosition) {
        positions.add(position)
        log.debug("position added, position='{}'", position)
    }

    fun calculateProfitAndLoss() {
        val profitAndLoss = positions
            .map { it.profitAndLoss() }
            .reduce { acc, next -> acc + next }
        printReport(profitAndLoss)
        log.info("profit='{}'", profitAndLoss)
    }

    private fun printReport(profit: ProfitAndLoss) {

        val report = positions
            .sortedBy { it.stoTx.date }
            .map {
                val stoTx = it.stoTx
                val profitAndLoss = it.profitAndLoss()
                "${stoTx.rootSymbol}\t\t${stoTx.date}\t${stoTx.value}\t${it.status(2021)}\t${profitAndLoss.profit}\t${profitAndLoss.loss}"
            }
            .joinToString ( "\n", prefix = "\nSymbol\tDate\tPremium\tStatus\tProfit\tLoss\n", postfix = "\ntotal\tprofit\t${profit.profit}\tloss\t${profit.loss}" )
        log.info("{}", report)
    }
}
