package com.elchworks.tastyworkstaxcalculator.convert

import com.elchworks.tastyworkstaxcalculator.positions.Profit
import com.elchworks.tastyworkstaxcalculator.times
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.money.MonetaryAmount

@Component
class currencyExchange(
    private val exchangeRateRepository: ExchangeRateRepository
) {
    private val log = LoggerFactory.getLogger(currencyExchange::class.java)

    fun usdToEur(profit: Profit): MonetaryAmount {
        log.debug("usdToEur profit='{}'", profit)
        val date = ZonedDateTime.ofInstant(profit.date, ZoneId.of("CET")).toLocalDate()
        val rate = exchangeRateRepository.monthlyRateUsdToEur(date)
        log.debug("usdToEur rate='{}'", rate)
        val eurValue = Money.of((profit.value * rate).number, "EUR")
        log.debug("usdToEur eurValue='{}'", eurValue)
        return eurValue
    }
}
