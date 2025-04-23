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
import com.elchworks.tastyworkstaxcalculator.usd
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

    /*
     * For matching the BTO and STC transactions of a stock split.
     * Since split transactions happen at the same time there can only be one counterpart at a time.
     */
    private var splitTransactionCounterpart: StockTransaction? = null

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
        log.info("Stock STC symbol='{}' stcTx date {} quantity {} price {} description {}",
            stcTx.symbol, stcTx.date, stcTx.quantity, stcTx.averagePrice, stcTx.description)
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
        log.info("Stock BTO symbol='{}' stcTx date {} quantity {} price {} description {}",
            btoTx.symbol, btoTx.date, btoTx.quantity, btoTx.averagePrice, btoTx.description)
    }

    private fun stockTrade(stockTransaction: StockTransaction) {
        if(stockTransaction.type == "Reverse Split") {
            reverseSplit(stockTransaction)
        } else {
            when(stockTransaction.action) {
                BUY_TO_OPEN -> openStockPosition(stockTransaction)
                SELL_TO_CLOSE -> closeStockPosition(stockTransaction)
                else -> error("Unexpected action for StockTrade: ${stockTransaction.action}")
            }
        }
    }

    private fun reverseSplit(splitTransaction: StockTransaction) {
        if(splitTransactionCounterpart == null) {
            splitTransactionCounterpart = splitTransaction
            log.debug("First part of stock split.")
            return
        }

        splitTransactionCounterpart!!.validateMatchingSplitTransaction(splitTransaction)
        var btoTx: StockTransaction? = null
        var stcTx: StockTransaction? = null

        // The order of the two split transactions is not defined since they have the same timestamp
        if(splitTransaction.action == BUY_TO_OPEN) {
            btoTx = splitTransaction
            stcTx = splitTransactionCounterpart!!
        } else {
            btoTx = splitTransactionCounterpart!!
            stcTx = splitTransaction
        }

        log.debug("split btoTx='{}', stc='{}'", btoTx, stcTx)

        val totalBuyValue = stockPositions[splitTransaction.symbol]!!.totalBuyValue()

        val newQuantity = btoTx.quantity
        val averagePrice = totalBuyValue.divide(newQuantity)
        val newPostion = StockPosition(
            StockTrade(
                date = btoTx.date,
                symbol = splitTransaction.symbol,
                action = BUY_TO_OPEN,
                type = btoTx.type,
                value = totalBuyValue,
                quantity = newQuantity,
                averagePrice = averagePrice,
                description = btoTx.description,
                commissions = usd(0.0),
                fees = usd(0.0)
            )
        )
        log.debug("new postion='{}'", newPostion)
        stockPositions[splitTransaction.symbol] = LinkedList<StockPosition>().apply { offer(newPostion) }
        splitTransactionCounterpart = null
        log.info("Stock reverse split. oldQuantity='{}', newQuantity='{}', totalBuyValue='{}', averagePrice='{}'", stcTx.quantity, btoTx.quantity, totalBuyValue, averagePrice)
    }

    fun Queue<StockPosition>.totalBuyValue() =
        fold(usd(0.0)) { sum, position -> sum.add(position.buyValue()) }

    private fun StockTransaction.validateMatchingSplitTransaction(stockTrade: StockTransaction) {
        if(this.date != stockTrade.date) {
            throw RuntimeException("Dates of split transaction don't match")
        }
        if(this.symbol != stockTrade.symbol) {
            throw RuntimeException("Symbols of split transaction don't match")
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

    private fun OptionTransaction.key() = "${this.callOrPut}-${this.symbol}-${this.expirationDate}-${this.strikePrice}"
}
