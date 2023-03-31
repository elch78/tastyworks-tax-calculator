package com.elchworks.tastyworkstaxcalculator.convert

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoField

@Component
class ExchangeRateRepository {
    private val log = LoggerFactory.getLogger(ExchangeRateRepository::class.java)

    // source: https://de.statista.com/statistik/daten/studie/214878/umfrage/wechselkurs-des-euro-gegenueber-dem-us-dollar-monatliche-entwicklung/
    private val rates = mapOf(
        "2023-2" to 1.07F,
        "2023-1" to 1.08F,
        "2022-12" to 1.06F,
        "2022-11" to 1.02F,
        "2022-10" to 0.98F,
        "2022-9" to 0.99F,
        "2022-8" to 1.01F,
        "2022-7" to 1.02F,
        "2022-6" to 1.06F,
        "2022-5" to 1.06F,
        "2022-4" to 1.08F,
        "2022-3" to 1.1F,
        "2022-2" to 1.13F,
        "2022-1" to 1.13F,
        "2021-12" to 1.13F,
        "2021-11" to 1.14F,
    )

    fun monthlyRateUsdToEur(date: LocalDate): Float {
        val month = date.get(ChronoField.MONTH_OF_YEAR)
        val year = date.get(ChronoField.YEAR)
        val key = "$year-$month"
        log.debug("usdToEur year='{}', month='{}', key='{}'", year, month, key)
        val rate = 1 / (rates[key] ?: error("No rate for $key"))
        log.debug("usdToEur rate='{}'", rate)
        return rate
    }
}
