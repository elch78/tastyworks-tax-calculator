package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.FiscalYearManager
import com.elchworks.tastyworkstaxcalculator.positions.NewTransactionEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationEventPublisher
import java.io.File


@SpringBootApplication
class TastyworksTaxCalculatorApplication(
    private val csvReader: CsvReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val fiscalYearManager: FiscalYearManager,
): ApplicationRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)
    override fun run(args: ApplicationArguments) {
        val transactionsDir = args.getOptionValues("transactionsDir")[0]
        File(transactionsDir)
            .walk()
            .filter { it.isFile }
            .map {
                log.info("reading $it")
                csvReader.readCsv(it)
            }
            .flatten()
            .sortedBy { it.date }
            .forEach {
                eventPublisher.publishEvent(NewTransactionEvent(it))
            }
        fiscalYearManager.printReports()
    }
}

fun main(args: Array<String>) {
    runApplication<TastyworksTaxCalculatorApplication>(*args)
}
