package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.portfolio.NewTransactionEvent
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Transaction
import com.elchworks.tastyworkstaxcalculator.usd
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month.JANUARY
import java.time.Year

@Component
class Context(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun publishTx(tx: Transaction) {
        eventPublisher.publishEvent(NewTransactionEvent(tx))
    }

    fun defaultOptionStoTx() = randomOptionTrade().copy(
        date = randomDate(YEAR_2021, JANUARY),
        action = SELL_TO_OPEN,
        symbol = SYMBOL,
        value = usd(SELL_VALUE_USD),
        callOrPut = "PUT",
        strikePrice = usd(STRIKE_PRICE),
        expirationDate = EXPIRATION_DATE,
        commissions = usd(COMMISSIONS)
    )


    companion object {
        val TWO = BigDecimal("2.0")
        val YEAR_2021 = Year.of(2021)
        val YEAR_2022 = Year.of(2022)
        val SYMBOL = randomString("symbol")
        val EXCHANGE_RATE = TWO
        val SELL_VALUE_USD = randomBigDecimal()
        val SELL_VALUE_EUR = SELL_VALUE_USD * EXCHANGE_RATE
        val BUY_VALUE_USD = randomBigDecimal()
        val BUY_VALUE_EUR = BUY_VALUE_USD * EXCHANGE_RATE
        val STRIKE_PRICE = randomBigDecimal()
        val COMMISSIONS = randomBigDecimal()
        val FEE = randomBigDecimal()
        val EXPIRATION_DATE = LocalDate.now()
    }
}