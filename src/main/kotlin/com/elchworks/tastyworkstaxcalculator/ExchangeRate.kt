package com.elchworks.tastyworkstaxcalculator

import com.elchworks.tastyworkstaxcalculator.positions.Profit
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField.MONTH_OF_YEAR
import java.time.temporal.ChronoField.YEAR
import javax.money.MonetaryAmount

@Component
class ExchangeRate {
    private val log = LoggerFactory.getLogger(ExchangeRate::class.java)

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

    fun usdToEur(profit: Profit): MonetaryAmount {
        log.debug("usdToEur profit='{}'", profit)
        val dateTime = ZonedDateTime.ofInstant(profit.date, ZoneId.of("CET"))
        val month = dateTime.get(MONTH_OF_YEAR)
        val year = dateTime.get(YEAR)
        val key = "$year-$month"
        log.debug("usdToEur year='{}', month='{}', key='{}'", year, month, key)
        val rate = 1 / (rates[key] ?: error("No rate for $key"))
        log.debug("usdToEur rate='{}'", rate)
        val eurValue = Money.of((profit.value * rate).number, "EUR")
        log.debug("usdToEur eurValue='{}'", eurValue)
        return eurValue
    }
}
