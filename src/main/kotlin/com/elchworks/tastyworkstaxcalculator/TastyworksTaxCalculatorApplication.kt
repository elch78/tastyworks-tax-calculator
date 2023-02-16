package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File


@SpringBootApplication
class TastyworksTaxCalculatorApplication(
    private val positionsManager: PositionsManager,
    private val taxProfitAndLoss: TaxProfitAndLoss,
): CommandLineRunner {
    private val log = LoggerFactory.getLogger(TastyworksTaxCalculatorApplication::class.java)
    override fun run(vararg args: String?) {
        val file =
            File("/home/elch/ws/tastyworks-tax-calculator/src/main/resources/tastyworks_transactions_x3569_2021-11-01_2021-12-31.csv")
        val transactions = readCsv(file.inputStream())
        positionsManager.process(transactions)
        log.info("profit: {} loss: {}", taxProfitAndLoss.profit, taxProfitAndLoss.loss)
    }
}

fun main(args: Array<String>) {
    runApplication<TastyworksTaxCalculatorApplication>(*args)
}
