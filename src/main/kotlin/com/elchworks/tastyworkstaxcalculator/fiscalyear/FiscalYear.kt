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
                val description = "${stoTx.rootSymbol.padEnd(4)} ${stoTx.expirationDate} ${stoTx.callOrPut}@${stoTx.strikePrice}".padEnd(25)
                "$description ${it.status.name.padEnd(8)} ${profitAndLoss.profit.toString().padEnd(12)} ${profitAndLoss.loss}"
            }
            .joinToString ( "\n", prefix = "\nPosition Status Profit Loss\n", postfix = "\ntotal profit: ${profit.profit} loss: ${profit.loss}" )
        log.info("{}", report)
    }
}
