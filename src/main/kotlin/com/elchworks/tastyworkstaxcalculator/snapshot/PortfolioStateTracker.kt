package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockBuyToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PortfolioStateTracker(
    private val snapshotSerializer: SnapshotSerializer
) {
    private val log = LoggerFactory.getLogger(PortfolioStateTracker::class.java)

    // Maps of position key -> list of position snapshots (FIFO order)
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()

    @EventListener(OptionSellToOpenEvent::class)
    fun onOptionOpened(event: OptionSellToOpenEvent) {
        val stoTx = event.stoTx
        val key = stoTx.key()

        val snapshot = OptionPositionSnapshot(
            stoTx = snapshotSerializer.serializeOptionTrade(stoTx),
            quantityLeft = stoTx.quantity
        )

        optionPositions.computeIfAbsent(key) { mutableListOf() }
            .add(snapshot)

        log.debug("Tracked option position opened: key={}, quantity={}", key, stoTx.quantity)
    }

    @EventListener(OptionBuyToCloseEvent::class)
    fun onOptionClosed(event: OptionBuyToCloseEvent) {
        val stoTx = event.stoTx
        val key = stoTx.key()
        val quantityClosed = event.quantitySold

        val positions = optionPositions[key]
            ?: throw IllegalStateException("No tracked position for option close: $key")

        // Update first position's quantity (FIFO)
        val position = positions.first()
        val newQuantity = position.quantityLeft - quantityClosed

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                optionPositions.remove(key)
                log.debug("Removed option position key: {}", key)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }

        log.debug("Tracked option position closed: key={}, quantityClosed={}, remaining={}",
            key, quantityClosed, newQuantity)
    }

    @EventListener(StockBuyToOpenEvent::class)
    fun onStockOpened(event: StockBuyToOpenEvent) {
        val btoTx = event.btoTx
        val symbol = btoTx.symbol

        val snapshot = StockPositionSnapshot(
            btoTx = snapshotSerializer.serializeStockTransaction(btoTx),
            quantityLeft = btoTx.quantity
        )

        stockPositions.computeIfAbsent(symbol) { mutableListOf() }
            .add(snapshot)

        log.debug("Tracked stock position opened: symbol={}, quantity={}", symbol, btoTx.quantity)
    }

    @EventListener(StockSellToCloseEvent::class)
    fun onStockClosed(event: StockSellToCloseEvent) {
        val btoTx = event.btoTx
        val symbol = btoTx.symbol
        val quantityClosed = event.quantitySold

        val positions = stockPositions[symbol]
            ?: throw IllegalStateException("No tracked position for stock close: $symbol")

        // Update first position's quantity (FIFO)
        val position = positions.first()
        val newQuantity = position.quantityLeft - quantityClosed

        if (newQuantity == 0) {
            positions.removeAt(0)
            if (positions.isEmpty()) {
                stockPositions.remove(symbol)
                log.debug("Removed stock position symbol: {}", symbol)
            }
        } else {
            positions[0] = position.copy(quantityLeft = newQuantity)
        }

        log.debug("Tracked stock position closed: symbol={}, quantityClosed={}, remaining={}",
            symbol, quantityClosed, newQuantity)
    }

    fun getPortfolioSnapshot(): PortfolioSnapshot {
        log.debug("Creating portfolio snapshot from tracker: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
        return PortfolioSnapshot(
            optionPositions = optionPositions.mapValues { it.value.toList() },
            stockPositions = stockPositions.mapValues { it.value.toList() }
        )
    }

    fun reset() {
        log.debug("Resetting portfolio state tracker")
        optionPositions.clear()
        stockPositions.clear()
    }

    fun restoreFrom(snapshot: PortfolioSnapshot) {
        reset()

        snapshot.optionPositions.forEach { (key, list) ->
            optionPositions[key] = list.toMutableList()
        }
        snapshot.stockPositions.forEach { (symbol, list) ->
            stockPositions[symbol] = list.toMutableList()
        }

        log.debug("Restored portfolio state tracker: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
    }

    @EventListener(PortfolioStateRestoredEvent::class)
    fun onPortfolioRestored(event: PortfolioStateRestoredEvent) {
        log.info("Portfolio state restored event received, restoring tracker state")
        restoreFrom(event.portfolioSnapshot)
    }

    // Duplicate of Portfolio.optionKey() to avoid module dependency
    private fun OptionTrade.key() = "${this.callOrPut}-${this.symbol}-${this.expirationDate}-${this.strikePrice}"
}
