package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PositionsManager(
    private val eventPublisher: ApplicationEventPublisher,
    private val exchangeRate: ExchangeRate,
) {
    private val log = LoggerFactory.getLogger(PositionsManager::class.java)
    private val positions = mutableMapOf<String, OptionPosition>()
    private val closedPositions = mutableListOf<OptionPosition>()

    fun process(transactions: List<Transaction>) {
        transactions
            .sortedBy { it.date }
            .forEach {
            when (it.action) {
                "SELL_TO_OPEN" -> openPosition(it)
                "BUY_TO_CLOSE" -> closePosition(it)
            }
        }
        calculateProfitAndLoss()
    }

    private fun calculateProfitAndLoss() {
        var profit: Float = 0.0F
        var loss: Float = 0.0F
        positions.values.forEach {
            var profitAndLoss = it.profitAndLoss()
            profit += profitAndLoss.profit
            loss += profitAndLoss.loss
        }
        closedPositions.forEach{
            var profitAndLoss = it.profitAndLoss()
            profit += profitAndLoss.profit
            loss += profitAndLoss.loss
        }
        printReport(profit, loss)
        log.info("profit='{}', loss='{}'", profit, loss)
    }

    private fun printReport(profit: Float, loss: Float) {

        val report = positions.values.plus(closedPositions)
            .sortedBy { it.stoTx.date }
            .map {
                val stoTx = it.stoTx
                val profitAndLoss = it.profitAndLoss()
                "${stoTx.rootSymbol}\t\t${stoTx.date}\t${stoTx.value}\t${it.status(2021)}\t${profitAndLoss.profit}\t${profitAndLoss.loss}"
            }
            .joinToString ( "\n", prefix = "\nSymbol\tDate\tPremium\tStatus\tProfit\tLoss\n", postfix = "\ntotal\tprofit\t$profit\tloss\t$loss" )
        log.info("{}", report)
    }

    private fun closePosition(btcTx: Transaction) {
        val position = positions[btcTx.key()]!!
        positions.remove(btcTx.key())
        position.buyToClose(btcTx)
        closedPositions.add(position)
        log.debug("closed position='{}'", position)
        eventPublisher.publishEvent(OptionBuyToCloseEvent(position, btcTx))
    }

    private fun openPosition(
        tx: Transaction
    ) {
        val position = OptionPosition.fromTransction(tx, exchangeRate)
        positions[tx.key()] = position
        log.debug("opened position='{}'", position)
        eventPublisher.publishEvent(OptionSellToOpenEvent(position, tx))
    }
}
