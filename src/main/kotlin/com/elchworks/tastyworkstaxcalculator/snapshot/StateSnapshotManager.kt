package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.OptionProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.fiscalyear.StockProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockBuyToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant
import javax.money.MonetaryAmount

/**
 * Unified manager for state tracking and snapshot creation.
 * Listens to domain events and maintains snapshot-ready state for both
 * portfolio and fiscal years.
 */
@Component
class StateSnapshotManager {
    private val log = LoggerFactory.getLogger(StateSnapshotManager::class.java)

    // Portfolio state: maps of position key -> list of position snapshots (FIFO order)
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()

    // Fiscal year state: map of year -> fiscal year state
    private val fiscalYears = mutableMapOf<Int, FiscalYearState>()

    // Portfolio event listeners
    @EventListener(OptionSellToOpenEvent::class)
    fun onOptionOpened(event: OptionSellToOpenEvent) {
        val stoTx = event.stoTx
        val key = stoTx.key()

        val snapshot = OptionPositionSnapshot.from(
            com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionShortPosition(stoTx, stoTx.quantity)
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

        val snapshot = StockPositionSnapshot.from(
            com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockPosition(btoTx, btoTx.quantity)
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

    // Fiscal year event listeners
    @EventListener(OptionProfitLossUpdatedEvent::class)
    fun onOptionProfitLossUpdated(event: OptionProfitLossUpdatedEvent) {
        val year = event.year.value
        val state = getOrCreateFiscalYearState(year)
        state.profitAndLossFromOptions = event.totalProfitAndLoss

        log.debug("Tracked option P/L for year {}: delta={}, total={}",
            year, event.profitLossDelta, event.totalProfitAndLoss)
    }

    @EventListener(StockProfitLossUpdatedEvent::class)
    fun onStockProfitLossUpdated(event: StockProfitLossUpdatedEvent) {
        val year = event.year.value
        val state = getOrCreateFiscalYearState(year)
        state.profitAndLossFromStocks = event.totalProfitAndLoss

        log.debug("Tracked stock P/L for year {}: delta={}, total={}",
            year, event.profitLossDelta, event.totalProfitAndLoss)
    }

    // Restoration event listeners
    @EventListener(PortfolioStateRestoredEvent::class)
    fun onPortfolioRestored(event: PortfolioStateRestoredEvent) {
        log.info("Portfolio state restored event received, restoring tracker state")
        restorePortfolioState(event.portfolioSnapshot)
    }

    @EventListener(FiscalYearStateRestoredEvent::class)
    fun onFiscalYearRestored(event: FiscalYearStateRestoredEvent) {
        val snapshot = event.fiscalYearSnapshot
        log.debug("Fiscal year state restored event received for year {}", snapshot.year)

        val state = FiscalYearState(
            profitAndLossFromOptions = ProfitAndLoss(
                profit = Money.of(
                    snapshot.profitAndLossFromOptions.profit.amount,
                    snapshot.profitAndLossFromOptions.profit.currency
                ),
                loss = Money.of(
                    snapshot.profitAndLossFromOptions.loss.amount,
                    snapshot.profitAndLossFromOptions.loss.currency
                )
            ),
            profitAndLossFromStocks = Money.of(
                snapshot.profitAndLossFromStocks.amount,
                snapshot.profitAndLossFromStocks.currency
            )
        )
        fiscalYears[snapshot.year] = state
    }

    // Snapshot creation
    fun createSnapshot(lastTransactionDate: Instant): StateSnapshot {
        log.info("Creating snapshot with lastTransactionDate={}", lastTransactionDate)

        return StateSnapshot(
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                lastTransactionDate = lastTransactionDate
            ),
            portfolio = PortfolioSnapshot(
                optionPositions = optionPositions.mapValues { it.value.toList() },
                stockPositions = stockPositions.mapValues { it.value.toList() }
            ),
            fiscalYears = fiscalYears.mapValues { (year, state) ->
                FiscalYearSnapshot(
                    year = year,
                    profitAndLossFromOptions = ProfitAndLossSnapshot(
                        profit = MonetaryAmountSnapshot.from(state.profitAndLossFromOptions.profit),
                        loss = MonetaryAmountSnapshot.from(state.profitAndLossFromOptions.loss)
                    ),
                    profitAndLossFromStocks = MonetaryAmountSnapshot.from(state.profitAndLossFromStocks)
                )
            }
        )
    }

    // State management
    fun reset() {
        log.debug("Resetting state snapshot manager")
        optionPositions.clear()
        stockPositions.clear()
        fiscalYears.clear()
    }

    private fun restorePortfolioState(snapshot: PortfolioSnapshot) {
        optionPositions.clear()
        stockPositions.clear()

        snapshot.optionPositions.forEach { (key, list) ->
            optionPositions[key] = list.toMutableList()
        }
        snapshot.stockPositions.forEach { (symbol, list) ->
            stockPositions[symbol] = list.toMutableList()
        }

        log.debug("Restored portfolio state: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
    }

    private fun getOrCreateFiscalYearState(year: Int): FiscalYearState {
        return fiscalYears.computeIfAbsent(year) {
            FiscalYearState(
                profitAndLossFromOptions = ProfitAndLoss(eur(0), eur(0)),
                profitAndLossFromStocks = eur(0)
            )
        }
    }

    // Duplicate of Portfolio.optionKey() to avoid module dependency
    private fun OptionTrade.key() = "${this.callOrPut}-${this.symbol}-${this.expirationDate}-${this.strikePrice}"

    private data class FiscalYearState(
        var profitAndLossFromOptions: ProfitAndLoss,
        var profitAndLossFromStocks: MonetaryAmount
    )
}
