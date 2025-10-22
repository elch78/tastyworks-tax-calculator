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
    private val snapshotService: SnapshotService
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)

    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")[0]

        val snapshot = snapshotService.loadAndRestoreState(transactionsDir)

        val transactions = File(transactionsDir)
            .walk()
            .filter { it.isFile && !it.absolutePath.contains("/snapshots/") }
            .map {
                log.debug("reading {}", it)
                val tx = transactionsCsvReader.read(it)
                log.info("read {}", it)
                tx
            }
            .flatten()
            .sortedBy { it.date }
            .toList()

        log.debug("Total transactions loaded: {}", transactions.size)

        if (transactions.isEmpty()) {
            log.debug("No transactions to process")
            return
        }

        validateChronologicalOrder(snapshot, transactions)

        var lastTransactionDate: Instant? = snapshot?.metadata?.lastTransactionDate
        log.debug("Starting transaction processing. Initial lastTransactionDate: {}", lastTransactionDate)

        transactions.forEach { tx ->
            eventPublisher.publishEvent(NewTransactionEvent(tx))
            lastTransactionDate = tx.date
        }

        log.debug("Transaction processing complete. Final lastTransactionDate: {}", lastTransactionDate)

        fiscalYearManager.printReports()

        snapshotService.saveSnapshot(lastTransactionDate!!, transactionsDir)
    }

    private fun validateChronologicalOrder(snapshot: StateSnapshot?, transactions: List<Transaction>) {
        log.debug("validateChronologicalOrder snapshot={}, transactions.size={}",
            snapshot?.metadata?.lastTransactionDate, transactions.size)

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
