package com.elchworks.tastyworkstaxcalculator.snapshot

import com.elchworks.tastyworkstaxcalculator.eur
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.fiscalyear.OptionProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.fiscalyear.StockProfitLossUpdatedEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.portfolio.ProfitAndLoss
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionBuyToCloseEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionSellToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockBuyToOpenEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.stock.StockSellToCloseEvent
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.fasterxml.jackson.databind.ObjectMapper
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.money.MonetaryAmount

/**
 * Unified service for state tracking, snapshot persistence, and restoration.
 * Listens to domain events, maintains snapshot-ready state, and handles
 * saving/loading/restoring snapshots.
 */
@Component
class SnapshotService(
    private val objectMapper: ObjectMapper,
    private val portfolio: Portfolio,
    private val fiscalYearRepository: FiscalYearRepository
) {
    private val log = LoggerFactory.getLogger(SnapshotService::class.java)

    private val filenameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        .withZone(ZoneId.of("CET")) // Use CET to match transaction dates

    // Portfolio state: maps of position key -> list of position snapshots (FIFO order)
    private val optionPositions = mutableMapOf<String, MutableList<OptionPositionSnapshot>>()
    private val stockPositions = mutableMapOf<String, MutableList<StockPositionSnapshot>>()

    // Fiscal year state: map of year -> fiscal year state
    private val fiscalYears = mutableMapOf<Int, FiscalYearState>()

    // Last transaction date
    private var lastTransactionDate: Instant? = null
    private var snapshot: StateSnapshot? = null

    // Transaction event listener
    @EventListener(NewTransactionEvent::class)
    fun onNewTransaction(event: NewTransactionEvent) {
        val transactionDate = event.tx.date

        validateChronologicalOrder(transactionDate)

        lastTransactionDate = transactionDate
        log.debug("Tracked transaction date: {}", lastTransactionDate)
    }

    private fun validateChronologicalOrder(transactionDate: Instant) {
        snapshot?.let {
            require(transactionDate > it.metadata.lastTransactionDate) {
                """
                    |
                    |ERROR: Chronological order violation detected
                    |
                    |  Snapshot last transaction: ${it.metadata.lastTransactionDate}
                    |  Current transaction:       $transactionDate
                    |
                    |  Cannot process transactions that occur before the snapshot date.
                    |
                    |  Please provide complete transaction history from the beginning,
                    |  or delete the snapshot files in the snapshots/ directory to start fresh.
                    |
                    """.trimMargin()
            }
        }
    }

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

    fun saveSnapshot(transactionsDir: String) {
        log.debug("saveSnapshot lastTransactionDate={}", lastTransactionDate)

        val snapshot = StateSnapshot(
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                lastTransactionDate = lastTransactionDate!!
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

        val snapshotDir = getSnapshotDirectory(transactionsDir)
        snapshotDir.mkdirs()

        val filename = generateFilename(snapshot.metadata.lastTransactionDate)
        val file = File(snapshotDir, filename)

        log.debug("Saving snapshot to {}", file.absolutePath)
        objectMapper.writeValue(file, snapshot)
        log.info("Snapshot saved successfully")
    }

    fun loadLatestSnapshot(transactionsDir: String): StateSnapshot? {
        val snapshotDir = getSnapshotDirectory(transactionsDir)

        if (!snapshotDir.exists() || !snapshotDir.isDirectory) {
            log.info("No snapshot directory found at {}", snapshotDir.absolutePath)
            return null
        }

        val latestFile = findLatestSnapshotFile(snapshotDir)
            ?: return null.also { log.info("No snapshot files found in {}", snapshotDir.absolutePath) }

        log.info("Loading snapshot from {}", latestFile.absolutePath)
        snapshot = objectMapper.readValue(latestFile, StateSnapshot::class.java)
        log.info("Snapshot loaded successfully. lastTransactionDate={}", snapshot?.metadata?.lastTransactionDate)
        return snapshot
    }

    fun loadAndRestoreState(transactionsDir: String) {
        val snapshot = loadLatestSnapshot(transactionsDir) ?: return

        restoreState(snapshot)
        log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
    }

    fun restoreState(snapshot: StateSnapshot) {
        log.info("Restoring state from snapshot. lastTransactionDate={}", snapshot.metadata.lastTransactionDate)

        this.snapshot = snapshot
        lastTransactionDate = snapshot.metadata.lastTransactionDate

        restorePortfolioState(snapshot.portfolio)

        restoreFiscalYears(snapshot.fiscalYears)

        log.info("State restoration complete")
    }

    private fun restorePortfolioState(portfolioSnapshot: PortfolioSnapshot) {
        portfolio.restoreFrom(portfolioSnapshot)

        portfolioSnapshot.optionPositions.forEach { (key, list) ->
            optionPositions[key] = list.toMutableList()
        }
        portfolioSnapshot.stockPositions.forEach { (symbol, list) ->
            stockPositions[symbol] = list.toMutableList()
        }

        log.debug("Restored portfolio state: optionKeys={}, stockSymbols={}",
            optionPositions.size, stockPositions.size)
    }

    private fun restoreFiscalYears(fiscalYearsSnapshot: Map<Int, FiscalYearSnapshot>) {

        fiscalYearsSnapshot.forEach { (yearValue, snapshot) ->
            val fiscalYear = fiscalYearRepository.getFiscalYear(Year.of(yearValue))
            fiscalYear.restoreState(
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
            fiscalYears[yearValue] = state
        }

        log.debug("Restored {} fiscal years", fiscalYearsSnapshot.size)
    }

    // State management
    fun reset() {
        log.debug("Resetting state snapshot manager")
        optionPositions.clear()
        stockPositions.clear()
        fiscalYears.clear()
        lastTransactionDate = null
        snapshot = null
    }

    private fun getOrCreateFiscalYearState(year: Int): FiscalYearState {
        return fiscalYears.computeIfAbsent(year) {
            FiscalYearState(
                profitAndLossFromOptions = ProfitAndLoss(eur(0), eur(0)),
                profitAndLossFromStocks = eur(0)
            )
        }
    }

    private fun getSnapshotDirectory(transactionsDir: String): File {
        return File(transactionsDir, "snapshots")
    }

    private fun generateFilename(lastTransactionDate: Instant): String {
        val timestamp = filenameDateFormatter.format(lastTransactionDate)
        return "snapshot-$timestamp.json"
    }

    private fun findLatestSnapshotFile(snapshotDir: File): File? {
        val snapshotFiles = snapshotDir.listFiles { file ->
            file.isFile && file.name.startsWith("snapshot-") && file.name.endsWith(".json")
        } ?: return null

        // Sort by filename (which includes timestamp) and return the latest
        return snapshotFiles.maxByOrNull { it.name }
    }

    private fun OptionTrade.key() = "${this.callOrPut}-${this.symbol}-${this.expirationDate}-${this.strikePrice}"

    private data class FiscalYearState(
        var profitAndLossFromOptions: ProfitAndLoss,
        var profitAndLossFromStocks: MonetaryAmount
    )
}
