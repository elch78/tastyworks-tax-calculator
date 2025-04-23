package com.elchworks.tastyworkstaxcalculator.portfolio

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.EXPIRED
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
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
class Portfolio(
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(Portfolio::class.java)
    private val optionPositions = mutableMapOf<String, Queue<OptionShortPosition>>()
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
        log.info("Stock STC symbol='{}' stcTx date {} quantity {} price {}",
            stcTx.symbol, stcTx.date, stcTx.quantity, stcTx.averagePrice)
        var quantityToClose = stcTx.quantity
        do {
            val position = stockPositions[stcTx.symbol]!!.peek()
            log.debug("optionAssignmentSellToClose position='{}'", position)
            val positionCloseResult = position.sellToClose(quantityToClose)
            if (positionCloseResult.positionDepleted()) stockPositions[stcTx.symbol]!!.remove()
            log.info("Closing position='{}', result='{}'", position.description(), positionCloseResult)
            eventPublisher.publishEvent(StockSellToCloseEvent(position.btoTx, stcTx, positionCloseResult.quantityClosed))
            quantityToClose -= positionCloseResult.quantityClosed
            log.debug("optionAssignmentSellToClose quantityToClose='{}'", quantityToClose)
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
        val position = removePositionFifo(tx)
        when(tx.status) {
            ASSIGNED -> log.info("position assigned. position='{}'", position.description())
            EXPIRED -> log.info("position expired. position='{}'", position.description())
            else -> error("unexpected status ${tx.status}")
        }
    }

    private fun optionPositionBuyToClose(btcTx: OptionTrade) {
        log.info("Option BTC option='{}' btcTx date {} quantity {} price {}", btcTx.optionDescription(),
            btcTx.date, btcTx.quantity, btcTx.averagePrice)
        var quantityToClose = btcTx.quantity
        do {
            val position = optionPositions[btcTx.key()]!!.peek()
                ?: throw RuntimeException("No position for ${btcTx.key()}")
            val buyToCloseResult = position.buyToClose(quantityToClose)
            if (buyToCloseResult.positionDepleted()) optionPositions[btcTx.key()]!!.remove()
            log.info("Closing position='{}', result='{}'", position.description(), buyToCloseResult)
            eventPublisher.publishEvent(OptionBuyToCloseEvent(position.stoTx, btcTx, buyToCloseResult.quantityClosed))
            quantityToClose -= buyToCloseResult.quantityClosed
            log.debug("optionPositionBuyToClose quantityToClose='{}'", quantityToClose)
        } while (quantityToClose != 0)

    }

    private fun optionPositionSellToOpen(tx: OptionTrade) {
        optionPositions.computeIfAbsent(tx.key()) { LinkedList() }
            .offer(OptionShortPosition(tx))
        log.debug("opened stoTx='{}'", tx)
        eventPublisher.publishEvent(OptionSellToOpenEvent(tx))
    }

    private fun removePositionFifo(tx: OptionTransaction) =
        optionPositions[tx.key()]!!.remove()

    private fun OptionTransaction.key() = "${this.callOrPut}-${this.rootSymbol}-${this.expirationDate}-${this.strikePrice}"
}
