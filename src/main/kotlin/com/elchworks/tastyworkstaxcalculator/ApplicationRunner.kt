package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearManager
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.snapshot.SnapshotService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.File

@Component
class ApplicationRunner(
    private val transactionsCsvReader: TransactionCsvReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearManager: FiscalYearManager,
    private val snapshotService: SnapshotService
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)

    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")?.firstOrNull()
            ?: error("Missing required argument: --transactionsDir")

        snapshotService.loadAndRestoreState(transactionsDir)

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

        transactions.forEach { tx ->
            eventPublisher.publishEvent(NewTransactionEvent(tx))
        }

        fiscalYearManager.printReports()
        snapshotService.saveSnapshot(transactionsDir)
    }
}
