package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.fiscalyear.EndOfYearEvent
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
        val file =
            File("/home/elch/ws/tastyworks-tax-calculator/src/main/resources/tastyworks_transactions_x3569_2021-11-01_2021-12-31.csv")
        csvReader.readCsv(file.inputStream())
        eventPublisher.publishEvent(EndOfYearEvent())
    }
}

fun main(args: Array<String>) {
    runApplication<TastyworksTaxCalculatorApplication>(*args)
}
