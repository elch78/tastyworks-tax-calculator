package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.positions.option.OptionPositionStatus
import com.elchworks.tastyworkstaxcalculator.toMonetaryAmountUsd
import com.elchworks.tastyworkstaxcalculator.transactions.Action.BUY_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
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
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime


fun randomOptionTrade() =
    OptionTrade(
        date = randomDateIn2021(),
        action = SELL_TO_OPEN,
        symbol = "",
        instrumentType = "",
        description = "",
        value = randomUsdAmount(),
        quantity = 1, // not random for now. In order to prevent error message "Currently only complete closing of positions is supported."
        averagePrice = Money.of(0, "USD"),
        commissions = usd(randomBigDecimal(1,6)),
        fees = usd(ZERO),
        multiplier = 100,
        rootSymbol = "rootSymbol",
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
        rootSymbol = "rootSymbol",
        expirationDate = randomLocalDate(),
        strikePrice = usd(randomBigDecimal()),
        callOrPut = "PUT",
        status = OptionPositionStatus.EXPIRED,
        quantity = 1,
        averagePrice = usd(ZERO),
    )

fun randomAssignment() =
    OptionAssignment(
        date = randomDateIn2021(),
        action = BUY_TO_OPEN,
        symbol = "rootSymbol",
        value = randomUsdAmount(),
        quantity = RandomUtils.nextInt(),
        averagePrice = randomUsdAmount(),
        fees = randomUsdAmount()
    )

private fun randomLocalDate(): LocalDate = LocalDate.now()

fun randomStockTrade() =
    StockTrade(
        symbol = "symbol",
        action = SELL_TO_CLOSE,
        quantity = RandomUtils.nextInt(),
        value = randomUsdAmount(),
        date = randomDateIn2021(),
        fees = randomUsdAmount(),
        commissions = randomUsdAmount(),
        averagePrice = randomUsdAmount(),
        description = "randomDescription"
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
