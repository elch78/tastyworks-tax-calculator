package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearManager
import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.portfolio.Portfolio
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
    private val portfolio: Portfolio,
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)
    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")[0]
        File(transactionsDir)
            .walk()
            .filter { it.isFile }
            .map {
                log.debug("reading $it")
                val tx = transactionsCsvReader.read(it)
                log.info("read $it")
                tx
            }
            .flatten()
            .sortedBy { it.date }
            .forEach {
                eventPublisher.publishEvent(NewTransactionEvent(it))
            }
        fiscalYearManager.printReports()
    }
}
