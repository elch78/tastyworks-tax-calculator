package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearManager
import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearRepository
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
import com.elchworks.tastyworkstaxcalculator.snapshot.*
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant

@Component
class ApplicationRunner(
    private val transactionsCsvReader: TransactionCsvReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearManager: FiscalYearManager,
    private val portfolio: Portfolio,
    private val fiscalYearRepository: FiscalYearRepository,
    private val snapshotFileService: SnapshotFileService,
    private val snapshotSerializer: SnapshotSerializer,
    private val snapshotDeserializer: SnapshotDeserializer,
    private val portfolioStateTracker: PortfolioStateTracker,
    private val fiscalYearStateTracker: FiscalYearStateTracker
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)

    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")[0]

        // Load snapshot if available
        val snapshot = snapshotFileService.loadLatestSnapshot(transactionsDir)
        if (snapshot != null) {
            snapshotDeserializer.restoreState(snapshot, portfolio, fiscalYearRepository)
            // Trackers will restore automatically via events
            log.info("Resumed from snapshot. Last transaction: {}", snapshot.metadata.lastTransactionDate)
        } else {
            log.info("No snapshot found. Processing all transactions from scratch.")
        }

        // Read and sort all transactions
        val transactions = File(transactionsDir)
            .walk()
            .filter { it.isFile && !it.absolutePath.contains("/snapshots/") }
            .map {
                log.debug("reading $it")
                val tx = transactionsCsvReader.read(it)
                log.info("read $it")
                tx
            }
            .flatten()
            .sortedBy { it.date }
            .toList()

        log.debug("Total transactions loaded: {}", transactions.size)

        // Validate chronological order
        validateChronologicalOrder(snapshot, transactions)

        // Process transactions
        var lastTransactionDate: Instant? = snapshot?.metadata?.lastTransactionDate
        log.debug("Starting transaction processing. Initial lastTransactionDate: {}", lastTransactionDate)

        transactions.forEach { tx ->
            eventPublisher.publishEvent(NewTransactionEvent(tx))
            lastTransactionDate = tx.date
        }

        log.debug("Transaction processing complete. Final lastTransactionDate: {}", lastTransactionDate)

        // Generate reports
        fiscalYearManager.printReports()

        // Save snapshot if we processed any transactions
        if (lastTransactionDate != null) {
            log.debug("Creating new snapshot with lastTransactionDate: {}", lastTransactionDate)
            val newSnapshot = snapshotSerializer.createSnapshot(
                portfolio = portfolio,
                fiscalYearRepository = fiscalYearRepository,
                lastTransactionDate = lastTransactionDate!!,
                portfolioStateTracker = portfolioStateTracker,
                fiscalYearStateTracker = fiscalYearStateTracker
            )
            snapshotFileService.saveSnapshot(newSnapshot, transactionsDir)
        } else {
            log.debug("No transactions processed, snapshot not updated")
        }
    }

    private fun validateChronologicalOrder(snapshot: StateSnapshot?, transactions: List<Transaction>) {
        log.debug("validateChronologicalOrder snapshot={}, transactions.size={}",
            snapshot?.metadata?.lastTransactionDate, transactions.size)

        // Skip validation if no snapshot or no transactions
        if (snapshot == null || transactions.isEmpty()) {
            return
        }

        val firstNewTx = transactions.first()
        val snapshotLastDate = snapshot.metadata.lastTransactionDate

        log.debug("validateChronologicalOrder firstNewTx.date='{}', snapshotLastDate='{}'",
            firstNewTx.date, snapshotLastDate)

        require(firstNewTx.date > snapshotLastDate) {
            """
            |
            |ERROR: Chronological order violation detected
            |
            |  Snapshot last transaction: $snapshotLastDate
            |  First new transaction:     ${firstNewTx.date}
            |
            |  Cannot process transactions that occur before or at the same time as the snapshot date.
            |
            |  Please provide complete transaction history from the beginning,
            |  or delete the snapshot files in the snapshots/ directory to start fresh.
            |
            """.trimMargin()
        }
    }
}
