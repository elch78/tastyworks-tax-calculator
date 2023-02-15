package com.elchworks.tastyworkstaxcalculator

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField.MONTH_OF_YEAR
import java.time.temporal.ChronoField.YEAR

@Component
class ExchangeRate {
    private val log = LoggerFactory.getLogger(ExchangeRate::class.java)

    // source: https://de.statista.com/statistik/daten/studie/214878/umfrage/wechselkurs-des-euro-gegenueber-dem-us-dollar-monatliche-entwicklung/
    private val rates = mapOf(
        "2021-12" to 1.13F,
        "2021-11" to 1.14F,
    )

    fun usdToEur(value: Float, date: Instant): Float {
        log.debug("usdToEur value='{}', date='{}'", value, date)
        val dateTime = ZonedDateTime.ofInstant(date, ZoneId.of("CET"))
        val month = dateTime.get(MONTH_OF_YEAR)
        val year = dateTime.get(YEAR)
        val key = "$year-$month"
        log.debug("usdToEur year='{}', month='{}', key='{}'", year, month, key)
        val rate = 1 / rates[key]!!
        log.debug("usdToEur rate='{}'", rate)
        val eurValue = value * rate
        log.debug("usdToEur eurValue='{}'", eurValue)
        return eurValue
    }
}
