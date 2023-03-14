package com.elchworks.tastyworkstaxcalculator.positions

import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.positions.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTransaction
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
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
        val tx = event.tx
        // TODO: simplify just switch on the type
        when {
            isOpenOptionPosition(tx) -> openPosition(tx as OptionTrade)
            isCloseOptionPosition(tx) -> closePosition(tx as OptionTrade)
            isOptionRemoval(tx) -> optionRemoval(tx as OptionRemoval)
            isSellStockTrade(tx) -> closeStockPosition(tx as StockTrade)
            tx is OptionAssignment -> assignment(tx)
            else -> error("Unhandled Transaction: $tx")
        }
    }

    private fun assignment(tx: OptionAssignment) {
        when(tx.action) {
            BUY_TO_OPEN -> optionAssignmentBuyToOpen(tx)
            SELL_TO_CLOSE -> optionAssignmentSellToClose(tx)
            else -> error("unexpected action for assignment: ${tx.action}")
        }
    }

    private fun optionAssignmentSellToClose(stcTx: OptionAssignment) {
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

    private fun optionAssignmentBuyToOpen(btoTx: OptionAssignment) {
        stockPositions.computeIfAbsent(btoTx.symbol) {LinkedList()}
            .offer(StockPosition(btoTx))
        log.info("Assignment tx='{}'", btoTx)
    }

    private fun isSellStockTrade(tx: Transaction): Boolean {
        val isSellStockTrade = tx is StockTrade && tx.action == SELL_TO_CLOSE
        log.debug("isSellStockTrade='{}'", isSellStockTrade)
        return isSellStockTrade
    }

    private fun closeStockPosition(stockTrade: StockTrade) {
        stockPositions[stockTrade.symbol]

    }

    private fun isOptionRemoval(tx: Transaction): Boolean {
        val isOptionRemoval = tx is OptionRemoval
        log.debug("isOptionRemoval='{}'", isOptionRemoval)
        return isOptionRemoval
    }

    private fun isCloseOptionPosition(tx: Transaction): Boolean {
        val isCloseOptionPosition = tx is OptionTrade && tx.action == BUY_TO_CLOSE
        log.debug("isCloseOptionPosition='{}'", isCloseOptionPosition)
        return isCloseOptionPosition
    }

    private fun isOpenOptionPosition(tx: Transaction): Boolean {
        val isOpenOptionPosition = tx is OptionTrade && tx.action == SELL_TO_OPEN
        log.debug("isOpenOptionPosition='{}'", isOpenOptionPosition)
        return isOpenOptionPosition
    }

    private fun optionRemoval(tx: OptionRemoval) {
        val stoTx = removePositionFifo(tx)
        when(tx.status) {
            ASSIGNED -> log.info("position assigned. position='{}'", stoTx.optionDescription())
            EXPIRED -> log.info("position expired. position='{}'", stoTx.optionDescription())
            else -> error("unexpected status ${tx.status}")
        }
    }

    private fun closePosition(btcTx: OptionTrade) {
        val stoTx = removePositionFifo(btcTx)
        log.debug("closed stoTx='{}'", stoTx)
        eventPublisher.publishEvent(OptionBuyToCloseEvent(stoTx, btcTx))
    }

    private fun openPosition(tx: OptionTrade) {
        optionPositions.computeIfAbsent(tx.key()) { LinkedList() }
            .offer(tx)
        log.debug("opened stoTx='{}'", tx)
        eventPublisher.publishEvent(OptionSellToOpenEvent(tx))
    }

    private fun removePositionFifo(tx: OptionTransaction) =
        optionPositions[tx.key()]!!.remove()

    private fun OptionTransaction.key() = "${this.callOrPut}-${this.rootSymbol}-${this.expirationDate}-${this.strikePrice}"
}
