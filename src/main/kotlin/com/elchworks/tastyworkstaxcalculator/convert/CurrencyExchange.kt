package com.elchworks.tastyworkstaxcalculator.convert

import com.elchworks.tastyworkstaxcalculator.portfolio.Profit
import com.elchworks.tastyworkstaxcalculator.times
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.money.MonetaryAmount

@Component
class CurrencyExchange(
    private val exchangeRateRepository: ExchangeRateRepository
) {
    private val log = LoggerFactory.getLogger(CurrencyExchange::class.java)

    fun usdToEur(value: MonetaryAmount, date: Instant,): MonetaryAmount {
        log.debug("usdToEur value='{}', date='{}'", value, date)
        val dateCet = ZonedDateTime.ofInstant(date, ZoneId.of("CET")).toLocalDate()
        val rate = exchangeRateRepository.monthlyRateUsdToEur(dateCet)
        log.debug("usdToEur rate='{}'", rate)
        val eurValue = Money.of((value * rate).number, "EUR")
        log.debug("usdToEur eurValue='{}'", eurValue)
        return eurValue
    }
}
