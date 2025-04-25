package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus
import com.elchworks.tastyworkstaxcalculator.portfolio.option.OptionPositionStatus.ASSIGNED
import com.elchworks.tastyworkstaxcalculator.toMonetaryAmountUsd
import com.elchworks.tastyworkstaxcalculator.transactions.Action.*
import com.elchworks.tastyworkstaxcalculator.transactions.OptionAssignment
import com.elchworks.tastyworkstaxcalculator.transactions.OptionRemoval
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import com.elchworks.tastyworkstaxcalculator.usd
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.RandomUtils
import org.javamoney.moneta.Money
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.math.BigInteger
import java.time.*
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY

val TWO = BigDecimal("2.0")
val YEAR_2021 = Year.of(2021)
val YEAR_2022 = Year.of(2022)
val SYMBOL = randomString("symbol")
val EXCHANGE_RATE = TWO
val SELL_VALUE_USD = randomBigDecimal()
val SELL_VALUE_EUR = SELL_VALUE_USD * EXCHANGE_RATE
val BUY_VALUE_USD = randomBigDecimal()
val BUY_VALUE_EUR = BUY_VALUE_USD * EXCHANGE_RATE
val ZERO_USD = usd(0.0)
val STRIKE_PRICE = randomBigDecimal()
val COMMISSIONS = randomBigDecimal()
val FEE = randomBigDecimal()
val EXPIRATION_DATE = LocalDate.now()

fun randomOptionTrade() =
    OptionTrade(
        date = randomDateIn2021(),
        action = SELL_TO_OPEN,
        symbol = "",
        instrumentType = "",
        description = "",
        value = randomUsdAmount(),
        quantity = 1,
        averagePrice = Money.of(0, "USD"),
        commissions = usd(randomBigDecimal(1,6)),
        fees = usd(ZERO),
        multiplier = 100,
        underlyingSymbol = "",
        expirationDate = randomLocalDate(),
        strikePrice = usd(randomBigDecimal()),
        callOrPut = "PUT",
        orderNr = 0
    )

fun randomUsdAmount() = RandomUtils.nextFloat().toMonetaryAmountUsd()
fun randomUsdAmount(startInclusive: Float, endExclusive: Float) = RandomUtils.nextFloat(startInclusive, endExclusive).toMonetaryAmountUsd()

fun randomOptionRemoval() =
    OptionRemoval(
        date = randomDateIn2021(),
        symbol = "symbol",
        expirationDate = randomLocalDate(),
        strikePrice = usd(randomBigDecimal()),
        callOrPut = "PUT",
        status = OptionPositionStatus.EXPIRED,
        quantity = 1,
        averagePrice = usd(ZERO),
        description = "description",
    )

fun randomAssignment() =
    OptionAssignment(
        date = randomDateIn2021(),
        action = BUY_TO_OPEN,
        type = "Buy to Open",
        symbol = "symbol",
        value = randomUsdAmount(),
        quantity = RandomUtils.nextInt(),
        averagePrice = randomUsdAmount(),
        fees = randomUsdAmount(),
        description = "description",
    )

private fun randomLocalDate(): LocalDate = LocalDate.now()

fun randomStockTrade() =
    StockTrade(
        symbol = "symbol",
        action = SELL_TO_CLOSE,
        type = "Buy to Open",
        quantity = RandomUtils.nextInt(),
        value = randomUsdAmount(),
        date = randomDateIn2021(),
        fees = randomUsdAmount(),
        commissions = randomUsdAmount(),
        averagePrice = randomUsdAmount(),
        description = "randomDescription"
    )

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

fun defaultReverseSplitTransaction() =
    defaultStockTrade().copy(
        type = "Reverse Split"
    )

fun defaultAssignment() = randomOptionRemoval().copy(
    date = randomDate(YEAR_2021, FEBRUARY),
    symbol = SYMBOL,
    status = ASSIGNED,
    callOrPut = "PUT",
    strikePrice = usd(STRIKE_PRICE),
    expirationDate = EXPIRATION_DATE
)

fun defaultStockTrade() = randomStockTrade().copy(
    symbol = SYMBOL,
    action = BUY_TO_OPEN,
    quantity = 100,
    averagePrice = usd(STRIKE_PRICE)
)

private fun randomDateIn2021(): Instant =
    ZonedDateTime.of(2021, 11, 1, 1, 1, 1, 0, ZoneId.of("CET"))
        .plusMinutes(RandomUtils.nextLong(1,  1000))
        .toInstant()
fun randomDate(year: Year, month: Month): Instant =
    ZonedDateTime.of(year.value, month.value, 1, 1, 1, 1, 0, ZoneId.of("CET"))
        .plusMinutes(RandomUtils.nextLong(1,  1000))
        .toInstant()

fun randomString(id: String) = "$id-${RandomStringUtils.randomAlphabetic(3)}"

fun randomBigDecimal() = randomBigDecimal(1, 101)
fun randomBigDecimal(startInclusive: Long, endExclusive: Long) = BigDecimal(BigInteger.valueOf(RandomUtils.nextLong(startInclusive, endExclusive)), 2)
