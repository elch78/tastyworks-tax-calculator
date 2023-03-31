package com.elchworks.tastyworkstaxcalculator.convert

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoField

@Component
class ExchangeRateRepository {
    private val log = LoggerFactory.getLogger(ExchangeRateRepository::class.java)

    // source: https://de.statista.com/statistik/daten/studie/214878/umfrage/wechselkurs-des-euro-gegenueber-dem-us-dollar-monatliche-entwicklung/
    private val rates = mapOf(
        "2023-2" to BigDecimal("1.07"),
        "2023-1" to BigDecimal("1.08"),
        "2022-12" to BigDecimal("1.06"),
        "2022-11" to BigDecimal("1.02"),
        "2022-10" to BigDecimal("0.98"),
        "2022-9" to BigDecimal("0.99"),
        "2022-8" to BigDecimal("1.01"),
        "2022-7" to BigDecimal("1.02"),
        "2022-6" to BigDecimal("1.06"),
        "2022-5" to BigDecimal("1.06"),
        "2022-4" to BigDecimal("1.08"),
        "2022-3" to BigDecimal("1.1"),
        "2022-2" to BigDecimal("1.13"),
        "2022-1" to BigDecimal("1.13"),
        "2021-12" to BigDecimal("1.13"),
        "2021-11" to BigDecimal("1.14"),
    )

    fun monthlyRateUsdToEur(date: LocalDate): BigDecimal {
        val month = date.get(ChronoField.MONTH_OF_YEAR)
        val year = date.get(ChronoField.YEAR)
        val key = "$year-$month"
        log.debug("usdToEur year='{}', month='{}', key='{}'", year, month, key)
        val rate = BigDecimal.ONE / (rates[key] ?: error("No rate for $key"))
        log.debug("usdToEur rate='{}'", rate)
        return rate
    }
}
