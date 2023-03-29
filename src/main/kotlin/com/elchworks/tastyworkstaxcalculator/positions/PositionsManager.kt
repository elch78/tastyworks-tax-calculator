package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.positions.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.positions.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.positions.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.positions.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTransaction
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTransaction
import com.elchworks.tastyworkstaxcalculator.transactions.optionDescription
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.*

@Component
class PositionsManager(
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(PositionsManager::class.java)
    private val optionPositions = mutableMapOf<String, Queue<OptionTrade>>()
    private val stockPositions = mutableMapOf<String, Queue<StockPosition>>()

    @EventListener(NewTransactionEvent::class)
    fun onNewTransaction(event: NewTransactionEvent) {
        log.debug("onNewTransaction event='{}'", event)
        when(val tx = event.tx) {
            is OptionTrade -> optionTrade(tx)
            is OptionRemoval -> optionRemoval(tx)
            is StockTrade -> stockTrade(tx)
            is OptionAssignment -> stockTrade(tx)
            else -> error("Unhandled Transaction: $tx")
        }
    }

    private fun optionTrade(tx: OptionTrade) {
        when(tx.action) {
            SELL_TO_OPEN -> optionPositionSellToOpen(tx)
            BUY_TO_CLOSE -> optionPositionBuyToClose(tx)
            else -> error("Unexpected action for OptionTrade: ${tx.action}")
        }
    }

    private fun closeStockPosition(stcTx: StockTransaction) {
        var quantityToClose = stcTx.quantity
        do {
            val position = stockPositions[stcTx.symbol]!!.peek()
            log.debug("optionAssignmentSellToClose position='{}'", position)
            val positionCloseResult = position.sellToClose(quantityToClose)
            log.debug("optionAssignmentSellToClose positionCloseResult='{}'", positionCloseResult)
            val positionDepleted = positionCloseResult.quantityLeftInPosition == 0
            if (positionDepleted) stockPositions[stcTx.symbol]!!.remove()
            quantityToClose -= positionCloseResult.quantityClosed
            log.debug("optionAssignmentSellToClose quantityToClose='{}'", quantityToClose)
            eventPublisher.publishEvent(StockSellToCloseEvent(position.btoTx, stcTx, positionCloseResult.quantityClosed))
        } while (quantityToClose != 0)
    }

    private fun openStockPosition(btoTx: StockTransaction) {
        stockPositions.computeIfAbsent(btoTx.symbol) {LinkedList()}
            .offer(StockPosition(btoTx))
        log.info("Assignment tx='{}'", btoTx)
    }

    private fun stockTrade(stockTrade: StockTransaction) {
        when(stockTrade.action) {
            BUY_TO_OPEN -> openStockPosition(stockTrade)
            SELL_TO_CLOSE -> closeStockPosition(stockTrade)
            else -> error("Unexpected action for StockTrade: ${stockTrade.action}")
        }
    }

    private fun optionRemoval(tx: OptionRemoval) {
        val stoTx = removePositionFifo(tx)
        when(tx.status) {
            ASSIGNED -> log.info("position assigned. position='{}'", stoTx.optionDescription())
            EXPIRED -> log.info("position expired. position='{}'", stoTx.optionDescription())
            else -> error("unexpected status ${tx.status}")
        }
    }

    private fun optionPositionBuyToClose(btcTx: OptionTrade) {
        val stoTx = removePositionFifo(btcTx)
        log.debug("closed stoTx='{}'", stoTx)
        eventPublisher.publishEvent(OptionBuyToCloseEvent(stoTx, btcTx))
    }

    private fun optionPositionSellToOpen(tx: OptionTrade) {
        optionPositions.computeIfAbsent(tx.key()) { LinkedList() }
            .offer(tx)
        log.debug("opened stoTx='{}'", tx)
        eventPublisher.publishEvent(OptionSellToOpenEvent(tx))
    }

    private fun removePositionFifo(tx: OptionTransaction) =
        optionPositions[tx.key()]!!.remove()

    private fun OptionTransaction.key() = "${this.callOrPut}-${this.rootSymbol}-${this.expirationDate}-${this.strikePrice}"
}
