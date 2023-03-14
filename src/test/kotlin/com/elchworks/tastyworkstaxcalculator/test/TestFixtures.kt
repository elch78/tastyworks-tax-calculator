package com.elchworks.tastyworkstaxcalculator.test

import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_CLOSE
import com.elchworks.tastyworkstaxcalculator.transactions.Action.SELL_TO_OPEN
import com.elchworks.tastyworkstaxcalculator.transactions.OptionTrade
import com.elchworks.tastyworkstaxcalculator.transactions.StockTrade
import org.apache.commons.lang3.RandomUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

fun randomOptionTrade() =
    OptionTrade(
        date = randomDateIn2021(),
        action = SELL_TO_OPEN,
        symbol = "",
        instrumentType = "",
        description = "",
        value = RandomUtils.nextFloat(),
        quantity = 1, // not random for now. In order to prevent error message "Currently only complete closing of positions is supported."
        averagePrice = 0f,
        commissions = 0f,
        fees = 0f,
        multiplier = 100,
        rootSymbol = "rootSymbol",
        underlyingSymbol = "",
        expirationDate = LocalDate.now(),
        strikePrice = 5.0f,
        callOrPut = "PUT",
        orderNr = 0
    )

fun randomStockTrade() =
    StockTrade(
        symbol = "symbol",
        action = SELL_TO_CLOSE,
        quantity = RandomUtils.nextInt(),
        value = RandomUtils.nextFloat(),
        date = randomDateIn2021(),
        fees = RandomUtils.nextFloat(),
        commissions = RandomUtils.nextFloat(),
        averagePrice = RandomUtils.nextFloat(),
        description = "randomDescription"
    )

private fun randomDateIn2021(): Instant =
    ZonedDateTime.of(2021, 11, 1, 1, 1, 1, 0, ZoneId.of("CET"))
        .plusMinutes(RandomUtils.nextLong(1,  86400))
        .toInstant()
