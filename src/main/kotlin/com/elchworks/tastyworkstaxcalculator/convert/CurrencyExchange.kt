package com.elchworks.tastyworkstaxcalculator.convert

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

    fun usdToEur(valueUsd: MonetaryAmount, date: Instant,): MonetaryAmount {
        log.debug("usdToEur value='{}', date='{}'", valueUsd, date)
        val dateCet = ZonedDateTime.ofInstant(date, ZoneId.of("CET")).toLocalDate()
        val rate = exchangeRateRepository.dailyRateUsdToEur(dateCet)
        val valueEur = Money.of((valueUsd * rate).number, "EUR")
        log.info("valueUsd='{}', date='{}', rate='{}', valueEur='{}'", valueUsd, dateCet, rate, valueEur)
        return valueEur
    }
}
