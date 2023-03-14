package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.EndOfYearEvent
import com.elchworks.tastyworkstaxcalculator.positions.NewTransactionEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationEventPublisher
import java.io.File


@SpringBootApplication
class TastyworksTaxCalculatorApplication(
    private val csvReader: CsvReader,
    private val eventPublisher: ApplicationEventPublisher
): CommandLineRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)
    override fun run(vararg args: String?) {
        File("/home/elch/tmp/tastyworks/")
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
        eventPublisher.publishEvent(EndOfYearEvent(2021))
    }
}

fun main(args: Array<String>) {
    runApplication<TastyworksTaxCalculatorApplication>(*args)
}
